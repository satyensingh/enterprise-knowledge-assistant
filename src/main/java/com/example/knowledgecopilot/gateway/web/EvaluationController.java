package com.example.knowledgecopilot.gateway.web;

import com.example.knowledgecopilot.dto.CreateGoldenQuestionRequest;
import com.example.knowledgecopilot.dto.EvaluationFeedbackRequest;
import com.example.knowledgecopilot.dto.EvaluationRunDto;
import com.example.knowledgecopilot.dto.GoldenQuestionDto;
import com.example.knowledgecopilot.gateway.application.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/evaluations")
@Tag(name = "Evaluation", description = "Golden question and evaluation run APIs")
@SecurityRequirement(name = "bearerAuth")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/golden-questions")
    @Operation(summary = "Create golden question")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Golden question created"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    public ResponseEntity<GoldenQuestionDto> createGoldenQuestion(@Valid @RequestBody CreateGoldenQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.createGoldenQuestion(request));
    }

    @GetMapping("/golden-questions")
    @Operation(summary = "List golden questions")
    public ResponseEntity<List<GoldenQuestionDto>> listGoldenQuestions() {
        return ResponseEntity.ok(evaluationService.listGoldenQuestions());
    }

    @PostMapping("/runs/{goldenQuestionId}")
    @Operation(summary = "Run evaluation for a golden question")
    public ResponseEntity<EvaluationRunDto> runEvaluation(@PathVariable UUID goldenQuestionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.runEvaluation(goldenQuestionId));
    }

    @GetMapping("/runs")
    @Operation(summary = "List evaluation runs", description = "Optionally filter by goldenQuestionId")
    public ResponseEntity<List<EvaluationRunDto>> listRuns(@RequestParam(required = false) UUID goldenQuestionId) {
        return ResponseEntity.ok(evaluationService.listRuns(goldenQuestionId));
    }

    @PostMapping("/runs/{runId}/feedback")
    @Operation(summary = "Save evaluation feedback")
    public ResponseEntity<EvaluationRunDto> saveFeedback(
        @PathVariable UUID runId,
        @Valid @RequestBody EvaluationFeedbackRequest request
    ) {
        return ResponseEntity.ok(evaluationService.saveFeedback(runId, request));
    }
}
