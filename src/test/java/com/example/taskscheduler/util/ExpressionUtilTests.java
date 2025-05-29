package com.example.taskscheduler.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionUtilTests {

    private ExpressionUtil expressionUtil;
    private Map<String, Object> contextData;

    @BeforeEach
    void setUp() {
        expressionUtil = new ExpressionUtil();
        contextData = new HashMap<>();
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("name", "John Doe");
        userDetails.put("id", 123);
        contextData.put("user", userDetails);
        contextData.put("status", "ACTIVE");
        contextData.put("count", 10);
        contextData.put("isValid", true);
        contextData.put("nullValueKey", null);
        Map<String, Object> nodeA_output = new HashMap<>();
        nodeA_output.put("status", "SUCCESS");
        nodeA_output.put("value", 42);
        contextData.put("nodeA_output", nodeA_output);
    }

    // Tests for resolveTemplates
    @Test
    void testResolveSimpleTemplate() {
        assertEquals("ACTIVE", expressionUtil.resolveTemplates("${status}", contextData));
    }

    @Test
    void testResolveNestedTemplate() {
        assertEquals("John Doe", expressionUtil.resolveTemplates("${user.name}", contextData));
        assertEquals("123", expressionUtil.resolveTemplates("${user.id}", contextData));
    }

    @Test
    void testResolveTemplateWithNonExistingKey() {
        assertEquals("${nonExistingKey}", expressionUtil.resolveTemplates("${nonExistingKey}", contextData));
    }
    
    @Test
    void testResolveTemplateWithNonExistingNestedKey() {
        assertEquals("${user.nonExisting}", expressionUtil.resolveTemplates("${user.nonExisting}", contextData));
    }

    @Test
    void testResolveMultipleTemplatesInString() {
        assertEquals("User John Doe (ID: 123) is ACTIVE.",
                expressionUtil.resolveTemplates("User ${user.name} (ID: ${user.id}) is ${status}.", contextData));
    }

    @Test
    void testResolveTemplateWithNullContext() {
        assertEquals("Template: ${user.name}", expressionUtil.resolveTemplates("Template: ${user.name}", null));
    }

    @Test
    void testResolveTemplateWithEmptyContext() {
        assertEquals("Template: ${user.name}", expressionUtil.resolveTemplates("Template: ${user.name}", Collections.emptyMap()));
    }
    
    @Test
    void testResolveNonTemplateString() {
        assertEquals("This is a plain string.", expressionUtil.resolveTemplates("This is a plain string.", contextData));
    }

    @Test
    void testResolveNullInputString() {
        assertNull(expressionUtil.resolveTemplates(null, contextData));
    }

    // Tests for resolveValue (direct single value resolution)
    @Test
    void testResolveValueSimple() {
        assertEquals("ACTIVE", expressionUtil.resolveValue("${status}", contextData));
    }

    @Test
    void testResolveValueNested() {
        assertEquals("John Doe", expressionUtil.resolveValue("${user.name}", contextData));
    }

    @Test
    void testResolveValueNonTemplate() {
        assertEquals("status", expressionUtil.resolveValue("status", contextData));
    }
    
    @Test
    void testResolveValueNonExistent() {
         assertNull(expressionUtil.resolveValue("${nonExistent.key}", contextData));
    }


    // Tests for evaluate

    @ParameterizedTest
    @CsvSource({
            "${status} == 'ACTIVE', true",
            "${status} == 'INACTIVE', false",
            "${user.name} == 'John Doe', true",
            "${user.name} == 'Jane Doe', false",
            "${count} == 10, true",
            "${count} == '10', true", // String representation of number
            "${count} == 5, false",
            "${isValid} == true, true",
            "${isValid} == 'true', true",
            "${isValid} == false, false",
            "${nodeA_output.status} == 'SUCCESS', true",
            "${nodeA_output.value} == 42, true"
    })
    void testEvaluateEquality(String expression, boolean expectedResult) {
        assertEquals(expectedResult, expressionUtil.evaluate(expression, contextData));
    }

    @Test
    void testEvaluateEqualityWithNullActualValue() {
        assertFalse(expressionUtil.evaluate("${nullValueKey} == 'someValue'", contextData));
        assertTrue(expressionUtil.evaluate("${nullValueKey} == null", contextData)); // Special case for 'null' string
        assertFalse(expressionUtil.evaluate("${nonExistentKey} == 'value'", contextData)); // Non-existent implies null
        assertTrue(expressionUtil.evaluate("${nonExistentKey} == null", contextData));
    }
    
    @ParameterizedTest
    @CsvSource({
            "${status} exists, true",
            "${status} not exists, false",
            "${nonExistentKey} exists, false",
            "${nonExistentKey} not exists, true",
            "${user.name} exists, true",
            "${user.nonExistent} exists, false",
            "${nullValueKey} exists, false", // A key that exists but its value is null
            "${nullValueKey} not exists, true" // A key that exists but its value is null
    })
    void testEvaluateExistence(String expression, boolean expectedResult) {
        assertEquals(expectedResult, expressionUtil.evaluate(expression, contextData));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "success"})
    void testEvaluateSuccessKeyword(String expression) {
        assertTrue(expressionUtil.evaluate(expression, contextData));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILURE", "failure"})
    void testEvaluateFailureKeyword(String expression) {
        assertFalse(expressionUtil.evaluate(expression, contextData));
    }
    
    @Test
    void testEvaluateInvalidExpression() {
        assertFalse(expressionUtil.evaluate("${status} = 'ACTIVE'", contextData)); // Single equals
        assertFalse(expressionUtil.evaluate("just plain text", contextData));
        assertFalse(expressionUtil.evaluate("${status} == ACTIVE", contextData)); // Missing quotes for string
    }

    @Test
    void testEvaluateEmptyOrNullExpression() {
        assertFalse(expressionUtil.evaluate("", contextData));
        assertFalse(expressionUtil.evaluate(null, contextData));
    }
}
