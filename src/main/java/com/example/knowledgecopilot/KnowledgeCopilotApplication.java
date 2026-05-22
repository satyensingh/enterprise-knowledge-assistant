package com.example.knowledgecopilot;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, SecurityProperties.class})
public class KnowledgeCopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeCopilotApplication.class, args);
    }
}
