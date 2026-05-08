package com.uuidservice.config;

import com.uuidservice.core.CustomUUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UUIDConfig {

    private static final Logger log = LoggerFactory.getLogger(UUIDConfig.class);

    @Bean
    public CustomUUIDGenerator customUUIDGenerator(
            @Value("${uuid.datacenter-id:1}") long datacenterId,
            @Value("${uuid.worker-id:0}")     long workerId) {
        log.info("UUID node identity — datacenter={}, worker={}", datacenterId, workerId);
        return new CustomUUIDGenerator(datacenterId, workerId);
    }
}
