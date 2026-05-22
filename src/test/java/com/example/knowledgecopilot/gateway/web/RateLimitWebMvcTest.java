package com.example.knowledgecopilot.gateway.web;

import com.example.knowledgecopilot.config.SecurityProperties;
import com.example.knowledgecopilot.dto.AskRequest;
import com.example.knowledgecopilot.dto.AskResponse;
import com.example.knowledgecopilot.gateway.application.AnswerFeedbackService;
import com.example.knowledgecopilot.gateway.application.AuditEventService;
import com.example.knowledgecopilot.gateway.application.GatewayQueryService;
import com.example.knowledgecopilot.security.GatewayRateLimitFilter;
import com.example.knowledgecopilot.security.SecurityConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssistantController.class, properties = {
    "app.security.enabled=true",
    "app.security.rate-limit.enabled=true",
    "app.security.rate-limit.window-seconds=600",
    "app.security.rate-limit.ask-limit-per-window=1"
})
@Import({SecurityConfig.class, GatewayRateLimitFilter.class, RateLimitWebMvcTest.RateLimitTestConfig.class})
class RateLimitWebMvcTest {
    @TestConfiguration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class RateLimitTestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GatewayQueryService gatewayQueryService;

    @MockBean
    private AnswerFeedbackService answerFeedbackService;

    @MockBean
    private AuditEventService auditEventService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void askEndpointIsRateLimitedAfterConfiguredThreshold() throws Exception {
        when(gatewayQueryService.ask(any(AskRequest.class))).thenReturn(new AskResponse("response-1", "ok", List.of()));

        mockMvc.perform(post("/api/assistant/ask")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/assistant/ask")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isTooManyRequests());
    }
}
