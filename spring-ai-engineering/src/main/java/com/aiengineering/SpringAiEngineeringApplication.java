package com.aiengineering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Combines @Configuration + @EnableAutoConfiguration + @ComponentScan —
// registers all beans under this package automatically.
@SpringBootApplication

// Activates JPA auditing so @CreatedDate / @LastModifiedDate fields
// in BaseAuditingEntity are automatically populated on save.
@EnableJpaAuditing
public class SpringAiEngineeringApplication {

    public static void main(String[] args) {
        // Bootstraps the embedded server, loads application context, and starts the app.
        SpringApplication.run(SpringAiEngineeringApplication.class, args);
    }
}
