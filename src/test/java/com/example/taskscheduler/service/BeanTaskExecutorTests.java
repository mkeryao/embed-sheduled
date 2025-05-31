package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.service.testbeans.TestBean; // Assuming TestBean is in this package for testing
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BeanTaskExecutorTests {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private TaskExecuteLogDao taskExecuteLogDao; // Mocked, though not directly used by execute in this version

    @InjectMocks
    private BeanTaskExecutor beanTaskExecutor;

    private TestBean testBeanInstance;

    @BeforeEach
    void setUp() {
        testBeanInstance = spy(new TestBean()); // Spy on the actual bean instance
    }

    private TaskConfig createTaskConfig(String beanName, String methodName, String paramsJson, Integer timeout) {
        TaskConfig config = new TaskConfig();
        config.setTaskId(1);
        config.setTaskName("TestBeanTask");
        config.setBeanName(beanName);
        config.setMethodName(methodName);
        config.setBeanParameters(paramsJson);
        config.setExecuteTimeoutSeconds(timeout);
        return config;
    }

    @Test
    void testExecute_SuccessfulNoParams() throws Exception {
        TaskConfig config = createTaskConfig("testBean", "doSomething", null, 0);
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        beanTaskExecutor.execute(config);

        verify(testBeanInstance).doSomething();
    }

    @Test
    void testExecute_SuccessfulWithParams() throws Exception {
        String paramsJson = "{\"message\":\"Hello\", \"count\":5}";
        TaskConfig config = createTaskConfig("testBean", "doSomethingWithParams", paramsJson, 0);
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        beanTaskExecutor.execute(config);

        verify(testBeanInstance).doSomethingWithParams("Hello", 5);
    }

    @Test
    void testExecute_SuccessfulWithMapParam() throws Exception {
        String paramsJson = "{\"message\":\"Hello Map\", \"value\":123}";
        TaskConfig config = createTaskConfig("testBean", "doSomethingWithMap", paramsJson, 0);
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        beanTaskExecutor.execute(config);

        // ObjectMapper inside convertParameters will convert the JSON to a Map
        // The spy testBeanInstance will be called with this map.
        // We are verifying the method on the spy is called.
        verify(testBeanInstance).doSomethingWithMap(anyMap());
    }


    @Test
    void testExecute_BeanNotFound() {
        TaskConfig config = createTaskConfig("nonExistentBean", "doSomething", null, 0);
        when(applicationContext.getBean("nonExistentBean")).thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("nonExistentBean"));

        Exception exception = assertThrows(Exception.class, () -> beanTaskExecutor.execute(config));
        assertTrue(exception.getMessage().contains("Bean not found: nonExistentBean"));
    }

    @Test
    void testExecute_MethodNotFound() {
        TaskConfig config = createTaskConfig("testBean", "nonExistentMethod", null, 0);
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        Exception exception = assertThrows(NoSuchMethodException.class, () -> beanTaskExecutor.execute(config));
        assertTrue(exception.getMessage().contains("nonExistentMethod not found in testBean"));
    }

    @Test
    void testExecute_MethodWithMismatchedParams_findMethodReturnsNull() {
        // TestBean has doSomethingWithParams(String, int)
        // We call it with a JSON that suggests a different signature or different number of params
        // so findMethod should fail.
        String paramsJson = "{\"onlyOneParam\":\"value\"}"; // This map has 1 entry, method expects 2
        TaskConfig config = createTaskConfig("testBean", "doSomethingWithParams", paramsJson, 0);
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        // This test relies on findMethod's logic. If findMethod is too lenient, this might pass.
        // Current findMethod logic: if param map is not null, count must match.
        Exception exception = assertThrows(NoSuchMethodException.class, () -> beanTaskExecutor.execute(config));
        assertTrue(exception.getMessage().contains("doSomethingWithParams not found in testBean with compatible parameters."));
    }


    @Test
    void testExecute_Timeout() throws Exception {
        TaskConfig config = createTaskConfig("testBean", "doSomethingSlow", null, 1); // 1 second timeout
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        // Make the actual method call slow
        doAnswer(invocation -> {
            try {
                TimeUnit.SECONDS.sleep(2); // Sleep for 2 seconds, longer than timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(testBeanInstance).doSomethingSlow();

        Exception exception = assertThrows(BeanTaskExecutor.TaskTimeoutException.class, () -> beanTaskExecutor.execute(config));
        assertTrue(exception.getMessage().contains("timed out after 1 seconds"));
        verify(testBeanInstance).doSomethingSlow(); // Verify it was called
    }

    @Test
    void testExecute_NoTimeout() throws Exception {
        TaskConfig config = createTaskConfig("testBean", "doSomething", null, 0); // No timeout
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        beanTaskExecutor.execute(config);

        verify(testBeanInstance).doSomething();
        // No timeout exception should be thrown
    }

    @Test
    void testExecute_MethodThrowsException() throws Exception {
        TaskConfig config = createTaskConfig("testBean", "throwExceptionMethod", null, 0);
        when(applicationContext.getBean("testBean")).thenReturn(testBeanInstance);

        // Configure the spy to throw an exception when throwExceptionMethod is called
        doThrow(new RuntimeException("Test bean failure")).when(testBeanInstance).throwExceptionMethod();

        Exception exception = assertThrows(RuntimeException.class, () -> beanTaskExecutor.execute(config));
        // The exception from invoke will be wrapped in RuntimeException("Execution failed for task...")
        assertTrue(exception.getMessage().contains("Execution failed for task TestBeanTask: Test bean failure"));
        verify(testBeanInstance).throwExceptionMethod();
    }
}
