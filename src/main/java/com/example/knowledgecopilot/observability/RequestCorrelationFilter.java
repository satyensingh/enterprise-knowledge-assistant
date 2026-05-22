package com.example.knowledgecopilot.observability;

import com.example.knowledgecopilot.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String MDC_REQUEST_ID = "requestId";
    private final AppProperties appProperties;

    public RequestCorrelationFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String headerName = appProperties.getObservability().getRequestIdHeader();
        if (headerName == null || headerName.isBlank()) {
            headerName = "X-Request-Id";
        }

        String incomingRequestId = request.getHeader(headerName);
        String requestId = (incomingRequestId == null || incomingRequestId.isBlank())
            ? UUID.randomUUID().toString()
            : incomingRequestId.trim();

        response.setHeader(headerName, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
