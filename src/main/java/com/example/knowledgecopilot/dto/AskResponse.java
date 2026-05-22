package com.example.knowledgecopilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Assistant answer payload.")
public class AskResponse {
    @Schema(description = "Stable ID for this answer, used for feedback.")
    private String responseId;
    @Schema(description = "Generated answer text.")
    private String answer;
    @Schema(description = "Citations backing the answer.")
    private List<CitationDto> citations;
    @Schema(description = "True when the system could not find sufficient evidence.")
    private Boolean insufficientEvidence;

    public AskResponse() {}

    public AskResponse(String responseId, String answer, List<CitationDto> citations) {
        this(responseId, answer, citations, null);
    }

    public AskResponse(String responseId, String answer, List<CitationDto> citations, Boolean insufficientEvidence) {
        this.responseId = responseId;
        this.answer = answer;
        this.citations = citations;
        this.insufficientEvidence = insufficientEvidence;
    }

    public String getResponseId() { return responseId; }
    public void setResponseId(String responseId) { this.responseId = responseId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<CitationDto> getCitations() { return citations; }
    public void setCitations(List<CitationDto> citations) { this.citations = citations; }
    public Boolean getInsufficientEvidence() { return insufficientEvidence; }
    public void setInsufficientEvidence(Boolean insufficientEvidence) { this.insufficientEvidence = insufficientEvidence; }
}
