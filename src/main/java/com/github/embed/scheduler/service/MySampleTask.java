package com.github.embed.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("mySampleTask") // Bean name as referenced in schema.sql
public class MySampleTask {

    private static final Logger logger = LoggerFactory.getLogger(MySampleTask.class);

    public void executeSuccess(String message, int value) {
        logger.info("MySampleTask.executeSuccess called with message: '{}' and value: {}", message, value);
        // Simulate some work
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Task interrupted during sleep");
        }
        logger.info("MySampleTask.executeSuccess completed.");
    }

    public void executeFailed(String error) {
        logger.info("MySampleTask.executeFailed called with error: '{}'", error);
        // Simulate a failure
        throw new RuntimeException("Simulated failure: " + error);
    }

    public void executeWithMap(Map<String, Object> params) {
        logger.info("MySampleTask.executeWithMap called with parameters: {}", params);
        params.forEach((key, value) -> logger.info("Param: {} = {}", key, value));
        logger.info("MySampleTask.executeWithMap completed.");
    }

    public void simpleExecute() {
        logger.info("MySampleTask.simpleExecute called. No parameters.");
        logger.info("MySampleTask.simpleExecute completed.");
    }
}
