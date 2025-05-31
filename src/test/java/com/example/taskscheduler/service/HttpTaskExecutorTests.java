package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dto.taskparams.HttpTaskParameters;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.alibaba.fastjson.JSON; // Replaced ObjectMapper
import com.alibaba.fastjson.JSONException; // For testing Fastjson parsing errors
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class HttpTaskExecutorTests {

    @Mock
    private TaskExecuteLogDao taskExecuteLogDao;
    @Mock
    private RestTemplateBuilder restTemplateBuilder;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private HttpTaskExecutor httpTaskExecutor;

    private TaskConfig taskConfig;
    private TaskExecuteLog logEntry;
    private HttpTaskParameters httpParams;

    @BeforeEach
    void setUp() {
        taskConfig = new TaskConfig();
        taskConfig.setTaskId(1);
        taskConfig.setTaskName("TestHttpTask");

        logEntry = new TaskExecuteLog();
        logEntry.setLogId(101);
        logEntry.setTaskId(taskConfig.getTaskId());
        logEntry.setState("RUNNING"); // Initial state

        httpParams = new HttpTaskParameters();
        httpParams.setUrl("http://example.com/api/test");
        httpParams.setMethod("GET");

        // Mock RestTemplateBuilder chain
        lenient().when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.build()).thenReturn(restTemplate);
    }

    // ObjectMapper mock and mockTaskParams() helpers are removed as HttpTaskExecutor now uses Fastjson directly.

    @Test
    void testExecute_SuccessfulGet() throws Exception {
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("Success response", HttpStatus.OK);
        when(restTemplate.exchange(
                eq(httpParams.getUrl()),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(mockResponseEntity);

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("SUCCESS", logEntry.getState());
        assertTrue(logEntry.getRtnMsg().contains("Status: 200"));
        assertTrue(logEntry.getRtnMsg().contains("Success response"));
        assertNull(logEntry.getExMsg());
        verify(taskExecuteLogDao).updateLogStatus(logEntry.getLogId(), "SUCCESS", null);
    }

    @Test
    void testExecute_SuccessfulPostWithHeadersAndBody() throws Exception {
        HttpTaskParameters postParams = new HttpTaskParameters();
        postParams.setUrl("http://example.com/api/post");
        postParams.setMethod("POST");
        postParams.setBody("{\"key\":\"value\"}");
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("Content-Type", "application/json");
        headersMap.put("X-Custom-Header", "customValue");
        postParams.setHeaders(headersMap);
        postParams.setConnectTimeout(5000);
        postParams.setReadTimeout(10000);

        taskConfig.setBeanParameters(JSON.toJSONString(postParams));

        ArgumentCaptor<HttpEntity<String>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("POST successful", HttpStatus.CREATED);
        when(restTemplate.exchange(
                eq(postParams.getUrl()),
                eq(HttpMethod.POST),
                httpEntityCaptor.capture(),
                eq(String.class)))
                .thenReturn(mockResponseEntity);

        when(restTemplateBuilder.setConnectTimeout(Duration.ofMillis(5000))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(Duration.ofMillis(10000))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate); // Ensure build is called on the potentially reconfigured builder


        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("SUCCESS", logEntry.getState());
        assertTrue(logEntry.getRtnMsg().contains("Status: 201"));

        HttpEntity<String> capturedEntity = httpEntityCaptor.getValue();
        assertEquals("{\"key\":\"value\"}", capturedEntity.getBody());
        assertEquals("application/json", capturedEntity.getHeaders().getContentType().toString());
        assertEquals("customValue", capturedEntity.getHeaders().getFirst("X-Custom-Header"));

        verify(taskExecuteLogDao).updateLogStatus(logEntry.getLogId(), "SUCCESS", null);
        verify(restTemplateBuilder).setConnectTimeout(Duration.ofMillis(5000));
        verify(restTemplateBuilder).setReadTimeout(Duration.ofMillis(10000));
    }

    @Test
    void testExecute_ClientError4xx() throws Exception {
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found", "Response Body".getBytes(), null));

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getRtnMsg().contains("Status: 404"));
        assertTrue(logEntry.getRtnMsg().contains("Response Body"));
        assertTrue(logEntry.getExMsg().contains("Status: 404") && logEntry.getExMsg().contains("Error: Response Body"), "exMsg check failed. Actual: " + logEntry.getExMsg());
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("Status: 404")); // Verify with part of the actual exMsg content
    }

    @Test
    void testExecute_ServerError5xx() throws Exception {
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", "Error Details".getBytes(), null));

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getRtnMsg().contains("Status: 500"));
        assertTrue(logEntry.getRtnMsg().contains("Error Details"));
        assertTrue(logEntry.getExMsg().contains("Status: 500") && logEntry.getExMsg().contains("Error: Error Details"), "exMsg check failed. Actual: " + logEntry.getExMsg());
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("Status: 500")); // Verify with part of the actual exMsg content
    }

    @Test
    void testExecute_ConnectTimeoutSpecificException() throws Exception {
        httpParams.setConnectTimeout(100);
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("I/O error on GET request for \"" + httpParams.getUrl() + "\": connect timed out"));

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getExMsg().contains("Resource access error:"));
        assertTrue(logEntry.getExMsg().contains("connect timed out"));
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("connect timed out"));
    }

    @Test
    void testExecute_ReadTimeoutSpecificException() throws Exception {
        httpParams.setReadTimeout(100);
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("I/O error on GET request for \"" + httpParams.getUrl() + "\": Read timed out"));

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getExMsg().contains("Resource access error:"));
        assertTrue(logEntry.getExMsg().contains("Read timed out"));
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("Read timed out"));
    }

    @Test
    void testExecute_InvalidParameters_MissingUrl() throws Exception {
        httpParams.setUrl(null); // Make URL null
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getExMsg().contains("URL and Method are mandatory"));
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("URL and Method are mandatory"));
    }

    @Test
    void testExecute_InvalidParameters_JsonError() throws Exception {
        taskConfig.setBeanParameters("This is not JSON"); // Invalid JSON
        // HttpTaskExecutor now uses JSON.parseObject, which throws com.alibaba.fastjson.JSONException
        // The executor catches this and sets a specific exMsg.

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getExMsg().contains("Invalid task parameters JSON format:"), "Expected message not found: " + logEntry.getExMsg());
        // The specific error message from Fastjson might be appended, e.g., "Invalid task parameters JSON format: syntax error, pos 1"
        // For robustness, we check for the prefix HttpTaskExecutor sets.
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("Invalid task parameters JSON format:"));
    }

    @Test
    void testExecute_NoBeanParameters() {
        taskConfig.setBeanParameters(null); // No parameters defined

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getExMsg().contains("HTTP task parameters (beanParameters) are missing or empty."));
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("HTTP task parameters (beanParameters) are missing or empty."));
    }

    @Test
    void testExecute_UnsupportedHttpMethod() throws Exception {
        httpParams.setMethod("INVALID_METHOD");
        taskConfig.setBeanParameters(JSON.toJSONString(httpParams));

        httpTaskExecutor.execute(taskConfig, logEntry);

        assertEquals("FAILED", logEntry.getState());
        assertTrue(logEntry.getExMsg().contains("Unsupported HTTP method: INVALID_METHOD"));
        verify(taskExecuteLogDao).updateLogStatus(eq(logEntry.getLogId()), eq("FAILED"), contains("Unsupported HTTP method: INVALID_METHOD"));
    }
}
