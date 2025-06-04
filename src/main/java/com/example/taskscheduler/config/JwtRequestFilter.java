package com.example.taskscheduler.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.taskscheduler.util.JwtUtil;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    private JwtUtil jwtUtil;
    
    // Define paths that require JWT validation
    // 在前后端分离架构中，只有API请求需要JWT验证
    private static final String API_PATH_PREFIX = "/api";
    
    // Define API paths to exclude from JWT validation
    private static final Set<String> EXCLUDED_API_PATHS = new HashSet<>(Arrays.asList(
            "/api/auth/login",  // 登录接口
            "/api/auth/register", // 如果有注册接口
            "/api/public"       // 如果有公共API
    ));
    
    @Override    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String requestURI = request.getRequestURI();
        
        // 记录请求URI用于调试
        logger.debug("Processing request: {}", requestURI);

        // 只拦截/api路径下的请求，其他全部放行
        // 这是实现前后端分离的关键，静态资源请求不需要JWT验证
        if (!requestURI.startsWith(API_PATH_PREFIX)) {
            logger.debug("Non-API request detected, skipping JWT filter: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }
        
        // 排除不需要JWT验证的API路径
        if (isExcludedApiPath(requestURI)) {
            logger.debug("Excluded API path detected, skipping JWT filter: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.getUsernameFromToken(jwt);
            } catch (IllegalArgumentException e) {
                logger.warn("Unable to get JWT Token: {}", e.getMessage());
            } catch (ExpiredJwtException e) {
                logger.warn("JWT Token has expired: {}", e.getMessage());
            } catch (SignatureException e) {
                logger.warn("JWT Signature validation failed: {}", e.getMessage());
            } catch (MalformedJwtException e) {
                logger.warn("JWT Token is malformed: {}", e.getMessage());
            }
        } else {
            logger.warn("Authorization header does not begin with Bearer String or is missing for path: {}", requestURI);
        }

        if (username != null && jwtUtil.validateToken(jwt, username)) { // Validating against the extracted username
            // Token is valid. Store username in request attribute if needed by controllers.
            // In a Spring Security setup, this is where SecurityContextHolder would be updated.
            request.setAttribute("username", username); // Controllers can access this via @RequestAttribute
            logger.debug("JWT token validated for user: {} and URI: {}", username, requestURI);
            chain.doFilter(request, response);
        } else {
            logger.warn("JWT Token validation failed for URI: {}. Responding with 401 Unauthorized.", requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid or missing JWT token\"}");
            response.setContentType("application/json");
            // Do not proceed with the filter chain
        }
    }

    private boolean isExcludedApiPath(String requestURI) {
        // Direct match with excluded API paths
        if (EXCLUDED_API_PATHS.contains(requestURI)) {
            return true;
        }
        
        // Pattern match for excluded API paths
        for (String excludedPath : EXCLUDED_API_PATHS) {
            if (excludedPath.endsWith("/**") && requestURI.startsWith(excludedPath.substring(0, excludedPath.length() - 3))) {
                return true;
            }
        }
        
        // H2控制台（如果有）
        if (requestURI.startsWith("/h2-console")) {
            return true;
        }
        
        return false;
    }
}
