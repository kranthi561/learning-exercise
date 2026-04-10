package com.aiengineering.config;

import com.aiengineering.web.interceptor.LoggingInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.debug("addInterceptors: registering LoggingInterceptor for /api/**");
        // Register the logging interceptor and scope it to /api/** only,
        // so actuator and other internal paths are not logged redundantly.
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/api/**");
    }
}
