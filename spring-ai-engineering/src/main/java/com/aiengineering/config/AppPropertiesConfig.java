package com.aiengineering.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Marks this as a configuration class so Spring processes it at startup.
@Configuration

// Tells Spring Boot to bind application.yml/properties values under
// the prefix defined in JwtProperties and register it as a bean.
// Without this (or @Component on JwtProperties itself), the record
// would not be available for injection.
@EnableConfigurationProperties(JwtProperties.class)
public class AppPropertiesConfig {}
