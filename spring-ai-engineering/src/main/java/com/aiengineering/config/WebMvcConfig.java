package com.aiengineering.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {
    // LoggingInterceptor was a HandlerInterceptor registered here but has been
    // converted to an OncePerRequestFilter so it can run before RateLimitFilter.
    // Register future MVC-level interceptors here.
}
