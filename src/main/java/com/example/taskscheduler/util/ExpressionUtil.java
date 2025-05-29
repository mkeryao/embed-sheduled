package com.example.taskscheduler.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for resolving template strings and evaluating simple expressions.
 * Templates are in the format `${key.subkey...}`.
 * Expressions support simple equality `"${key} == 'value'"` and existence `"${key} exists"`.
 */
@Component
public class ExpressionUtil {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionUtil.class);

    // Pattern to find ${variable.path} or ${variable}
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    // Pattern for simple equality: ${key} == 'value' or ${key} == number or ${key} == boolean
    // Group 1: keyPath, Group 2: optional quote, Group 3: value
    private static final Pattern EQUALITY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}\\s*==\\s*(['\"]?)([^'\"]+)\\2");
    // Pattern for existence: ${key} exists or ${key} not exists
    // Group 1: keyPath, Group 2: "exists" or "not exists"
    private static final Pattern EXISTS_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}\\s+(exists|not exists)");


    /**
     * Resolves a templated string like "${key.path}" or "${key}" from the context data.
     * If the input is not a valid template (doesn't start with ${ and end with }), it's returned as is.
     *
     * @param template The string containing the template.
     * @param contextData The map of data to resolve from.
     * @return The resolved value as an Object (could be String, Number, Boolean, etc.), 
     *         the original template string if the key is not found in context, 
     *         or the original input if it's not a template.
     */
    public Object resolveValue(String template, Map<String, Object> contextData) {
        if (template == null || !template.startsWith("${") || !template.endsWith("}")) {
            return template; // Not a template or null
        }
        String keyPath = template.substring(2, template.length() - 1);
        Object value = getValueFromPath(contextData, keyPath);
        return value != null ? value : template; // Return template string if value not found
    }
    
    /**
     * Replaces all occurrences of ${variable.path} or ${variable} in a string 
     * with their corresponding values from the contextData.
     * If a template variable is not found in the context, it remains unresolved in the string.
     *
     * @param inputString The string with templates.
     * @param contextData The data context.
     * @return The string with templates resolved. Returns the original string if input is null or context is null/empty.
     */
    public String resolveTemplates(String inputString, Map<String, Object> contextData) {
        if (inputString == null || contextData == null || contextData.isEmpty()) {
            return inputString;
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(inputString);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String keyPath = matcher.group(1);
            Object value = getValueFromPath(contextData, keyPath);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            } else {
                // Keep original template if value not found
                matcher.appendReplacement(sb, matcher.group(0)); 
                 logger.warn("Template variable '{}' not found in context, keeping original.", keyPath);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    /**
     * Retrieves a value from a nested Map structure using a dot-separated path.
     *
     * @param context The map to search within.
     * @param path The dot-separated path to the desired value (e.g., "user.address.city").
     * @return The value found at the specified path, or null if the path is invalid or the value is not found.
     */
    @SuppressWarnings("unchecked")
    private Object getValueFromPath(Map<String, Object> context, String path) {
        if (!StringUtils.hasText(path) || context == null) {
            return null;
        }
        String[] keys = path.split("\\.");
        Object currentValue = context;
        for (String key : keys) {
            if (currentValue instanceof Map) {
                currentValue = ((Map<String, Object>) currentValue).get(key);
            } else {
                // Could add support for reflection on POJOs here if contextData values are not just Maps
                logger.warn("Cannot access path '{}' in non-Map object: {}", path, currentValue);
                return null;
            }
            if (currentValue == null) {
                return null; // Key not found or value is null
            }
        }
        return currentValue;
    }

    /**
     * Evaluates a simple expression against the context data.
     * Supported formats:
     * <ul>
     *   <li><code>"${key} == 'value'"</code> (string literal, single quotes optional if value is number/boolean)</li>
     *   <li><code>"${key} == value"</code> (numeric or boolean literal)</li>
     *   <li><code>"${key} exists"</code></li>
     *   <li><code>"${key} not exists"</code></li>
     *   <li><code>"SUCCESS"</code> (evaluates to true)</li>
     *   <li><code>"FAILURE"</code> (evaluates to false)</li>
     * </ul>
     * For 'exists'/'not exists', a key is considered to exist if it's present and its value is not null.
     *
     * @param expression The expression string.
     * @param contextData The data context.
     * @return true if the expression evaluates to true, false otherwise or if the expression is malformed/unsupported.
     */
    public boolean evaluate(String expression, Map<String, Object> contextData) {
        if (!StringUtils.hasText(expression)) {
            logger.warn("Expression is empty or null, evaluating to false.");
            return false; // Or true, depending on desired default for empty expressions
        }

        // Direct keywords
        if ("SUCCESS".equalsIgnoreCase(expression)) return true; // Default success path
        if ("FAILURE".equalsIgnoreCase(expression)) return false; // Default failure path (or handle separately)


        Matcher equalityMatcher = EQUALITY_PATTERN.matcher(expression);
        if (equalityMatcher.matches()) {
            String keyPath = equalityMatcher.group(1);
            String expectedValueStr = equalityMatcher.group(3);
            Object actualValue = getValueFromPath(contextData, keyPath);

            if (actualValue == null) return "null".equalsIgnoreCase(expectedValueStr); // Check if expecting "null" string

            // Try to match type of actualValue for comparison
            try {
                if (actualValue instanceof Boolean) {
                    return ((Boolean) actualValue).equals(Boolean.parseBoolean(expectedValueStr));
                } else if (actualValue instanceof Number) {
                    // Attempt to parse expectedValueStr as a number for numeric comparison
                    // Handle potential floating point comparisons carefully if needed
                    return ((Number) actualValue).doubleValue() == Double.parseDouble(expectedValueStr);
                } else { // Default to string comparison
                    return actualValue.toString().equals(expectedValueStr);
                }
            } catch (NumberFormatException e) {
                 // If expectedValueStr is not parseable as a number when actual is a number,
                 // or if any other parsing issue occurs, fall back to string comparison.
                return actualValue.toString().equals(expectedValueStr);
            }
        }

        Matcher existenceMatcher = EXISTS_PATTERN.matcher(expression);
        if (existenceMatcher.matches()) {
            String keyPath = existenceMatcher.group(1);
            String operator = existenceMatcher.group(2); // "exists" or "not exists"
            Object value = getValueFromPath(contextData, keyPath); // Check if path resolves to something non-null
            
            if ("exists".equalsIgnoreCase(operator)) {
                return value != null; // Key exists and its value is not null
            } else if ("not exists".equalsIgnoreCase(operator)) {
                return value == null; // Key doesn't exist or its value is null
            }
        }
        
        logger.warn("Expression '{}' did not match any supported pattern. Evaluating to false.", expression);
        return false; // Default if no pattern matches
    }
}
