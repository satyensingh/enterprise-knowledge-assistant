package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.SecurityProperties;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.entity.KnowledgeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationFilterServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyChunksAllowedForTheAuthenticatedRole() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        AuthorizationFilterService service = new AuthorizationFilterService(properties, objectMapper);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "kc_user",
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );

        KnowledgeChunk publicChunk = chunk("{}");
        KnowledgeChunk restrictedToAdmins = chunk("""
            {"access":{"allowedRoles":["ADMIN"]}}
            """);
        KnowledgeChunk restrictedToAssistant = chunk("""
            {"access":{"allowedRoles":["USER"]}}
            """);

        List<KnowledgeChunk> filtered = service.filterAuthorizedChunks(
            List.of(publicChunk, restrictedToAdmins, restrictedToAssistant)
        );

        assertEquals(List.of(publicChunk, restrictedToAssistant), filtered);
    }

    @Test
    void adminRoleBypassesChunkAcl() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        AuthorizationFilterService service = new AuthorizationFilterService(properties, objectMapper);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "kc_admin",
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );

        KnowledgeChunk restricted = chunk("""
            {"access":{"allowedRoles":["USER"]}}
            """);

        assertEquals(List.of(restricted), service.filterAuthorizedChunks(List.of(restricted)));
    }

    @Test
    void anonymousRequestsReturnNoChunksWhenSecurityIsEnabled() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        AuthorizationFilterService service = new AuthorizationFilterService(properties, objectMapper);

        assertEquals(List.of(), service.filterAuthorizedChunks(List.of(chunk("{}"))));
    }

    private KnowledgeChunk chunk(String metadataJson) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID());
        document.setSourceType("FILE_SYSTEM");
        document.setSourcePath("/tmp/doc.txt");
        document.setFileName("doc.txt");
        document.setTitle("Doc");
        document.setContentType("text/plain");
        document.setChecksum("checksum");
        document.setUpdatedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setIndexedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setMetadataJson("{}");

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocument(document);
        chunk.setChunkIndex(0);
        chunk.setContent("content");
        chunk.setEmbedding(new float[] {1.0f, 0.0f});
        chunk.setCitationLabel("doc.txt#chunk-0");
        chunk.setMetadataJson(metadataJson);
        return chunk;
    }
}
