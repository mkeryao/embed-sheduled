package com.github.embed.scheduler.config;

import java.io.IOException;
import java.lang.reflect.Type;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import com.alibaba.fastjson.JSON;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yaomengke
 * @create 2022- 05 - 12 - 18:26
 */
@Slf4j(topic = "access")
@RestControllerAdvice
@Order(Integer.MIN_VALUE)
public class RestRequestBodyAdvice implements RequestBodyAdvice {

    @Autowired
    private Environment environment ;

    @Override
    public boolean supports(MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return true ;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) throws IOException {
        return httpInputMessage ;
    }

    @Override
    public Object afterBodyRead(Object o, HttpInputMessage httpInputMessage,
                                MethodParameter methodParameter, Type type,
                                Class<? extends HttpMessageConverter<?>> aClass) {
        log.info("[REQUEST][BODY] [{}]", JSON.toJSONString(o));
        return o ;
    }

    @Override
    public Object handleEmptyBody(Object o, HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return o ;
    }

}
