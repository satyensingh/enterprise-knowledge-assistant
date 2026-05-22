package com.example.knowledgecopilot.retrieval.api;

import com.example.knowledgecopilot.dto.AskResponse;
import com.example.knowledgecopilot.dto.CitationDto;

import java.util.List;

public class AskExecutionTrace {
    private String question;
    private String answer;
    private List<CitationDto> citations;
    private List<RetrievedChunkTrace> retrievedChunks;
    private long latencyMs;
    private String modelUsed;
    private String promptVersion;
    private Boolean insufficientEvidence;

    public AskExecutionTrace() {}

    public AskExecutionTrace(
        String question,
        String answer,
        List<CitationDto> citations,
        List<RetrievedChunkTrace> retrievedChunks,
        long latencyMs,
        String modelUsed,
        String promptVersion
    ) {
        this(
            question,
            answer,
            citations,
            retrievedChunks,
            latencyMs,
            modelUsed,
            promptVersion,
            null
        );
    }

    public AskExecutionTrace(
        String question,
        String answer,
        List<CitationDto> citations,
        List<RetrievedChunkTrace> retrievedChunks,
        long latencyMs,
        String modelUsed,
        String promptVersion,
        Boolean insufficientEvidence
    ) {
        this.question = question;
        this.answer = answer;
        this.citations = citations;
        this.retrievedChunks = retrievedChunks;
        this.latencyMs = latencyMs;
        this.modelUsed = modelUsed;
        this.promptVersion = promptVersion;
        this.insufficientEvidence = insufficientEvidence;
    }

    public AskResponse toAskResponse() {
        return new AskResponse(null, answer, citations, insufficientEvidence);
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<CitationDto> getCitations() { return citations; }
    public void setCitations(List<CitationDto> citations) { this.citations = citations; }
    public List<RetrievedChunkTrace> getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(List<RetrievedChunkTrace> retrievedChunks) { this.retrievedChunks = retrievedChunks; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Boolean getInsufficientEvidence() { return insufficientEvidence; }
    public void setInsufficientEvidence(Boolean insufficientEvidence) { this.insufficientEvidence = insufficientEvidence; }
}
