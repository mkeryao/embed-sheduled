package com.github.embed.scheduler.service;

import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.entity.TaskConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.ParserConfig; // Added import
import com.alibaba.fastjson.util.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter; // Import Parameter
import java.util.ArrayList;
import java.util.Collections;
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
    private TaskExecuteLogDao taskExecuteLogDao;

    private final ExecutorService taskExecutorService = Executors.newCachedThreadPool();

    public static class TaskTimeoutException extends RuntimeException {
        public TaskTimeoutException(String message) {
            super(message);
        }
    }

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
            throw new Exception("Bean not found: " + beanName, e);
        }

        final Map<String, Object> parametersMap;
        if (StringUtils.hasText(beanParametersJson)) {
            try {
                parametersMap = JSON.parseObject(beanParametersJson, new TypeReference<Map<String, Object>>() {});
            } catch (JSONException e) {
                logger.error("Failed to parse bean parameters JSON (Fastjson) for task '{}': {}. Error: {}", taskConfig.getTaskName(), beanParametersJson, e.getMessage(), e);
                throw new Exception("Failed to parse bean parameters (Fastjson): " + e.getMessage(), e);
            }
        } else {
            parametersMap = Collections.emptyMap();
        }

        final Method methodToExecute = findMethod(beanInstance.getClass(), methodName, parametersMap);
        if (methodToExecute == null) {
            String errorMsg = String.format("Method '%s' with compatible parameters not found in bean '%s' for task '%s'. Check parameter names and types in JSON against method signature.",
                                            methodName, beanName, taskConfig.getTaskName());
            logger.error(errorMsg);
            throw new NoSuchMethodException(errorMsg);
        }

        final Object[] finalArgs = convertParameters(methodToExecute, parametersMap);

        Runnable taskLogic = () -> {
            try {
                logger.info("Executing method '{}' on bean '{}' for task '{}' (Task ID: {}) with parameters: {}",
                        methodName, beanName, taskConfig.getTaskName(), taskConfig.getTaskId(),
                        parametersMap != null && !parametersMap.isEmpty() ? beanParametersJson : "none");
                methodToExecute.invoke(beanInstance, finalArgs);
                logger.info("Successfully executed method '{}' on bean '{}' for task '{}' (Task ID: {})",
                        methodName, beanName, taskConfig.getTaskName(), taskConfig.getTaskId());
            } catch (Exception e) {
                logger.error("Error during method execution for task ID {}: {}", taskConfig.getTaskId(), e.getMessage(), e);
                // Ensure the original cause is propagated if it's a RuntimeException from the method itself
                if (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
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
     * Finds a suitable method on the bean's class that matches the given method name and
     * is compatible with the provided parameters.
     *
     * Strategy:
     * 1. Collect all methods with the exact specified name.
     * 2. From candidates, prioritize:
     *    a. A no-argument method if no parameters are provided.
     *    b. A method taking a single {@link Map} argument if parameters are provided (allows flexible parameter passing).
     *    c. A method where all its parameter names (requires -parameters javac flag) are present as keys in the input {@code parametersMap}.
     * 3. As a fallback, if only one method with the name exists, it's selected, and {@code convertParameters} will determine compatibility.
     * 4. If multiple methods exist and ambiguity remains (e.g., overloaded methods where parameter names don't fully resolve the choice based on map keys),
     *    it may log a warning and select the first candidate or one matching parameter count if unambiguous.
     *
     * @param beanClass The class of the bean.
     * @param methodName The name of the method to find.
     * @param parametersMap The map of parameters (from JSON) intended for the method.
     * @return A {@link Method} object if a suitable match is found, otherwise {@code null}.
     */
    private Method findMethod(Class<?> beanClass, String methodName, final Map<String, Object> parametersMap) {
        Method[] methods = beanClass.getMethods();
        List<Method> candidates = new ArrayList<>();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Prefer method where all its parameter names are present as keys in the parametersMap,
        // or if the method takes a single Map, or if method has no params and map is empty.
        for (Method candidate : candidates) {
            Parameter[] methodParams = candidate.getParameters();
            if (methodParams.length == 0 && (parametersMap == null || parametersMap.isEmpty())) {
                return candidate; // No-arg method for empty/null params
            }
            if (methodParams.length == 1 && Map.class.isAssignableFrom(methodParams[0].getType())) {
                 // If paramsMap is null, this isn't a good fit unless it's truly optional for the bean method
                if (parametersMap != null) return candidate;
            }
            if (parametersMap != null && methodParams.length > 0) {
                boolean allNamesFound = true;
                for (Parameter p : methodParams) {
                    if (!p.isNamePresent() || !parametersMap.containsKey(p.getName())) {
                        allNamesFound = false;
                        break;
                    }
                }
                if (allNamesFound) return candidate;
            }
        }

        // Fallback: if only one candidate, return it and let convertParameters try.
        // If multiple candidates and no clear match by name, this is ambiguous.
        if (candidates.size() == 1) {
             logger.warn("Only one method found for name '{}'. Proceeding with it: {}. Parameter name matching in convertParameters will be critical.", methodName, candidates.get(0).toGenericString());
            return candidates.get(0);
        }

        // If parametersMap is not null and not empty, and we still haven't found a match by name,
        // it's safer to return null than to guess based on param count alone.
        if (parametersMap != null && !parametersMap.isEmpty() && !candidates.isEmpty()) {
             logger.warn("Multiple method candidates for '{}' and no definitive match by parameter names. Candidates: {}", methodName, candidates);
             // Could try to match by param count as a last resort if only one such candidate exists
             List<Method> countMatchingCandidates = new ArrayList<>();
             for(Method c : candidates) {
                 if (c.getParameterCount() == parametersMap.size()) {
                     countMatchingCandidates.add(c);
                 }
             }
             if (countMatchingCandidates.size() == 1) {
                 logger.warn("Falling back to parameter count matching for method '{}'. Selected: {}", methodName, countMatchingCandidates.get(0).toGenericString());
                 return countMatchingCandidates.get(0);
             }
        }


        logger.warn("Could not find a definitive method match for '{}' with provided parameters.", methodName);
        return null;
    }

    /**
     * Converts a map of parameters (typically from a JSON object) into an array of arguments
     * suitable for invoking the specified method.
     * <p>
     * This method relies on Java 8's {@link Parameter#getName()} to retrieve actual parameter names,
     * which requires the Java compiler to be run with the {@code -parameters} flag. Spring Boot
     * projects usually enable this by default. If parameter names are not available, this method
     * will throw an {@link IllegalStateException}.
     * </p>
     * The conversion uses Fastjson's {@link TypeUtils#castToJavaBean(Object, Class)} for each parameter,
     * attempting to convert the value from the {@code parametersMap} (keyed by parameter name)
     * to the target method parameter type.
     * <p>
     * Special handling for methods expecting a single {@link Map} argument: the input {@code parametersMap}
     * is passed directly (or cast to the specific Map type if declared by the method).
     * </p>
     * If a parameter required by the method is not found in the {@code parametersMap}:
     * <ul>
     *   <li>If the method parameter is a primitive type, Fastjson's default for that primitive
     *       (e.g., 0 for int, false for boolean) will be used.</li>
     *   <li>If the method parameter is an object type, {@code null} will be passed.</li>
     * </ul>
     *
     * @param method The {@link Method} for which to convert parameters.
     * @param parametersMap A map where keys are parameter names and values are parameter values from JSON.
     * @return An array of {@link Object}s representing the converted arguments in the correct order for method invocation.
     * @throws IllegalArgumentException if a parameter value cannot be converted to the required type,
     *                                or if parameter names are required but not found (e.g. -parameters flag missing).
     * @throws IllegalStateException if parameter names are not available via reflection.
     */
    private Object[] convertParameters(Method method, Map<String, Object> parametersMap) throws Exception {
        Parameter[] methodParameters = method.getParameters();
        if (methodParameters.length == 0) {
            return new Object[0];
        }

        if (parametersMap == null) parametersMap = Collections.emptyMap();

        // Handle single Map argument case separately
        if (methodParameters.length == 1 && Map.class.isAssignableFrom(methodParameters[0].getType())) {
            // Ensure the map is compatible or convert it. Fastjson's TypeUtils.castToJavaBean can handle this.
            // The TypeReference here is tricky for generic Map<String, SpecificValueType>.
            // For Map<String, Object> or raw Map, direct casting or TypeUtils.castToMap might be okay.
            // If method truly expects Map<String, Object>, this is fine.
            // If it expects Map<String, SpecificType>, TypeUtils.castToJavaBean might work if the map structure matches.
             Object castedMap = TypeUtils.cast(parametersMap, methodParameters[0].getParameterizedType(), ParserConfig.getGlobalInstance()); // Changed to ParserConfig.getGlobalInstance()
            return new Object[]{castedMap};
        }

        Object[] convertedArgs = new Object[methodParameters.length];
        for (int i = 0; i < methodParameters.length; i++) {
            Parameter param = methodParameters[i];
            String paramName = param.getName(); // Relies on -parameters javac flag

            if (!param.isNamePresent()) {
                 logger.error("Parameter names not available for method '{}' (bean: {}). Ensure code is compiled with the -parameters javac flag.", method.getName(), method.getDeclaringClass().getSimpleName());
                 throw new IllegalStateException("Parameter names not available for method " + method.getName() + ". Compile with -parameters flag.");
            }

            Object valueFromMap = parametersMap.get(paramName);

            if (valueFromMap != null) {
                try {
                    convertedArgs[i] = TypeUtils.castToJavaBean(valueFromMap, param.getType());
                } catch (JSONException e) {
                    logger.error("Fastjson conversion error for parameter '{}' (type: {}), value: '{}'. Method: {}",
                                 paramName, param.getType().getSimpleName(), valueFromMap, method.getName(), e);
                    throw new IllegalArgumentException("Error converting parameter '" + paramName + "' to type " + param.getType().getSimpleName() + ". Value: " + valueFromMap, e);
                } catch (Exception e) {
                     logger.error("General error converting parameter '{}' (type: {}), value: '{}'. Method: {}",
                                 paramName, param.getType().getSimpleName(), valueFromMap, method.getName(), e);
                    throw new IllegalArgumentException("Error converting parameter '" + paramName + "' to type " + param.getType().getSimpleName() + ". Value: " + valueFromMap, e);
                }
            } else { // Value not in map for this parameter name
                if (param.getType().isPrimitive()) {
                    logger.warn("Parameter '{}' for method '{}' not found in JSON, and it's a primitive type ({}). Attempting to use Fastjson's default for primitive.",
                                 paramName, method.getName(), param.getType().getSimpleName());
                    // TypeUtils.cast(null, primitiveClass, config) should yield the default for primitives (e.g., 0 for int)
                    convertedArgs[i] = TypeUtils.cast(null, param.getType(), ParserConfig.getGlobalInstance());
                } else {
                    // For Object types, if not found, it's null. This is standard.
                    convertedArgs[i] = null;
                    logger.debug("Parameter '{}' for method '{}' not found in JSON. Passing null for type {}.",
                                 paramName, method.getName(), param.getType().getSimpleName());
                }
            }
        }
        return convertedArgs;
    }
}
