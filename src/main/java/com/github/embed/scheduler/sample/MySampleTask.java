package com.github.embed.scheduler.sample;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("mySampleTask") // Bean name as referenced in schema.sql
public class MySampleTask {

    private static final Logger logger = LoggerFactory.getLogger(MySampleTask.class);

    public Map<String,Object> executeSuccess(Map<String,Object> params) {
        logger.info("MySampleTask.executeSuccess  with Params : {} ", params );
        // Simulate some work
        try {
            long sleep = (long) params.getOrDefault("sleep" , 1000L) ;
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Task interrupted during sleep");
        }
        logger.info("MySampleTask.executeSuccess completed.");
        params.put("status", "success");
        return  params ;
    }

    public void executeFailed(String error) {

        logger.info("MySampleTask.executeFailed called with error: '{}'", error);
        // Simulate a failure
        throw new RuntimeException("Simulated failure: " + error);
    }

    public Map<String, Object> executeException(Map<String, Object> params) {

        logger.info("MySampleTask.executeFailed called with error: '{}'", params);
        // Simulate a failure
        int id = (int) params.getOrDefault("id", 10);
        if (id % 5 == 0) {
            throw new RuntimeException("Simulated failure: " + id);
        }
        return params;

    }

    public Map<String, Object> executeWithMap(Map<String, Object> params) {
        logger.info("MySampleTask.executeWithMap called with parameters: {}", params);
        params.forEach((key, value) -> logger.info("Param: {} = {}", key, value));
        logger.info("MySampleTask.executeWithMap completed.");
        return params;
    }

    public Map<String,Object> simpleExecute(Map<String,Object> params) {
        logger.info("MySampleTask.simpleExecute called. No parameters.");
        logger.info("MySampleTask.simpleExecute completed.");
        params.put("status", "success");
        params.put("name" , "simpleExecute");
        return  params ;
    }

    public Map<String, Object> executeTimeout(Map<String, Object> params) throws InterruptedException {
        logger.info("MySampleTask.executeTimeout called. with parameters {}", params);

        Object timeout = params.get("timeout");
        if (Objects.nonNull(timeout)) {
            TimeUnit.SECONDS.sleep((long) timeout);
        }
        logger.info("MySampleTask.simpleExecute completed.");

        return params;
    }

}
