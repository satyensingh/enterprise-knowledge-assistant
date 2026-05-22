package com.example.knowledgecopilot.gateway.web;

import com.example.knowledgecopilot.dto.AnswerFeedbackResponse;
import com.example.knowledgecopilot.dto.AskRequest;
import com.example.knowledgecopilot.config.SecurityProperties;
import com.example.knowledgecopilot.dto.AskResponse;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import com.example.knowledgecopilot.gateway.application.AnswerFeedbackService;
import com.example.knowledgecopilot.gateway.application.AuditEventService;
import com.example.knowledgecopilot.gateway.application.GatewayQueryService;
import com.example.knowledgecopilot.gateway.application.ObservabilitySummaryService;
import com.example.knowledgecopilot.ingestion.api.IngestionModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.example.knowledgecopilot.security.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AssistantController.class, AdminController.class}, properties = {
    "app.security.enabled=true",
    "app.security.rate-limit.enabled=false"
})
@Import({SecurityConfig.class, SecurityWebMvcTest.SecurityTestConfig.class})
class SecurityWebMvcTest {
    @TestConfiguration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class SecurityTestConfig {
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
    private IngestionModule ingestionModule;

    @MockBean
    private ObservabilitySummaryService observabilitySummaryService;

    @MockBean
    private AuditEventService auditEventService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void askEndpointRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/assistant/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userRoleCanCallAskEndpoint() throws Exception {
        when(gatewayQueryService.ask(any(AskRequest.class))).thenReturn(new AskResponse("response-1", "ok", List.of()));

        mockMvc.perform(post("/api/assistant/ask")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void adminRoleCanCallAskEndpoint() throws Exception {
        when(gatewayQueryService.ask(any(AskRequest.class))).thenReturn(new AskResponse("response-1", "ok", List.of()));

        mockMvc.perform(post("/api/assistant/ask")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void userRoleCanSubmitFeedback() throws Exception {
        when(answerFeedbackService.submitFeedback(any())).thenReturn(
            new AnswerFeedbackResponse("response-1", FeedbackVerdict.GOOD, 1L, 0L, 1.0d)
        );

        mockMvc.perform(post("/api/assistant/feedback")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseId\":\"00000000-0000-0000-0000-000000000001\",\"verdict\":\"GOOD\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void feedbackEndpointRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/assistant/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseId\":\"00000000-0000-0000-0000-000000000001\",\"verdict\":\"GOOD\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userRoleCannotCallAdminEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/reindex")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleCanCallAdminEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/reindex")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isAccepted());
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/api/assistant/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
    }
}
