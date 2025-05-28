package com.example.taskscheduler.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public FilterRegistrationBean<JwtRequestFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtRequestFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(jwtRequestFilter);
        // Apply this filter to all API paths
        registrationBean.addUrlPatterns("/api/*"); 
        // Exclude paths are handled within the filter itself.
        // If specific ordering is needed with other filters, setOrder() can be used.
        registrationBean.setOrder(1); // Example order
        return registrationBean;
    }
}
