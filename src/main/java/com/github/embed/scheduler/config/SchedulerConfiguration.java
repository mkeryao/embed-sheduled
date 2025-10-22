package com.github.embed.scheduler.config;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionManager;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
public class SchedulerConfiguration {


    @Resource
    private Map<String , DataSource> dataSourceMap ;

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    //@ConditionalOnMissingBean(value = JdbcTemplate.class , name = "schedulerJdbcTemplate")
    @Bean("schedulerJdbcTemplate")
    public JdbcTemplate schedulerJdbcTemplate(
            @Value("${scheduler.ds.name:schedulerDataSource}") String schedulerName
            ){
        DataSource dataSource = dataSourceMap.get(schedulerName) ;
        if(Objects.nonNull(dataSource)){
            new JdbcTemplate(dataSource) ;
        }
        if( 1 == dataSourceMap.size()){
            return new JdbcTemplate(
                    dataSourceMap.entrySet()
                    .iterator()
                    .next()
                    .getValue() );
        }
        throw new BeanCreationException("Can not create Bean schedulerJdbcTemplate") ;
    }


    @Bean("schedulerTransactionManager")
    public TransactionManager schedulerTransactionManager(
            @Value("${scheduler.tsm.name:schedulerTransactionManager") String schedulerName){
        DataSource dataSource = dataSourceMap.get(schedulerName) ;
        if(Objects.nonNull(dataSource)){
            return new DataSourceTransactionManager(dataSource) ;
        }
        if( 1 == dataSourceMap.size()){
            return new DataSourceTransactionManager(
                    dataSourceMap.entrySet()
                            .iterator()
                            .next()
                            .getValue() );
        }
        throw new BeanCreationException("Can not create Bean schedulerTransactionManager") ;
    }


    @Bean
    public ThreadPoolTaskExecutor beanThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(15);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bean-scheduled-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

}
