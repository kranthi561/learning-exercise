package com.instagram.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates the InstagramFolderWatcher's @Scheduled scan.
@SpringBootApplication
@EnableScheduling
public class SpringAiMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiMcpApplication.class, args);
    }
}
