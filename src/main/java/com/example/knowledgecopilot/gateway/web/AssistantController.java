package com.example.knowledgecopilot.gateway.web;

import com.example.knowledgecopilot.dto.AnswerFeedbackRequest;
import com.example.knowledgecopilot.dto.AnswerFeedbackResponse;
import com.example.knowledgecopilot.dto.AskRequest;
import com.example.knowledgecopilot.dto.AskResponse;
import com.example.knowledgecopilot.gateway.application.AnswerFeedbackService;
import com.example.knowledgecopilot.gateway.application.GatewayQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Assistant", description = "User-facing assistant APIs")
public class AssistantController {
    private final GatewayQueryService gatewayQueryService;
    private final AnswerFeedbackService answerFeedbackService;

    public AssistantController(
        GatewayQueryService gatewayQueryService,
        AnswerFeedbackService answerFeedbackService
    ) {
        this.gatewayQueryService = gatewayQueryService;
        this.answerFeedbackService = answerFeedbackService;
    }

    @PostMapping("/ask")
    @Operation(
        summary = "Ask question",
        description = "Runs authorization-aware RAG and returns answer with citations.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Answer generated"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content)
    })
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(gatewayQueryService.ask(request));
    }

    @PostMapping("/feedback")
    @Operation(
        summary = "Submit answer feedback",
        description = "Stores GOOD/BAD feedback for a response and returns aggregate counters.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Feedback stored"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content)
    })
    public ResponseEntity<AnswerFeedbackResponse> submitFeedback(@Valid @RequestBody AnswerFeedbackRequest request) {
        return ResponseEntity.ok(answerFeedbackService.submitFeedback(request));
    }

    @GetMapping("/health")
    @Operation(
        summary = "Assistant health check",
        description = "Public health probe for assistant endpoint.",
        security = {}
    )
    @ApiResponse(
        responseCode = "200",
        description = "Service is healthy",
        content = @Content(schema = @Schema(implementation = String.class))
    )
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
