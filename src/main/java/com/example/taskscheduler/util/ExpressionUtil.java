package com.example.taskscheduler.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExpressionUtil {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionUtil.class);

    // Pattern to find ${variable.path} or ${variable}
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    // Pattern for simple equality: ${key} == 'value' or ${key} == number or ${key} == boolean
    private static final Pattern EQUALITY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}\\s*==\\s*(['\"]?)([^'\"]+)\\2");
    // Pattern for existence: ${key} exists or ${key} not exists
    private static final Pattern EXISTS_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}\\s+(exists|not exists)");


    /**
     * Resolves a templated string like "${key.path}" or "${key}" from the context data.
     * @param template The string containing the template.
     * @param contextData The map of data to resolve from.
     * @return The resolved value, or the original template if not found or not a template.
     */
    public Object resolveValue(String template, Map<String, Object> contextData) {
        if (template == null || !template.startsWith("${") || !template.endsWith("}")) {
            return template; // Not a template or null
        }
        String keyPath = template.substring(2, template.length() - 1);
        return getValueFromPath(contextData, keyPath);
    }
    
    /**
     * Replaces all occurrences of ${variable.path} or ${variable} in a string.
     * @param inputString The string with templates.
     * @param contextData The data context.
     * @return The string with templates resolved.
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
                // Keep original template if value not found, or throw error, or replace with empty/default
                matcher.appendReplacement(sb, matcher.group(0)); // Keep ${...}
                 logger.warn("Template variable '{}' not found in context, keeping original.", keyPath);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


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
     * - "${key} == 'value'" (string literal)
     * - "${key} == value" (numeric or boolean literal)
     * - "${key} exists"
     * - "${key} not exists"
     * - "SUCCESS" or "FAILURE" (direct keywords, useful for simple conditions)
     * @param expression The expression string.
     * @param contextData The data context.
     * @return true if the expression evaluates to true, false otherwise.
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

            if (actualValue == null) return "null".equals(expectedValueStr); // Check if expecting "null"

            // Try to match type of actualValue for comparison
            try {
                if (actualValue instanceof Boolean) {
                    return ((Boolean) actualValue).equals(Boolean.parseBoolean(expectedValueStr));
                } else if (actualValue instanceof Number) {
                    // Handle potential floating point comparisons carefully if needed
                    // For now, simple string comparison after converting actual to string,
                    // or parse expectedValueStr to number.
                    // Let's try parsing expectedValueStr as Double for numeric comparison.
                    return ((Number) actualValue).doubleValue() == Double.parseDouble(expectedValueStr);
                } else { // Default to string comparison
                    return actualValue.toString().equals(expectedValueStr);
                }
            } catch (NumberFormatException e) {
                 // If expectedValueStr is not a number, fall back to string comparison
                return actualValue.toString().equals(expectedValueStr);
            }
        }

        Matcher existenceMatcher = EXISTS_PATTERN.matcher(expression);
        if (existenceMatcher.matches()) {
            String keyPath = existenceMatcher.group(1);
            String operator = existenceMatcher.group(2); // "exists" or "not exists"
            Object value = getValueFromPath(contextData, keyPath); // Check if path resolves to something non-null
            
            if ("exists".equalsIgnoreCase(operator)) {
                return value != null;
            } else if ("not exists".equalsIgnoreCase(operator)) {
                return value == null;
            }
        }
        
        logger.warn("Expression '{}' did not match any supported pattern. Evaluating to false.", expression);
        return false; // Default if no pattern matches
    }
}
