package com.example.knowledgecopilot.security;

import com.example.knowledgecopilot.config.SecurityProperties;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.observability.ObservabilityTagUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityProperties securityProperties,
        MeterRegistry meterRegistry,
        GatewayRateLimitFilter gatewayRateLimitFilter
    ) throws Exception {
        var bearerAuthEntryPoint = new BearerTokenAuthenticationEntryPoint();
        var bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();

        if (!securityProperties.isEnabled()) {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/assistant/health").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/assistant/ask", "/api/assistant/feedback").hasAnyRole("USER", "ADMIN")
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint((request, response, authException) -> {
                    recordAuthFailureMetric(request.getRequestURI(), request.getMethod(), 401, "unauthenticated", meterRegistry);
                    bearerAuthEntryPoint.commence(request, response, authException);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    recordAuthFailureMetric(request.getRequestURI(), request.getMethod(), 403, "forbidden", meterRegistry);
                    bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
                })
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    recordAuthFailureMetric(request.getRequestURI(), request.getMethod(), 401, authReason(authException), meterRegistry);
                    bearerAuthEntryPoint.commence(request, response, authException);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    recordAuthFailureMetric(request.getRequestURI(), request.getMethod(), 403, deniedReason(accessDeniedException), meterRegistry);
                    bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
                })
            )
            .addFilterAfter(gatewayRateLimitFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private void recordAuthFailureMetric(
        String requestPath,
        String method,
        int status,
        String reason,
        MeterRegistry meterRegistry
    ) {
        Counter counter = meterRegistry.counter(
            MetricNames.SECURITY_AUTH_FAILURES_TOTAL,
            "status", Integer.toString(status),
            "reason", ObservabilityTagUtils.trimTagValue(reason, 50),
            "path", ObservabilityTagUtils.normalizePath(requestPath),
            "method", method == null ? "unknown" : method
        );
        if (counter != null) {
            counter.increment();
        }
    }

    private String authReason(AuthenticationException exception) {
        return exception == null ? "authentication_error" : exception.getClass().getSimpleName();
    }

    private String deniedReason(AccessDeniedException exception) {
        return exception == null ? "access_denied" : exception.getClass().getSimpleName();
    }
}
