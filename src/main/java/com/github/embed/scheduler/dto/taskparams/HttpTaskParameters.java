package com.github.embed.scheduler.dto.taskparams;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * DTO for storing parameters specific to HTTP tasks.
 * This object will be serialized to/from JSON and stored in TaskConfig.beanParameters
 * when taskType is HTTP.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpTaskParameters {
    /** The URL to which the HTTP request will be sent. */
    private String url;

    /** The HTTP method to use (e.g., "GET", "POST", "PUT", "DELETE"). */
    private String method;

    /** A map of HTTP headers to include in the request. */
    private Map<String, String> headers;

    /** The request body, typically a JSON string for POST/PUT requests. */
    private String body;

    /** Connection timeout in milliseconds. */
    private Integer connectTimeout; // Optional, use RestTemplate default if null

    /** Read timeout in milliseconds. */
    private Integer readTimeout;    // Optional, use RestTemplate default if null
}
