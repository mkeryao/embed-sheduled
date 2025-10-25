package com.github.embed.scheduler.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.TypeUtils;
import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Service
public class BeanTaskExecutor implements TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(BeanTaskExecutor.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Resource(name="asyncThreadPoolTaskExecutor")
    private ThreadPoolTaskExecutor asyncThreadPoolTaskExecutor ;

    public static class TaskTimeoutException extends RuntimeException {
        public TaskTimeoutException(String message) {
            super(message);
        }
    }

    @Override
    public String execute(TaskConfig taskConfig, Long logId) throws Exception {
        final String beanName = taskConfig.getBeanName();
        final String methodName = taskConfig.getMethodName();
        final Integer timeoutSeconds = taskConfig.getExecuteTimeoutSeconds();

        Optional<TaskExecuteLog> logOpt = taskExecuteLogDao.findById(logId);
        if (!logOpt.isPresent()) {
            throw new IllegalStateException("Execution log with ID " + logId + " not found.");
        }
        TaskExecuteLog log = logOpt.get();

        String finalParametersJson = resolveParameters(log, taskConfig);

        try {
            if (!StringUtils.hasText(beanName) || !StringUtils.hasText(methodName)) {
                throw new IllegalArgumentException("Bean name or method name is empty for task: " + taskConfig.getTaskName());
            }

            final Object beanInstance;
            try {
                beanInstance = applicationContext.getBean(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                logger.error("Bean with name '{}' not found for task '{}'", beanName, taskConfig.getTaskName());
                throw new Exception("Bean not found: " + beanName, e);
            }

            final Map<String, Object> parametersMap;
            if (StringUtils.hasText(finalParametersJson)) {
                try {
                    parametersMap = JSON.parseObject(finalParametersJson, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    logger.error("Failed to parse parameters JSON for task '{}': {}. Error: {}", taskConfig.getTaskName(), finalParametersJson, e.getMessage(), e);
                    throw new IllegalArgumentException("Invalid JSON format in parameters field.");
                }
            } else {
                parametersMap = Collections.emptyMap();
            }

            final Method methodToExecute = findMethod(beanInstance.getClass(), methodName, parametersMap);
            if (methodToExecute == null) {
                String errorMsg = String.format("Method '%s' with compatible parameters not found in bean '%s' for task '%s'. Check parameter names and types in JSON against method signature.",
                        methodName, beanName, taskConfig.getTaskName());
                logger.error(errorMsg);
                throw new NoSuchMethodException(errorMsg);
            }

            final Object[] finalArgs = convertParameters(methodToExecute, parametersMap);

            Future<Object> future = asyncThreadPoolTaskExecutor.submit(() -> {
                logger.info("Executing bean task '{}': bean='{}', method='{}', params={}", taskConfig.getTaskName(), beanName, methodName,
                        !parametersMap.isEmpty() ? finalParametersJson : "none");
                return methodToExecute.invoke(beanInstance, finalArgs);
            });

            Object result;
            try {
                result = (timeoutSeconds != null && timeoutSeconds > 0)
                        ? future.get(timeoutSeconds, TimeUnit.SECONDS)
                        : future.get();
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new TaskTimeoutException("Task " + taskConfig.getTaskName() + " timed out after " + timeoutSeconds + " seconds.");
            } catch (Exception ex) {
                Throwable throwable = NestedExceptionUtils.getRootCause(ex) ;
                throw new Exception(  ExceptionUtils.getRootCauseMessage(ex) , throwable) ;
            }
            
            // If we reach here, the future.get() was successful.
            log.setState(ExecutionState.SUCCESS);
            String resultStr = result != null ? result.toString() : null;
            log.setRtnMsg(resultStr);
            return resultStr;
        } catch (InvocationTargetException e) {
            log.setState(ExecutionState.FAILED);
            log.setExMsg( ExceptionUtils.getRootCauseMessage(e));
            throw e.getCause() instanceof Exception ? (Exception)e.getCause() : e ;
         } catch (Exception e) {
            String errorMsg = "Bean execution failed: " + e.getMessage();
            logger.error("Bean Task ID {} execution failed. {}", taskConfig.getTaskId(), errorMsg, e);
            log.setState(ExecutionState.FAILED);
            log.setExMsg(errorMsg);
        } finally {
            taskExecuteLogDao.update(log);
        }
        return null;
    }

    private String resolveParameters(TaskExecuteLog log, TaskConfig taskConfig) {
        if (log.getTaskPattern() == ExecutionPattern.WORKFLOW_STEP && StringUtils.hasText(log.getParameters())) {
            logger.debug("Using parameters from workflow node log for log ID {}.", log.getLogId());
            return log.getParameters();
        }
        logger.debug("Using parameters from task config for log ID {}.", log.getLogId());
        return taskConfig.getParameters();
    }

    private Method findMethod(Class<?> beanClass, String methodName, final Map<String, Object> parametersMap) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        for (Method candidate : candidates) {
            Parameter[] methodParams = candidate.getParameters();
            if (methodParams.length == 0 && (parametersMap == null || parametersMap.isEmpty())) {
                return candidate;
            }
            if (methodParams.length == 1 && Map.class.isAssignableFrom(methodParams[0].getType())) {
                if (parametersMap != null) return candidate;
            }
            if (parametersMap != null && methodParams.length > 0) {
                boolean allNamesFound = true;
                for (Parameter p : methodParams) {
                    if (!p.isNamePresent() || !parametersMap.containsKey(p.getName())) {
                        allNamesFound = false;
                        break;
                    }
                }
                if (allNamesFound) return candidate;
            }
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (parametersMap != null && !parametersMap.isEmpty()) {
            List<Method> countMatchingCandidates = new ArrayList<>();
            for (Method c : candidates) {
                if (c.getParameterCount() == parametersMap.size()) {
                    countMatchingCandidates.add(c);
                }
            }
            if (countMatchingCandidates.size() == 1) {
                return countMatchingCandidates.get(0);
            }
        }

        logger.warn("Could not find a definitive method match for '{}' with provided parameters.", methodName);
        return null;
    }

    private Object[] convertParameters(Method method, Map<String, Object> parametersMap) throws Exception {
        Parameter[] methodParameters = method.getParameters();
        if (methodParameters.length == 0) {
            return new Object[0];
        }

        if (parametersMap == null) parametersMap = Collections.emptyMap();

        if (methodParameters.length == 1 && Map.class.isAssignableFrom(methodParameters[0].getType())) {
            Object castedMap = TypeUtils.cast(parametersMap, methodParameters[0].getParameterizedType(), ParserConfig.getGlobalInstance());
            return new Object[]{castedMap};
        }

        Object[] convertedArgs = new Object[methodParameters.length];
        for (int i = 0; i < methodParameters.length; i++) {
            Parameter param = methodParameters[i];
            if (!param.isNamePresent()) {
                throw new IllegalStateException("Parameter names not available for method " + method.getName() + ". Compile with -parameters flag.");
            }
            String paramName = param.getName();
            Object valueFromMap = parametersMap.get(paramName);

            try {
                convertedArgs[i] = TypeUtils.cast(valueFromMap, param.getParameterizedType(), ParserConfig.getGlobalInstance());
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting parameter '" + paramName + "' to type " + param.getType().getSimpleName() + ". Value: " + valueFromMap, e);
            }
        }
        return convertedArgs;
    }
}
