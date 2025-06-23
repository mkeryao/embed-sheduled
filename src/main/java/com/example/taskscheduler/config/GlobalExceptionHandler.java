package com.example.taskscheduler.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Example custom exception (define this class elsewhere if used)
    // public static class ResourceNotFoundException extends RuntimeException {
    // public ResourceNotFoundException(String message) {
    // super(message);
    // }
    // }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, String>> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Unhandled exception caught by GlobalExceptionHandler: ", ex);
        logger.error(ex.getMessage(), ex);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", "An unexpected internal server error occurred: " + ex.getMessage());
        // In a production environment, you might not want to expose ex.getMessage()
        // directly to the client
        // depending on the sensitivity of the information.
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex,
            WebRequest request) {
        logger.error("Illegal argument exception: {}", ex.getMessage());
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", "Bad request: " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // @ExceptionHandler(HttpMessageNotReadableException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            WebRequest request) {
        logger.error(ex.getMessage(), ex);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", "HttpMessageNotReadableException: " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request) {
        logger.error(ex.getMessage(), ex);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", "HttpRequestMethodNotSupportedException: " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // Add more specific exception handlers as needed, for example:
    // @ExceptionHandler(ResourceNotFoundException.class)
    // @ResponseStatus(HttpStatus.NOT_FOUND)
    // public ResponseEntity<Map<String, String>>
    // handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest
    // request) {
    // logger.warn("Resource not found: {}", ex.getMessage());
    // Map<String, String> errorResponse = new HashMap<>();
    // errorResponse.put("status", "error");
    // errorResponse.put("message", ex.getMessage());
    // return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    // }

    // Example: To handle exceptions from your custom services if they throw
    // specific types
    // @ExceptionHandler(WorkflowExecutionException.class)
    // @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    // public ResponseEntity<Map<String, String>>
    // handleWorkflowException(WorkflowExecutionException ex, WebRequest request) {
    // logger.error("Workflow execution error: {}", ex.getMessage(), ex);
    // Map<String, String> errorResponse = new HashMap<>();
    // errorResponse.put("status", "error");
    // errorResponse.put("message", "Workflow processing failed: " +
    // ex.getMessage());
    // return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    // }
}
