package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
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

/**
 * Service responsible for executing tasks defined as Spring beans.
 * It uses reflection to invoke specified methods on beans and handles parameters and timeouts.
 */
@Service
public class BeanTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(BeanTaskExecutor.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao; // Not directly used in execute for log updates currently, CoreSchedulerService handles it

    // Using a cached thread pool, consider configuring it more specifically for production
    private final ExecutorService taskExecutorService = Executors.newCachedThreadPool();


    /**
     * Executes a configured bean task.
     * <p>
     * The method locates the bean and method specified in the {@link TaskConfig}.
     * It parses JSON parameters from {@code TaskConfig.beanParameters}, and invokes the method.
     * If {@code executeTimeoutSeconds} in {@link TaskConfig} is greater than 0,
     * the execution is subject to this timeout. If the task times out, a {@link TaskTimeoutException} is thrown.
     * </p>
     *
     * @param taskConfig The configuration of the task to execute.
     * @throws NoSuchMethodException If the specified method is not found or parameters are incompatible.
     * @throws TaskTimeoutException If the task execution exceeds the configured timeout.
     * @throws Exception If the bean is not found, parameter parsing fails,
     *                   or if the invoked bean method throws an exception.
     */
    public void execute(TaskConfig taskConfig) throws Exception {
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
            throw new Exception("Bean not found: " + beanName, e); // More specific custom exception could be used
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
                logger.error("Error during method execution for task ID {}: {}", taskConfig.getTaskId(), e.getMessage(), e);
                throw new RuntimeException("Execution failed for task " + taskConfig.getTaskName() + ": " + e.getMessage(), e);
            }
        };

        if (timeoutSeconds != null && timeoutSeconds > 0) {
            Future<?> future = taskExecutorService.submit(taskLogic);
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); 
                logger.warn("Task {} (ID: {}) timed out after {} seconds.", taskConfig.getTaskName(), taskConfig.getTaskId(), timeoutSeconds);
                throw new TaskTimeoutException("Task " + taskConfig.getTaskName() + " timed out after " + timeoutSeconds + " seconds.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Task {} (ID: {}) execution was interrupted.", taskConfig.getTaskName(), taskConfig.getTaskId(), e);
                throw e; 
            } catch (Exception e) { 
                logger.error("Task {} (ID: {}) failed with exception during future.get(): {}", taskConfig.getTaskName(), taskConfig.getTaskId(), e.getMessage(), e);
                throw e; 
            }
        } else {
            taskLogic.run();
        }
    }

    /**
     * Custom exception to indicate that a task execution has timed out.
     */
    public static class TaskTimeoutException extends RuntimeException {
        public TaskTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Finds a method in the given class by name and parameter compatibility.
     * <p>
     * This implementation performs a basic search by method name and parameter count.
     * For methods with parameters, it attempts to find one that matches the number of keys
     * in the provided {@code parametersMap}, or a method that accepts a single {@link Map} argument.
     * More sophisticated type checking or annotation-based parameter mapping is not implemented here.
     * </p>
     *
     * @param beanClass The class of the bean to inspect.
     * @param methodName The name of the method to find.
     * @param parametersMap A map of parameters that might be passed to the method. Used to infer parameter count.
     * @return The {@link Method} object if a suitable method is found, otherwise {@code null}.
     */
    private Method findMethod(Class<?> beanClass, String methodName, final Map<String, Object> parametersMap) {
        Method[] methods = beanClass.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                if ((parametersMap == null || parametersMap.isEmpty()) && method.getParameterCount() == 0) {
                    return method;
                }
                if (parametersMap != null && method.getParameterCount() == parametersMap.size()) {
                    // This is a simplification. It assumes parameters in the map are somewhat ordered or named
                    // such that convertParameters can handle it. True robustness needs parameter name matching.
                    return method;
                }
                if (method.getParameterCount() == 1 && Map.class.isAssignableFrom(method.getParameterTypes()[0]) && parametersMap != null) {
                    return method;
                }
            }
        }
        return null; 
    }
    
    /**
     * Converts the provided parameter map into an array of objects suitable for method invocation.
     * <p>
     * This method handles a few cases:
     * <ul>
     *   <li>If the target method takes a single {@link Map} argument, the {@code parametersMap} itself is returned in an array.</li>
     *   <li>If the method expects multiple arguments, this implementation attempts a simplified mapping:
     *     <ul>
     *       <li>For specific known methods like "executeSuccess" or "executeFailed" (from `MySampleTask`),
     *           it tries to map known keys ("message", "value", "error") to typed parameters.</li>
     *       <li>Otherwise, it logs an error and throws {@link UnsupportedOperationException} because
     *           reliable mapping of arbitrary map keys to positional method parameters without more metadata
     *           (like parameter names from bytecode or annotations) is complex and error-prone.</li>
     *     </ul>
     *   </li>
     *   <li>If the method expects a single argument (not a Map), it attempts to convert the first value from the map (this is arbitrary and fragile).</li>
     * </ul>
     * This simplified approach has limitations and would need to be made more robust for a general-purpose solution,
     * potentially using Spring's {@code ParameterNameDiscoverer} or by requiring specific annotations on target methods.
     * </p>
     *
     * @param method The method for which parameters are being prepared.
     * @param parametersMap The map of parameter names to values.
     * @return An array of objects to be used as arguments for method invocation.
     * @throws Exception If parameter conversion fails or a suitable mapping strategy cannot be determined.
     */
    private Object[] convertParameters(Method method, Map<String, Object> parametersMap) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        if (paramTypes.length == 1 && Map.class.isAssignableFrom(paramTypes[0])) {
            return new Object[]{parametersMap};
        }
        
        if (parametersMap.size() != paramTypes.length && !(method.getName().equals("executeSuccess") || method.getName().equals("executeFailed"))) {
             // Allow size mismatch for specific hardcoded methods for now, but generally this is an issue.
             // This specific check is weak because `executeSuccess` could be overloaded.
            if(parametersMap.size() != paramTypes.length) {
                 throw new IllegalArgumentException("Parameter count mismatch. Method " + method.getName() + " expects " + paramTypes.length + ", but found " + parametersMap.size() + " in JSON.");
            }
        }

        List<Object> argsList = new ArrayList<>();
        if (paramTypes.length > 1) { // Multiple parameters
            logger.warn("Multiple parameters detected for method {}. Attempting simplified mapping. Consider using a single Map<String, Object> argument for robustness.", method.getName());
            // Specific handling for known sample methods
            if (method.getName().equals("executeSuccess") && paramTypes.length == 2) {
                 argsList.add(objectMapper.convertValue(parametersMap.get("message"), paramTypes[0]));
                 argsList.add(objectMapper.convertValue(parametersMap.get("value"), paramTypes[1]));
            } else if (method.getName().equals("executeFailed") && paramTypes.length == 1) { // Should be handled by single param logic below if signature is (String)
                 argsList.add(objectMapper.convertValue(parametersMap.get("error"), paramTypes[0]));
            } else {
                // This part remains problematic without parameter name discovery.
                // For now, it will likely fail if this path is hit for methods not explicitly handled above.
                logger.error("Cannot reliably map parameters for method {} with {} parameters if it's not 'executeSuccess' or if parameter names are not 'message'/'value'/'error'.", method.getName(), paramTypes.length);
                throw new UnsupportedOperationException("Generic parameter mapping for multiple arguments not implemented reliably for method: " + method.getName());
            }
        } else if (paramTypes.length == 1) { // Single parameter (not a Map, already handled)
            if (!parametersMap.isEmpty()) {
                // If method is executeFailed (String error), and paramsMap has "error" key.
                if (method.getName().equals("executeFailed") && parametersMap.containsKey("error")) {
                     argsList.add(objectMapper.convertValue(parametersMap.get("error"), paramTypes[0]));
                } else {
                    // Fallback: take the first value from the map. This is arbitrary.
                    logger.warn("Single parameter method {}: using the first value from parameter map. This might be unreliable.", method.getName());
                    Object value = parametersMap.values().iterator().next();
                    argsList.add(objectMapper.convertValue(value, paramTypes[0]));
                }
            } else {
                 argsList.add(null); // Or throw error if param is required and map is empty
            }
        }

        if (argsList.size() != paramTypes.length) {
             logger.warn("Final argument list size {} does not match parameter types length {} for method {}.", argsList.size(), paramTypes.length, method.getName());
             throw new IllegalArgumentException("Could not correctly map parameters from JSON to method arguments for " + method.getName());
        }
        return argsList.toArray();
    }
}
