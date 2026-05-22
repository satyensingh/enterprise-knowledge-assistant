package com.example.knowledgecopilot.gateway.web;

import com.example.knowledgecopilot.gateway.application.ObservabilitySummaryService;
import com.example.knowledgecopilot.gateway.application.AuditEventService;
import com.example.knowledgecopilot.ingestion.api.IngestionModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final IngestionModule ingestionModule;
    private final ObservabilitySummaryService observabilitySummaryService;
    private final AuditEventService auditEventService;

    public AdminController(
        IngestionModule ingestionModule,
        ObservabilitySummaryService observabilitySummaryService,
        AuditEventService auditEventService
    ) {
        this.ingestionModule = ingestionModule;
        this.observabilitySummaryService = observabilitySummaryService;
        this.auditEventService = auditEventService;
    }

    @PostMapping("/reindex")
    @Operation(summary = "Trigger reindex", description = "Triggers ingestion full sync/reindex.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Reindex accepted"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    public ResponseEntity<String> reindex() {
        try {
            ingestionModule.reindexAllFiles("admin");
            auditEventService.record(
                "ADMIN_REINDEX",
                "ingestion",
                null,
                202,
                Map.of("trigger", "admin")
            );
            return ResponseEntity.accepted().body("Reindex triggered");
        } catch (RuntimeException ex) {
            auditEventService.record(
                "ADMIN_REINDEX",
                "ingestion",
                null,
                500,
                Map.of("trigger", "admin", "error", ex.getClass().getSimpleName())
            );
            throw ex;
        }
    }

    @GetMapping("/observability/summary")
    @Operation(summary = "Get observability summary", description = "Returns aggregate observability KPIs.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary returned"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    public ResponseEntity<Map<String, Object>> observabilitySummary() {
        Map<String, Object> summary = observabilitySummaryService.buildSummary();
        auditEventService.record(
            "ADMIN_OBSERVABILITY_SUMMARY",
            "observability",
            null,
            200,
            Map.of("keys", summary.keySet())
        );
        return ResponseEntity.ok(summary);
    }
}
