package com.github.embed.scheduler.sample;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class JdbcProcTask {

    @Resource
    private ApplicationContext applicationContext;

    public static String MYSQL_DB = "mysql" ;
    public static String ORACLE_DB = "oracle" ;
    public static String POSTGRESQL_DB = "postgresql" ;
    public static String SQLSERVER_DB = "sqlserver" ;
    public static String UNKOWN_DB = "unknown" ;



    /**
     * Execute a stored procedure that does not return a value (only IN parameters).
     * Parameters are read from the context as param1, param2, ...
     * Puts the affected rows count (or null if unavailable) into context.result
     */
    public Object executeProc(Map<String, Object> context) {
        log.debug("[JdbcOperatorTask executeProc1 context: {}]", context);
        String jdbcTemplateName = MapUtils.getString(context, "jdbcTemplateName");
        Assert.hasText(jdbcTemplateName, "jdbcTemplateName must not be empty");
        JdbcTemplate jdbcTemplate = applicationContext.getBean(jdbcTemplateName, JdbcTemplate.class);
        Assert.notNull(jdbcTemplate, "jdbcTemplate must not be null");

        String procName = MapUtils.getString(context, "procName");
        Assert.hasText(procName, "procName must not be empty");

        String sql = buildProcSql(procName, context, false);
        List<Object> params = collectParams(context);

        log.info("Executing stored procedure (no return): {} with params={}", sql, params);
        try {
            Integer updateCount = jdbcTemplate.execute(connection -> {
                CallableStatement cs = connection.prepareCall(sql);
                // set IN params starting at index 1
                for (int i = 0; i < params.size(); i++) {
                    Object p = params.get(i);
                    int idx = i + 1;
                    if (p == null) {
                        cs.setNull(idx, sqlTypeForObject(null));
                    } else {
                        cs.setObject(idx, p);
                    }
                }
                return cs;
            }, (CallableStatement cs) -> {
                cs.execute();
                try {
                    int uc = cs.getUpdateCount();
                    // getUpdateCount may return -1 if not applicable
                    return uc >= 0 ? uc : null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            log.info("[Stored procedure executed, updateCount: {}]", updateCount);
            context.put("result", updateCount);
            return context;
        } catch (Exception ex) {
            log.error("Failed to execute stored procedure {}", procName, ex);
            throw ex;
        }
    }

    /**
     * Execute a stored procedure/function that has a return value.
     * Supports MySQL (uses SELECT func(...)) and other DBs via CallableStatement with "? = call ...".
     * For SQL Server, uses the form "{call proc(?,...)}" where the first placeholder is the OUT param.
     * Input parameters are read from context.param1..paramN and may be null.
     * The retrieved return value (Object) is put into context.result.
     */
    public Object callProc(Map<String, Object> context) {
        log.debug("[JdbcOperatorTask executeProc2 context: {}]", context);
        String jdbcTemplateName = MapUtils.getString(context, "jdbcTemplateName");
        Assert.hasText(jdbcTemplateName, "jdbcTemplateName must not be empty");
        JdbcTemplate jdbcTemplate = applicationContext.getBean(jdbcTemplateName, JdbcTemplate.class);
        Assert.notNull(jdbcTemplate, "jdbcTemplate must not be null");

        String procName = MapUtils.getString(context, "procName");
        Assert.hasText(procName, "procName must not be empty");

        int returnSqlType = MapUtils.getIntValue(context, "returnSqlType", Types.INTEGER);

        String dbProduct = detectDbProduct(jdbcTemplate);
        List<Object> params = collectParams(context);

        log.info("Executing stored procedure/function (with return) on DB" +
                " {}: proc={} params={} returnSqlType={}", dbProduct, procName, params, returnSqlType);
        try {
            if (MYSQL_DB.equals(dbProduct)) {
                // For MySQL functions, prefer SELECT func(?,...)
                // Stored procedure: user can specify out param position via outParamIndex (1-based), default 1
                int outIndex = MapUtils.getIntValue(context, "outParamIndex", 1);
                String callSql = buildCallSqlWithOutAt(procName, params.size(), outIndex);
                log.debug("[MySQL CALL invocation: {}] outIndex={}", callSql, outIndex);
                Object returnValue = jdbcTemplate.execute(connection -> {
                    CallableStatement cs = connection.prepareCall(callSql);
                    cs.registerOutParameter(outIndex, returnSqlType);
                    // map input params into callable statement skipping the outIndex
                    for (int i = 0; i < params.size(); i ++ ) {
                        Object p = params.get(i);
                        int pos = i + 1;
                        if (pos >= outIndex) pos ++ ; // shift right when reaching/after out param
                        if (p == null) {
                            cs.setNull(pos, sqlTypeForObject(null));
                        } else {
                            cs.setObject(pos, p);
                        }
                    }
                    return cs;
                }, (CallableStatement cs) -> {
                    cs.execute();
                    try {
                        return cs.getObject(outIndex);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                context.put("result", returnValue);
                return context;
            } else if (SQLSERVER_DB.equals(dbProduct)) {
                // For SQL Server, call stored procedure with OUT as first param: {call proc(?,...)}
                String callSql = buildCallSqlWithOutFirst(procName, params.size());
                Object returnValue = jdbcTemplate.execute(connection -> {
                    CallableStatement cs = connection.prepareCall(callSql);
                    // register first parameter as OUT
                    cs.registerOutParameter(1, returnSqlType);
                    for (int i = 0; i < params.size(); i++) {
                        Object p = params.get(i);
                        int idx = i + 2; // IN params start at 2
                        if (p == null) {
                            cs.setNull(idx, sqlTypeForObject(null));
                        } else {
                            cs.setObject(idx, p);
                        }
                    }
                    return cs;
                }, (CallableStatement cs) -> {
                    cs.execute();
                    try {
                        return cs.getObject(1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                context.put("result", returnValue);
                return context;
            } else {
                // Other DBs (Oracle, PostgreSQL): use CallableStatement with ? = call ...
                String callSql = buildProcSql(procName, context, true);
                Object returnValue = jdbcTemplate.execute(connection -> {
                    CallableStatement cs = connection.prepareCall(callSql);
                    cs.registerOutParameter(1, returnSqlType);
                    for (int i = 0; i < params.size(); i++) {
                        Object p = params.get(i);
                        int idx = i + 2;
                        if (p == null) {
                            cs.setNull(idx, sqlTypeForObject(null));
                        } else {
                            cs.setObject(idx, p);
                        }
                    }
                    return cs;
                }, (CallableStatement cs) -> {
                    cs.execute();
                    try {
                        return cs.getObject(1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });

                context.put("result", returnValue);
                return context;
            }
        } catch (Exception ex) {
            log.error("Failed to execute stored procedure/function {} on {}", procName, dbProduct, ex);
            throw ex;
        }
    }

    // Helper: collect param1..paramN from context into a list (preserves order)
    private List<Object> collectParams(Map<String, Object> context) {
        List<Object> params = new ArrayList<>();
        int index = 1;
        while (true) {
            String key = "param" + index;
            if (context.containsKey(key)) {
                String value = MapUtils.getString(context ,key) ;
                if(StringUtils.equalsIgnoreCase(value , "null")){
                    params.add(null);
                } else {
                    params.add(context.get(key));
                }
                index ++ ;
            } else {
                break;
            }
        }
        return params;
    }

    // Build procedure SQL. If withReturn is true, produce the standard "? = call proc(...)" form.
    private String buildProcSql(String procName, Map<String, Object> context, boolean withReturn) {
        StringBuilder sqlBuilder = new StringBuilder();
        if (withReturn) {
            sqlBuilder.append("{? = call ").append(procName).append("(");
            int paramIndex = 1;
            boolean first = true;
            while (true) {
                String paramKey = "param" + paramIndex;
                if (context.containsKey(paramKey)) {
                    if (!first) sqlBuilder.append(", ");
                    sqlBuilder.append("?");
                    first = false;
                    paramIndex++;
                } else {
                    break;
                }
            }
            sqlBuilder.append(")}");
        } else {
            sqlBuilder.append("{call ").append(procName).append("(");
            int paramIndex = 1;
            boolean first = true;
            while (true) {
                String paramKey = "param" + paramIndex;
                if (context.containsKey(paramKey)) {
                    if (!first) sqlBuilder.append(", ");
                    sqlBuilder.append("?");
                    first = false;
                    paramIndex++;
                } else {
                    break;
                }
            }
            sqlBuilder.append(")}");
        }
        return sqlBuilder.toString();
    }

    // Build call sql where the first placeholder is reserved for OUT parameter: {call proc(?, ?, ...)} where total placeholders = paramCount + 1
    private String buildCallSqlWithOutFirst(String procName, int inputParamCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{call ").append(procName).append("(");
        for (int i = 0; i < inputParamCount + 1; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")}");
        return sb.toString();
    }

    // For MySQL function call via SELECT func(?,?)
    private String buildSelectFunctionSql(String funcName, int paramCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(funcName).append("(");
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildCallSqlWithOutAt(String procName, int inputParamCount, int outParamIndex) {
        int total = inputParamCount + 1;
        if (outParamIndex < 1) outParamIndex = 1;
        if (outParamIndex > total) outParamIndex = total;
        StringBuilder sb = new StringBuilder();
        sb.append("{call ").append(procName).append("(");
        for (int i = 1; i <= total; i++) {
            if (i > 1) sb.append(", ");
            sb.append("?");
        }
        sb.append(")}");
        return sb.toString();
    }

    // Detect DB product name from JdbcTemplate's DataSource
    private String detectDbProduct(JdbcTemplate jdbcTemplate) {
        DataSource ds = jdbcTemplate.getDataSource();
        if (Objects.isNull(ds)) return UNKOWN_DB ;
        try (Connection conn = ds.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) return UNKOWN_DB ;
            String p = product.toLowerCase();
            if (p.contains("mysql")) return MYSQL_DB;
            if (p.contains("oracle")) return ORACLE_DB;
            if (p.contains("postgres") || p.contains("postgresql")) return POSTGRESQL_DB;
            if (p.contains("microsoft") || p.contains("jtds") || p.contains("sql server") || p.contains("mssql")) return SQLSERVER_DB ;
            return p;
        } catch (SQLException e) {
            log.warn("Failed to detect DB product", e);
            return UNKOWN_DB;
        }
    }

    // Best-effort mapping of Java object to SQL type for setting NULLs. If unknown, default to VARCHAR.
    private int sqlTypeForObject(Object o) {
        if (o == null) return Types.VARCHAR; // default to VARCHAR for nulls (works in most drivers)
        if (o instanceof Integer) return Types.INTEGER;
        if (o instanceof Long) return Types.BIGINT;
        if (o instanceof Short) return Types.SMALLINT;
        if (o instanceof Byte) return Types.TINYINT;
        if (o instanceof Float || o instanceof Double) return Types.DOUBLE;
        if (o instanceof BigDecimal) return Types.DECIMAL;
        if (o instanceof Boolean) return Types.BOOLEAN;
        if (o instanceof java.sql.Date) return Types.DATE;
        if (o instanceof Timestamp) return Types.TIMESTAMP;
        if (o instanceof java.util.Date) return Types.TIMESTAMP;
        if (o instanceof String) return Types.VARCHAR;
        return Types.VARCHAR;
    }
}
