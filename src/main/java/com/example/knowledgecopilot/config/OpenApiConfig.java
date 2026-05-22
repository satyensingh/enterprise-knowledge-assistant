package com.example.knowledgecopilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI knowledgeCopilotOpenApi() {
        String bearerSchemeName = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("Knowledge Copilot API")
                .description("Gateway API for assistant chat, evaluation workflows, and admin operations.")
                .version("v1")
                .contact(new Contact().name("Knowledge Copilot Team")))
            .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
            .components(new Components()
                .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
