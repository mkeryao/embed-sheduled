package com.example.taskscheduler.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;

/**
 * Configures Fastjson as the primary JSON message converter for Spring MVC.
 * This replaces the default Jackson converter. It defines global serialization
 * features such as date formatting and handling of null values.
 */
@Configuration
public class FastjsonConfig implements WebMvcConfigurer {

    /**
     * Configures and registers the FastJsonHttpMessageConverter. This converter
     * will be used by Spring MVC for handling JSON request/response bodies.
     *
     * @return The configured FastJsonHttpMessageConverter.
     */
    @Bean
    public FastJsonHttpMessageConverter fastJsonHttpMessageConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        FastJsonConfig config = new FastJsonConfig();

        config.setSerializerFeatures(
                // Output fields with null values.
                SerializerFeature.WriteMapNullValue,
                // Write null List values as [].
                SerializerFeature.WriteNullListAsEmpty,
                // Write null String values as "".
                SerializerFeature.WriteNullStringAsEmpty,
                // Write null Boolean values as false.
                SerializerFeature.WriteNullBooleanAsFalse,
                // Disable circular reference detection to avoid "$ref" in output.
                // Be cautious if you have actual circular references in your DTOs/Entities.
                SerializerFeature.DisableCircularReferenceDetect,
                // Use yyyy-MM-dd HH:mm:ss format for Date objects.
                SerializerFeature.WriteDateUseDateFormat
        );
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");

        converter.setFastJsonConfig(config);
        converter.setDefaultCharset(StandardCharsets.UTF_8);

        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(MediaType.APPLICATION_JSON);
        supportedMediaTypes.add(MediaType.APPLICATION_JSON_UTF8);
        // Add other media types if your application produces/consumes them, e.g., application/vnd.custom+json
        converter.setSupportedMediaTypes(supportedMediaTypes);

        return converter;
    }

    /**
     * Adds the configured FastJson converter to the list of message converters.
     * By adding it here, we ensure it's available. To make it preferred over
     * other converters (like a potentially still present Jackson if not fully
     * excluded), it's usually added at the beginning of the list or other
     * converters are removed. Spring Boot typically orders converters, and
     * adding a custom one often gives it precedence. If
     * `spring-boot-starter-json` is excluded, this should become the primary
     * JSON converter.
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, fastJsonHttpMessageConverter()); // Add at the beginning to prioritize
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:E:/WorkspaceVscode/embed-sheduled/src/main/resources/static/");
    }

}
