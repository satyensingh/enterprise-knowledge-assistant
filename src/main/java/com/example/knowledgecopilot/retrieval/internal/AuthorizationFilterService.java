package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.SecurityProperties;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthorizationFilterService {
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public AuthorizationFilterService(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeChunk> filterAuthorizedChunks(List<KnowledgeChunk> candidates) {
        if (!securityProperties.isEnabled()) {
            return candidates;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        Set<String> authorityNames = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());

        if (authorityNames.contains("ROLE_ADMIN")) {
            return candidates;
        }

        String principalName = authentication.getName();
        return candidates.stream()
            .filter(chunk -> isAuthorized(chunk, authorityNames, principalName))
            .toList();
    }

    private boolean isAuthorized(KnowledgeChunk chunk, Set<String> authorityNames, String principalName) {
        String metadataJson = chunk.getMetadataJson();
        if (!StringUtils.hasText(metadataJson)) {
            return true;
        }

        JsonNode metadata;
        try {
            metadata = objectMapper.readTree(metadataJson);
        } catch (Exception ex) {
            return true;
        }

        JsonNode accessNode = metadata.path("access");
        if (accessNode.isMissingNode() || accessNode.isNull()) {
            accessNode = metadata.path("acl");
        }
        if (accessNode.isMissingNode() || accessNode.isNull()) {
            return true;
        }

        if (accessNode.path("public").asBoolean(false)) {
            return true;
        }

        Set<String> allowedRoles = readStringSet(accessNode.path("allowedRoles"));
        if (!allowedRoles.isEmpty()) {
            Set<String> normalizedAuthorities = new HashSet<>();
            for (String authority : authorityNames) {
                normalizedAuthorities.add(authority);
                if (authority.startsWith("ROLE_")) {
                    normalizedAuthorities.add(authority.substring("ROLE_".length()));
                }
            }
            if (!intersectionExists(allowedRoles, normalizedAuthorities)) {
                return false;
            }
        }

        Set<String> allowedUsers = readStringSet(accessNode.path("allowedUsers"));
        if (!allowedUsers.isEmpty() && !allowedUsers.contains(principalName)) {
            return false;
        }

        return true;
    }

    private Set<String> readStringSet(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Set.of();
        }

        if (node.isTextual()) {
            return Set.of(node.asText());
        }

        if (!node.isArray()) {
            return Set.of();
        }

        Set<String> values = new HashSet<>();
        node.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.asText());
            }
        });
        return values;
    }

    private boolean intersectionExists(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
