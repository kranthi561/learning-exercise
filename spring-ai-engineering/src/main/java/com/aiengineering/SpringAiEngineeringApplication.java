package com.aiengineering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SpringAiEngineeringApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiEngineeringApplication.class, args);
    }
}
