package com.example.knowledgecopilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(description = "Citation metadata and excerpt for one supporting chunk.")
public class CitationDto {
    private String documentId;
    private String fileName;
    private String title;
    private String sourcePath;
    private String sourceType;
    private String contentType;
    private String checksum;
    private String indexedAt;
    private String updatedAt;
    private Integer chunkIndex;
    private String citationLabel;
    private String excerpt;

    public CitationDto() {}

    public CitationDto(
        String documentId,
        String fileName,
        String title,
        String sourcePath,
        String sourceType,
        String contentType,
        String checksum,
        String indexedAt,
        String updatedAt,
        Integer chunkIndex,
        String citationLabel,
        String excerpt
    ) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.title = title;
        this.sourcePath = sourcePath;
        this.sourceType = sourceType;
        this.contentType = contentType;
        this.checksum = checksum;
        this.indexedAt = indexedAt;
        this.updatedAt = updatedAt;
        this.chunkIndex = chunkIndex;
        this.citationLabel = citationLabel;
        this.excerpt = excerpt;
    }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getIndexedAt() { return indexedAt; }
    public void setIndexedAt(String indexedAt) { this.indexedAt = indexedAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getCitationLabel() { return citationLabel; }
    public void setCitationLabel(String citationLabel) { this.citationLabel = citationLabel; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CitationDto that)) return false;
        return Objects.equals(documentId, that.documentId) &&
            Objects.equals(fileName, that.fileName) &&
            Objects.equals(title, that.title) &&
            Objects.equals(sourcePath, that.sourcePath) &&
            Objects.equals(sourceType, that.sourceType) &&
            Objects.equals(contentType, that.contentType) &&
            Objects.equals(checksum, that.checksum) &&
            Objects.equals(indexedAt, that.indexedAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(chunkIndex, that.chunkIndex) &&
            Objects.equals(citationLabel, that.citationLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            documentId,
            fileName,
            title,
            sourcePath,
            sourceType,
            contentType,
            checksum,
            indexedAt,
            updatedAt,
            chunkIndex,
            citationLabel
        );
    }
}
