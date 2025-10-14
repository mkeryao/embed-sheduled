package com.github.embed.scheduler.service;

import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.dto.taskparams.HttpTaskParameters;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionState;
import com.alibaba.fastjson.JSON; // Fastjson import
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

/**
 * Service responsible for executing HTTP tasks.
 * It parses HTTP parameters from TaskConfig, makes the HTTP request using RestTemplate,
 * and updates the TaskExecuteLog with the outcome.
 */
@Service
public class HttpTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HttpTaskExecutor.class);

    // ObjectMapper no longer needed if Fastjson is used exclusively for this internal parsing too
    // @Autowired
    // private ObjectMapper objectMapper;

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao; // To update log directly

    @Autowired
    private RestTemplateBuilder restTemplateBuilder; // For creating RestTemplate with specific timeouts


    /**
     * Executes an HTTP task based on the provided TaskConfig.
     * The TaskConfig's parameters field is expected to contain a JSON string
     * representing HttpTaskParameters.
     *
     * @param taskConfig The configuration of the HTTP task.
     * @param logEntry   The execution log entry associated with this task run. Its status and messages will be updated.
     */
    public void execute(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        HttpTaskParameters params = null;
        String responseSummary = null;
        int httpStatusCode = -1;

        try {
            if (!StringUtils.hasText(taskConfig.getParameters())) {
                throw new IllegalArgumentException("HTTP task parameters (parameters) are missing or empty.");
            }
            // Replace with Fastjson parsing
            params = JSON.parseObject(taskConfig.getParameters(), HttpTaskParameters.class);


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

            httpStatusCode = responseEntity.getStatusCodeValue();
            String responseBody = responseEntity.getBody();
            responseSummary = "Status: " + httpStatusCode +
                              ". Response: " + (responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 500)) : "[No Body]");

            logEntry.setExMsg(null); // Clear any previous exMsg if retrying
            // Consider HTTP status codes: 2xx are success. Others might be failures depending on requirements.
            // For now, any 2xx is considered SUCCESS for the task step.
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                logEntry.setState(ExecutionState.SUCCESS);
                if (responseEntity.getBody() != null) {
                    responseSummary += ", Body: " + responseEntity.getBody().substring(0, Math.min(responseEntity.getBody().length(), 200));
                }
            } else {
                logEntry.setState(ExecutionState.FAILED);
                logEntry.setExMsg("HTTP Error: " + httpStatusCode + ". " + responseSummary);
            }
            logger.info("HTTP Task ID {} completed. {}", taskConfig.getTaskId(), responseSummary);

        } catch (IllegalArgumentException e) { // JSON parsing exceptions from Fastjson are typically runtime (e.g., JSONException)
            logger.error("HTTP Task ID {} failed: Invalid parameters. {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("Invalid task parameters: " + e.getMessage());
        } catch (HttpStatusCodeException e) {
            responseSummary = "HTTP Error: " + e.getStatusCode() + " " + e.getResponseBodyAsString();
            logger.warn("HTTP Task ID {} failed with status code {}. Response: {}", taskConfig.getTaskId(), e.getStatusCode(), e.getResponseBodyAsString());
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg(responseSummary.substring(0, Math.min(responseSummary.length(), 2000)));
        } catch (ResourceAccessException e) { // Catches connect/read timeouts, DNS resolution issues etc.
            logger.error("HTTP Task ID {} failed: Resource access error (e.g., timeout, DNS). {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED); // Or "TIMED_OUT" if specifically identifiable
            logEntry.setExMsg("Resource access error: " + e.getMessage());
        } catch (com.alibaba.fastjson.JSONException e) { // Catch Fastjson specific parsing exception
            logger.error("HTTP Task ID {} failed: JSON parsing error. {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("Invalid task parameters JSON format: " + e.getMessage());
        } catch (Exception e) {
            logger.error("HTTP Task ID {} failed: Unexpected error. {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("Unexpected error: " + e.getMessage());
        } finally {
            // Update the log entry in the database
            // Log message (rtnMsg) can be used for success details or brief error summary
            logEntry.setRtnMsg(responseSummary != null ? responseSummary : (logEntry.getExMsg() != null ? logEntry.getExMsg().substring(0, Math.min(logEntry.getExMsg().length(), 500)) : "Execution finished."));
            taskExecuteLogDao.update(logEntry);
        }
    }

    private RestTemplate buildRestTemplate(HttpTaskParameters params) {
        RestTemplateBuilder builder = this.restTemplateBuilder; // Use the autowired one
        if (params.getConnectTimeout() != null && params.getConnectTimeout() > 0) {
            builder = builder.setConnectTimeout(Duration.ofMillis(params.getConnectTimeout()));
        }
        if (params.getReadTimeout() != null && params.getReadTimeout() > 0) {
            builder = builder.setReadTimeout(Duration.ofMillis(params.getReadTimeout()));
        }
        return builder.build();
    }
}
