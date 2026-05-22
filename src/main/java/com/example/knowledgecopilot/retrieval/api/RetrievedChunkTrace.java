package com.example.knowledgecopilot.retrieval.api;

public class RetrievedChunkTrace {
    private String chunkId;
    private String documentId;
    private String sourcePath;
    private String fileName;
    private Integer chunkIndex;
    private String citationLabel;
    private String excerpt;
    private boolean authorized;

    public RetrievedChunkTrace() {}

    public RetrievedChunkTrace(
        String chunkId,
        String documentId,
        String sourcePath,
        String fileName,
        Integer chunkIndex,
        String citationLabel,
        String excerpt,
        boolean authorized
    ) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.sourcePath = sourcePath;
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.citationLabel = citationLabel;
        this.excerpt = excerpt;
        this.authorized = authorized;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getCitationLabel() { return citationLabel; }
    public void setCitationLabel(String citationLabel) { this.citationLabel = citationLabel; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public boolean isAuthorized() { return authorized; }
    public void setAuthorized(boolean authorized) { this.authorized = authorized; }
}
