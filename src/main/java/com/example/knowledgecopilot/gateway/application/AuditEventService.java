package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.entity.AuditEvent;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.observability.RequestCorrelationFilter;
import com.example.knowledgecopilot.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventService {
    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AuditEventService(
        AuditEventRepository auditEventRepository,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public void record(
        String action,
        String resourceType,
        String resourceId,
        int statusCode,
        Map<String, Object> metadata
    ) {
        try {
            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID());
            event.setActor(currentActor());
            event.setAction(safeValue(action, "unknown_action"));
            event.setResourceType(blankToNull(resourceType));
            event.setResourceId(blankToNull(resourceId));
            event.setStatusCode(statusCode);
            event.setRequestId(blankToNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID)));
            event.setMetadataJson(toJson(metadata == null ? Map.of() : metadata));
            event.setCreatedAt(Instant.now());

            ServletRequestAttributes attrs = safeRequestAttributes();
            if (attrs != null) {
                event.setMethod(safeValue(attrs.getRequest().getMethod(), "UNKNOWN"));
                event.setPath(safeValue(attrs.getRequest().getRequestURI(), "/unknown"));
            } else {
                event.setMethod("UNKNOWN");
                event.setPath("/unknown");
            }

            auditEventRepository.save(event);
            meterRegistry.counter(
                MetricNames.AUDIT_EVENTS_TOTAL,
                "status", statusCode >= 400 ? "failure" : "success",
                "action", safeValue(action, "unknown_action")
            ).increment();
        } catch (RuntimeException ex) {
            log.warn("Failed to persist audit event for action={}", action, ex);
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private ServletRequestAttributes safeRequestAttributes() {
        try {
            var attributes = RequestContextHolder.currentRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
                return servletRequestAttributes;
            }
            return null;
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String safeValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
