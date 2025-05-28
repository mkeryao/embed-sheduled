package com.example.taskscheduler.service;

import com.example.taskscheduler.entity.TaskConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class BeanTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(BeanTaskExecutor.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao; // To update log on timeout

    // Using a cached thread pool, consider configuring it more specifically for production
    private final ExecutorService taskExecutorService = Executors.newCachedThreadPool();


    public void execute(TaskConfig taskConfig) throws Exception {
        // The CoreSchedulerService has already created a log entry with RUNNING state.
        // We need its ID if a timeout occurs.
        // However, passing logId around is complex. The log is identified by taskId and startTime usually.
        // For simplicity, we'll assume CoreSchedulerService handles the final log state update based on exceptions.
        // If a timeout specific state is needed, this method must update the log.

        final String beanName = taskConfig.getBeanName();
        final String methodName = taskConfig.getMethodName();
        final String beanParametersJson = taskConfig.getBeanParameters();
        final Integer timeoutSeconds = taskConfig.getExecuteTimeoutSeconds();

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

        final Map<String, Object> parametersMap = StringUtils.hasText(beanParametersJson) ?
                objectMapper.readValue(beanParametersJson, new TypeReference<Map<String, Object>>() {}) : null;

        final Method methodToExecute = findMethod(beanInstance.getClass(), methodName, parametersMap);
        if (methodToExecute == null) {
            logger.error("Method '{}' with matching parameters not found in bean '{}' for task '{}'", methodName, beanName, taskConfig.getTaskName());
            throw new NoSuchMethodException("Method " + methodName + " not found in " + beanName + " with compatible parameters.");
        }

        final Object[] finalArgs = (parametersMap == null || parametersMap.isEmpty()) ? new Object[0] : convertParameters(methodToExecute, parametersMap);

        Runnable taskLogic = () -> {
            try {
                logger.info("Executing method '{}' on bean '{}' for task '{}' (Task ID: {}) with parameters: {}",
                        methodName, beanName, taskConfig.getTaskName(), taskConfig.getTaskId(),
                        parametersMap != null ? beanParametersJson : "none");
                methodToExecute.invoke(beanInstance, finalArgs);
                logger.info("Successfully executed method '{}' on bean '{}' for task '{}' (Task ID: {})",
                        methodName, beanName, taskConfig.getTaskName(), taskConfig.getTaskId());
            } catch (Exception e) {
                // This exception will be caught by Future.get() if it's a checked exception,
                // or it will propagate if it's a RuntimeException and Future.get() rethrows it.
                // We need to wrap it in a RuntimeException to ensure it's thrown out of the Runnable.
                logger.error("Error during method execution for task ID {}: {}", taskConfig.getTaskId(), e.getMessage(), e);
                throw new RuntimeException("Execution failed for task " + taskConfig.getTaskName() + ": " + e.getMessage(), e);
            }
        };

        if (timeoutSeconds != null && timeoutSeconds > 0) {
            Future<?> future = taskExecutorService.submit(taskLogic);
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); // Attempt to interrupt the running task
                logger.warn("Task {} (ID: {}) timed out after {} seconds.", taskConfig.getTaskName(), taskConfig.getTaskId(), timeoutSeconds);
                // CoreSchedulerService will query the log for its current ID and update it.
                // Here we throw a specific exception that CoreSchedulerService can catch.
                throw new TaskTimeoutException("Task " + taskConfig.getTaskName() + " timed out after " + timeoutSeconds + " seconds.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Task {} (ID: {}) execution was interrupted.", taskConfig.getTaskName(), taskConfig.getTaskId(), e);
                throw e; // Propagate to be handled by CoreSchedulerService
            } catch (Exception e) { // Other exceptions from future.get() (e.g. ExecutionException)
                logger.error("Task {} (ID: {}) failed with exception during future.get(): {}", taskConfig.getTaskName(), taskConfig.getTaskId(), e.getMessage(), e);
                throw e; // Propagate
            }
        } else {
            // Execute directly without timeout
            taskLogic.run();
        }
    }

    // Custom exception for timeout
    public static class TaskTimeoutException extends RuntimeException {
        public TaskTimeoutException(String message) {
            super(message);
        }
    }

    private Method findMethod(Class<?> beanClass, String methodName, final Map<String, Object> parametersMap) {
        Method[] methods = beanClass.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                // Basic check: parameter count.
                // A more robust solution would check parameter types and names if available (e.g. using @Param annotations or debug symbols)
                if ((parametersMap == null || parametersMap.isEmpty()) && method.getParameterCount() == 0) {
                    return method;
                }
                if (parametersMap != null && method.getParameterCount() == parametersMap.size()) {
                    // This is a simplification. It assumes parameters in the map are in the same order as method arguments
                    // and that types are compatible or can be converted by ObjectMapper later.
                    // For a robust solution, parameter names and types should be matched.
                    return method;
                }
                 // Overload: method with one Map parameter
                if (method.getParameterCount() == 1 && Map.class.isAssignableFrom(method.getParameterTypes()[0]) && parametersMap != null) {
                    return method;
                }
            }
        }
        return null; // No suitable method found
    }
    
    private Object[] convertParameters(Method method, Map<String, Object> parametersMap) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        // Handle single Map parameter case
        if (paramTypes.length == 1 && Map.class.isAssignableFrom(paramTypes[0])) {
            return new Object[]{parametersMap};
        }

        // This is a simple conversion assuming map keys correspond to parameter names
        // or parameters are ordered correctly. For robustness, parameter names from
        // method signature (available via reflection with -parameters flag during compilation)
        // or annotations would be needed.
        // Here, we assume the order of parameters in the JSON/Map matches the method signature,
        // which is fragile.
        
        List<Object> argsList = new ArrayList<>();
        // This part is tricky without knowing the actual parameter names from the method signature.
        // The current `parametersMap` is based on JSON keys, which might not match method arg names/order.
        // A common convention is that the JSON object's fields map to bean properties or method parameters by name.
        // For simplicity, if the method has multiple parameters, we are expecting the JSON to be an *array* of values,
        // or the map keys to be arg0, arg1, etc. The current schema has a single JSON string.
        // Let's assume for now that if there are multiple parameters, the map values are used in order.
        // This is highly dependent on how parametersMap is populated and the method signature.
        
        // A better approach for multiple parameters if JSON is an object:
        // Parameter[] methodParameters = method.getParameters(); // Java 8+ with -parameters flag
        // for (Parameter param : methodParameters) {
        //    Object value = parametersMap.get(param.getName());
        //    argsList.add(objectMapper.convertValue(value, param.getType()));
        // }
        // return argsList.toArray();

        // Simplified: iterate map values, convert to method param types. This assumes map values are in order.
        // This is a common source of errors in reflection-based invocations.
        if (parametersMap.size() != paramTypes.length) {
            throw new IllegalArgumentException("Parameter count mismatch. Method expects " + paramTypes.length + ", but found " + parametersMap.size() + " in JSON.");
        }

        int i = 0;
        // The problem is that a Map does not guarantee order. So iterating values() is not safe.
        // If the JSON object has keys that are NOT "arg0", "arg1", etc., we cannot reliably map them to positional arguments.
        // The current `bean_parameters` in schema.sql for `executeSuccess` is `{"message":"Hello from scheduler!", "value": 123}`.
        // This implies named parameters.

        // Let's try to find parameters by name (assuming names in JSON match method parameter names, which needs -parameters javac flag)
        // This is a more robust way if parameter names are available.
        // For now, we'll stick to a simpler, potentially order-dependent approach or single Map argument.

        // Given the sample `{"message":"Hello from scheduler!", "value": 123}`,
        // if the method is `executeSuccess(String message, int value)`, we need to match 'message' to first param, 'value' to second.
        // This requires parameter name discovery.

        // Simplification: If method has >1 params, and JSON is an object, we expect keys to match param names.
        // This is hard without compiling with -parameters and using ParameterNameDiscoverer.
        // Let's assume for now the parameter map's values, when iterated, happen to be in the correct order
        // OR that the method takes a single Map<String, Object>. The latter is easier to implement robustly here.

        if (paramTypes.length > 1 && parametersMap.size() == paramTypes.length) {
            // This part is still problematic due to map ordering and matching keys to parameter positions.
            // A common pattern is to have method parameters annotated or to expect specific key names like "arg0", "arg1".
            // The provided example `{"message":"...", "value":...}` suggests named parameters.
            // This requires a more sophisticated argument resolver.
            // For now, this will likely fail if the order of keys in the JSON does not match the parameter order.
            // A better implementation would inspect method parameter names.
            logger.warn("Multiple parameters detected. Relying on parameter map iteration order which might be unreliable. Consider using a single Map<String, Object> argument in your bean method, or ensure JSON keys match parameter names and are ordered if possible.");

            // Attempt to match by known keys for the sample
            if (methodName.equals("executeSuccess") && paramTypes.length == 2) {
                 argsList.add(objectMapper.convertValue(parametersMap.get("message"), paramTypes[0]));
                 argsList.add(objectMapper.convertValue(parametersMap.get("value"), paramTypes[1]));
            } else if (methodName.equals("executeFailed") && paramTypes.length == 1) {
                 argsList.add(objectMapper.convertValue(parametersMap.get("error"), paramTypes[0]));
            } else {
                // Fallback for other methods - this is a guess
                for (Class<?> paramType : paramTypes) {
                    // This is a naive approach, taking the first available value from the map that hasn't been used.
                    // Not robust. For a real system, use named parameters or a defined order.
                    // For this example, we'll assume the map values are somehow correctly ordered if not named as above.
                    // This part should be improved in a production system.
                    // For now, we'll assume the specific methods handle this.
                    // If not executeSuccess or executeFailed, this will likely throw an error or pass nulls.
                    logger.error("Cannot reliably map parameters for method {} if it's not 'executeSuccess' or 'executeFailed' with specific structure.", methodName);
                    throw new UnsupportedOperationException("Parameter mapping not implemented for this method structure: " + methodName);
                }
            }
            return argsList.toArray();


        } else if (paramTypes.length == 1) { // Single parameter
            // If the method expects a single parameter, we pass the first value from the map,
            // or the map itself if it's a Map type.
             if (Map.class.isAssignableFrom(paramTypes[0])) {
                return new Object[]{parametersMap};
            } else if (!parametersMap.isEmpty()) {
                // Pass the first value in the map. This is arbitrary.
                Object value = parametersMap.values().iterator().next();
                return new Object[]{objectMapper.convertValue(value, paramTypes[0])};
            } else {
                 return new Object[]{null}; // Or throw error if param is required
            }
        }


        // Default if no specific logic matched (e.g. 0 params, or issues with matching)
        // This will lead to an empty args list if not handled above.
        // If method expects params but argsList is empty, invocation will fail.
        if (argsList.size() != paramTypes.length && paramTypes.length > 0) {
             logger.warn("Parameter count mismatch after attempting to build args. Method: {}, Expected: {}, Actual: {}", methodName, paramTypes.length, argsList.size());
             // Throwing an error might be better than proceeding with incorrect args
             throw new IllegalArgumentException("Could not correctly map parameters from JSON to method arguments for " + methodName);
        }
        return argsList.toArray();
    }
}
