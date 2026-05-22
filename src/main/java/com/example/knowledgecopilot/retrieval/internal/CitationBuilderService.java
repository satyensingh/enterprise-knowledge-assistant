package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.dto.CitationDto;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.entity.KnowledgeDocument;
import org.springframework.stereotype.Service;

@Service
public class CitationBuilderService {
    public CitationDto buildCitation(KnowledgeChunk chunk) {
        KnowledgeDocument document = chunk.getDocument();
        return new CitationDto(
            document.getId() == null ? null : document.getId().toString(),
            document.getFileName(),
            document.getTitle(),
            document.getSourcePath(),
            document.getSourceType(),
            document.getContentType(),
            document.getChecksum(),
            document.getIndexedAt() == null ? null : document.getIndexedAt().toString(),
            document.getUpdatedAt() == null ? null : document.getUpdatedAt().toString(),
            chunk.getChunkIndex(),
            chunk.getCitationLabel(),
            excerpt(chunk.getContent())
        );
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 240) + "...";
    }
}
