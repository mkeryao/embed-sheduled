package com.github.embed.scheduler.service;

import com.alibaba.fastjson.JSON;
import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.dto.taskparams.HttpTaskParameters;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class HttpTaskExecutor implements TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HttpTaskExecutor.class);

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Override
    public String execute(TaskConfig taskConfig, Long logId) {
        Optional<TaskExecuteLog> logEntryOpt = taskExecuteLogDao.findById(logId);
        if (!logEntryOpt.isPresent()) {
            logger.error("HttpTaskExecutor: TaskExecuteLog not found for logId: {}", logId);
            return "Execution failed: Log entry not found.";
        }
        TaskExecuteLog logEntry = logEntryOpt.get();

        String responseSummary = null;
        try {
            HttpTaskParameters params = resolveParameters(taskConfig, logEntry);

            if (!StringUtils.hasText(params.getUrl()) || !StringUtils.hasText(params.getMethod())) {
                throw new IllegalArgumentException("URL and Method are mandatory in HTTP task parameters.");
            }

            RestTemplate customRestTemplate = buildRestTemplate(params);

            HttpHeaders headers = new HttpHeaders();
            if (params.getHeaders() != null) {
                params.getHeaders().forEach(headers::set);
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(params.getBody(), headers);
            HttpMethod httpMethod = HttpMethod.resolve(params.getMethod().toUpperCase());
            if (httpMethod == null) {
                throw new IllegalArgumentException("Unsupported HTTP method: " + params.getMethod());
            }

            logger.info("Executing HTTP Task ID {}: Method={}, URL={}, Headers={}, Body Snippet='{}'",
                    taskConfig.getTaskId(), params.getMethod(), params.getUrl(), params.getHeaders(),
                    StringUtils.hasText(params.getBody()) ? params.getBody().substring(0, Math.min(params.getBody().length(), 100)) : "N/A");

            ResponseEntity<String> responseEntity = customRestTemplate.exchange(
                    params.getUrl(),
                    httpMethod,
                    requestEntity,
                    String.class
            );

            int httpStatusCode = responseEntity.getStatusCodeValue();
            String responseBody = responseEntity.getBody();
            responseSummary = "Status: " + httpStatusCode +
                    ". Response: " + (responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 500)) : "[No Body]");

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                logEntry.setState(ExecutionState.SUCCESS);
            } else {
                logEntry.setState(ExecutionState.FAILED);
                logEntry.setExMsg("HTTP Error: " + httpStatusCode);
            }
            logger.info("HTTP Task ID {} completed. {}", taskConfig.getTaskId(), responseSummary);
            return responseBody; // Return the body on success

        } catch (IllegalArgumentException e) {
            responseSummary = "Invalid task parameters: " + e.getMessage();
            logger.error("HTTP Task ID {} failed: {}", taskConfig.getTaskId(), responseSummary, e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg(responseSummary);
        } catch (HttpStatusCodeException e) {
            responseSummary = "HTTP Error: " + e.getStatusCode() + " " + e.getResponseBodyAsString();
            logger.warn("HTTP Task ID {} failed with status code {}. Response: {}", taskConfig.getTaskId(), e.getStatusCode(), e.getResponseBodyAsString());
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg(responseSummary.substring(0, Math.min(responseSummary.length(), 2000)));
        } catch (ResourceAccessException e) {
            responseSummary = "Resource access error: " + e.getMessage();
            logger.error("HTTP Task ID {} failed: Resource access error (e.g., timeout, DNS). {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg(responseSummary);
        } catch (Exception e) {
            responseSummary = "Unexpected error: " + e.getMessage();
            logger.error("HTTP Task ID {} failed: Unexpected error. {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg(responseSummary);
        } finally {
            logEntry.setRtnMsg(responseSummary != null ? responseSummary.substring(0, Math.min(responseSummary.length(), 500)) : "Execution finished.");
            taskExecuteLogDao.update(logEntry);
        }
        return null;
    }

    private HttpTaskParameters resolveParameters(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        String taskParamsJson = taskConfig.getParameters();
        String logParamsJson = logEntry.getParameters();

        HttpTaskParameters finalParams;

        // Start with base parameters from the TaskConfig
        if (StringUtils.hasText(taskParamsJson)) {
            finalParams = JSON.parseObject(taskParamsJson, HttpTaskParameters.class);
        } else {
            finalParams = new HttpTaskParameters();
        }

        // If there are overriding parameters in the log, merge them
        if (StringUtils.hasText(logParamsJson)) {
            Map<String, Object> logParamsMap = JSON.parseObject(logParamsJson);
            
            // This is a simple merge. A more sophisticated merge might be needed depending on the structure.
            // For HttpTaskParameters, we can just overwrite fields if they exist in the log parameters.
            if (logParamsMap.containsKey("url")) {
                finalParams.setUrl((String) logParamsMap.get("url"));
            }
            if (logParamsMap.containsKey("method")) {
                finalParams.setMethod((String) logParamsMap.get("method"));
            }
            if (logParamsMap.containsKey("headers")) {
                finalParams.setHeaders((Map<String, String>) logParamsMap.get("headers"));
            }
            if (logParamsMap.containsKey("body")) {
                finalParams.setBody((String) logParamsMap.get("body"));
            }
            if (logParamsMap.containsKey("connectTimeout")) {
                finalParams.setConnectTimeout((Integer) logParamsMap.get("connectTimeout"));
            }
            if (logParamsMap.containsKey("readTimeout")) {
                final Object readTimeout = logParamsMap.get("readTimeout");
                if (readTimeout instanceof Number) {
                    finalParams.setReadTimeout(((Number) readTimeout).intValue());
                }
            }
        }

        return finalParams;
    }


    private RestTemplate buildRestTemplate(HttpTaskParameters params) {
        RestTemplateBuilder builder = this.restTemplateBuilder;
        if (params.getConnectTimeout() != null && params.getConnectTimeout() > 0) {
            builder = builder.setConnectTimeout(Duration.ofMillis(params.getConnectTimeout()));
        }
        if (params.getReadTimeout() != null && params.getReadTimeout() > 0) {
            builder = builder.setReadTimeout(Duration.ofMillis(params.getReadTimeout()));
        }
        return builder.build();
    }
}
