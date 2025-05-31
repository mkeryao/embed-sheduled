package com.example.taskscheduler.config;

import com.example.taskscheduler.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    // Define paths to exclude from JWT validation
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/api/auth/login",
            "/error" // Spring Boot's default error path
            // Add other paths like H2 console if used, static resources, etc.
            // "/h2-console/" (and paths under it if H2 console is enabled and needs to be public)
    ));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String requestURI = request.getRequestURI();

        // Check if the path is excluded
        if (isPathExcluded(requestURI)) {
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

    private boolean isPathExcluded(String requestURI) {
        // Direct match
        if (EXCLUDED_PATHS.contains(requestURI)) {
            return true;
        }
        // Pattern match (e.g., for /h2-console/*)
        if (requestURI.startsWith("/h2-console")) { // Example if H2 console is used and public
             return true;
        }
        // Add more sophisticated matching if needed (e.g. AntPathMatcher)
        return false;
    }
}
