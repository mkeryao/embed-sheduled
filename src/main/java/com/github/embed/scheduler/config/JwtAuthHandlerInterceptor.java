package com.github.embed.scheduler.config;

import com.github.embed.scheduler.annotation.JwtAuth;
import com.github.embed.scheduler.exception.AuthException;
import com.github.embed.scheduler.service.JwtAuthService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class JwtAuthHandlerInterceptor implements HandlerInterceptor {

    @Resource
    private JwtAuthService jwtAuthService;


    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        final String requestURI = request.getRequestURI();
        if (handler instanceof HandlerMethod) {
            JwtAuth jwtAuth = ((HandlerMethod) handler)
                    .getMethod()
                    .getDeclaringClass()
                    .getAnnotation(JwtAuth.class) ;
            if(Objects.nonNull(jwtAuth)){
                final String authorizationHeader = request.getHeader("Authorization");
                String username = null;
                String jwt = null;
                if (authorizationHeader != null && authorizationHeader.startsWith("Bearer")) {
                    jwt = authorizationHeader.substring(6);
                    try {
                        username = jwtAuthService.getUsernameFromToken(jwt);
                    } catch (IllegalArgumentException e) {
                        log.warn("[Unable to get JWT Token: {}]", e.getMessage());
                    } catch (ExpiredJwtException e) {
                        log.warn("[JWT Token has expired: {}]", e.getMessage());
                    } catch (SignatureException e) {
                        log.warn("[JWT Signature validation failed: {}]", e.getMessage());
                    } catch (MalformedJwtException e) {
                        log.warn("[JWT Token is malformed: {}]", e.getMessage());
                    }
                } else {
                    log.warn("[Authorization header does not begin with Bearer String or is missing for path: {}]",
                            requestURI);
                }
                if (StringUtils.hasText(username)
                        && jwtAuthService.validateToken(jwt, username)) { // Validating against the extracted username
                    request.setAttribute("username", username); // Controllers can access this via @RequestAttribute
                    log.debug("[JWT token validated for user: {} and URI: {}]", username, requestURI);
                    return true ;
                } else {
                    throw new AuthException("Invalid or expired JWT token");
                }
            }
        }
        return true;
    }
}
