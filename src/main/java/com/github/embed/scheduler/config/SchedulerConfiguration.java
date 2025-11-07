package com.github.embed.scheduler.config;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionManager;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SchedulerConfiguration implements AsyncConfigurer {


    @Resource
    private Map<String , DataSource> dataSourceMap ;

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @ConditionalOnMissingBean(value = JdbcTemplate.class , name = "schedulerJdbcTemplate")
    @Bean("schedulerJdbcTemplate")
    public JdbcTemplate schedulerJdbcTemplate(
            @Value("${scheduler.ds.name:schedulerDataSource}") String schedulerName
            ){
        DataSource dataSource = dataSourceMap.get(schedulerName) ;
        if(Objects.nonNull(dataSource)){
            return new JdbcTemplate(dataSource) ;
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

    //@ConditionalOnMissingBean(value = TransactionManager.class , name = "schedulerTransactionManager")
    @Bean("schedulerTransactionManager")
    public TransactionManager schedulerTransactionManager(
            @Value("${scheduler.ds.name:schedulerDataSource}") String schedulerName){
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


    @Value("${scheduler.async.core-pool-size:15}")
    private int asyncCorePoolSize ;

    @Value("${scheduler.async.max-pool-size:20}")
    private int asyncMaxPoolSize ;

    @Value("${scheduler.async.queue-capacity:20}")
    private int asyncQueueCapacity ;


    @Bean(name = "asyncThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor () {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncCorePoolSize);
        executor.setMaxPoolSize(asyncMaxPoolSize);
        executor.setQueueCapacity(asyncQueueCapacity);
        executor.setThreadNamePrefix("async-scheduled-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
