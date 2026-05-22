package com.example.knowledgecopilot.security;

import com.example.knowledgecopilot.config.SecurityProperties;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.observability.ObservabilityTagUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GatewayRateLimitFilter extends OncePerRequestFilter {
    private static final long CLEANUP_EVERY_REQUESTS = 1_000L;

    private final SecurityProperties securityProperties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, CounterWindow> counters = new ConcurrentHashMap<>();
    private final AtomicLong seenRequests = new AtomicLong(0L);

    public GatewayRateLimitFilter(
        SecurityProperties securityProperties,
        MeterRegistry meterRegistry,
        ObjectMapper objectMapper
    ) {
        this.securityProperties = securityProperties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        SecurityProperties.RateLimit config = securityProperties.getRateLimit();
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = resolveLimit(request, config);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long nowSec = Instant.now().getEpochSecond();
        int windowSeconds = Math.max(1, config.getWindowSeconds());
        long windowId = nowSec / windowSeconds;
        String scope = scope(request);
        String subject = subject(request);
        String counterKey = scope + "|" + subject;

        AllowDecision decision = incrementAndCheck(counterKey, windowId, nowSec, limit);
        if (!decision.allowed()) {
            long retryAfterSec = windowSeconds - (nowSec % windowSeconds);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(Math.max(1L, retryAfterSec)));

            meterRegistry.counter(
                MetricNames.SECURITY_RATE_LIMIT_REJECTIONS_TOTAL,
                "path", ObservabilityTagUtils.normalizePath(request.getRequestURI()),
                "method", request.getMethod() == null ? "unknown" : request.getMethod(),
                "scope", scope
            ).increment();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "rate_limited");
            payload.put("message", "Rate limit exceeded for this endpoint.");
            payload.put("limit", limit);
            payload.put("windowSeconds", windowSeconds);
            payload.put("retryAfterSeconds", Math.max(1L, retryAfterSec));
            response.getWriter().write(objectMapper.writeValueAsString(payload));
            return;
        }

        maybeCleanup(config, windowId);
        filterChain.doFilter(request, response);
    }

    private int resolveLimit(HttpServletRequest request, SecurityProperties.RateLimit config) {
        String path = request.getRequestURI();
        if (path == null) {
            return config.getDefaultApiLimitPerWindow();
        }
        if (path.startsWith("/api/admin/")) {
            return config.getAdminLimitPerWindow();
        }
        if ("/api/assistant/ask".equals(path)) {
            return config.getAskLimitPerWindow();
        }
        if ("/api/assistant/feedback".equals(path)) {
            return config.getFeedbackLimitPerWindow();
        }
        return config.getDefaultApiLimitPerWindow();
    }

    private String scope(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return "api";
        }
        if (path.startsWith("/api/admin/")) {
            return "admin";
        }
        if ("/api/assistant/ask".equals(path)) {
            return "assistant_ask";
        }
        if ("/api/assistant/feedback".equals(path)) {
            return "assistant_feedback";
        }
        return "api";
    }

    private String subject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.isAuthenticated()
            && authentication.getName() != null
            && !authentication.getName().isBlank()
            && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return "user:" + authentication.getName();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return "ip:" + first;
            }
        }
        return "ip:" + (request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
    }

    private AllowDecision incrementAndCheck(String key, long windowId, long nowSec, int limit) {
        AllowState state = new AllowState();
        counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowId() != windowId) {
                state.allowed = true;
                return new CounterWindow(windowId, 1, nowSec);
            }

            int nextCount = existing.count() + 1;
            if (nextCount > limit) {
                state.allowed = false;
                return new CounterWindow(existing.windowId(), existing.count(), nowSec);
            }

            state.allowed = true;
            return new CounterWindow(existing.windowId(), nextCount, nowSec);
        });
        return new AllowDecision(state.allowed);
    }

    private void maybeCleanup(SecurityProperties.RateLimit config, long currentWindowId) {
        long count = seenRequests.incrementAndGet();
        if (count % CLEANUP_EVERY_REQUESTS != 0) {
            return;
        }

        int maxTracked = Math.max(1_000, config.getMaxTrackedKeys());
        if (counters.size() <= maxTracked) {
            return;
        }

        long keepThreshold = currentWindowId - 3;
        counters.entrySet().removeIf(entry -> entry.getValue().windowId() < keepThreshold);
    }

    private record CounterWindow(long windowId, int count, long lastSeenSec) {}

    private record AllowDecision(boolean allowed) {}

    private static final class AllowState {
        private boolean allowed;
    }
}
