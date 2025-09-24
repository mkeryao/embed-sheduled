package com.github.embed.scheduler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER , ElementType.METHOD , ElementType.PACKAGE , ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JwtAuth {
    boolean required() default true;
}
