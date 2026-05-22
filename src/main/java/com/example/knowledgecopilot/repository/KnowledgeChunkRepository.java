package com.example.knowledgecopilot.repository;

import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {
    @EntityGraph(attributePaths = "document")
    List<KnowledgeChunk> findByIdIn(List<UUID> ids);

    @EntityGraph(attributePaths = "document")
    @Query("""
        select kc
        from KnowledgeChunk kc
        where lower(kc.content) like lower(concat('%', :keyword, '%'))
           or lower(coalesce(kc.document.title, '')) like lower(concat('%', :keyword, '%'))
           or lower(kc.document.fileName) like lower(concat('%', :keyword, '%'))
           or lower(kc.document.sourcePath) like lower(concat('%', :keyword, '%'))
        order by kc.document.updatedAt desc, kc.document.indexedAt desc
        """)
    List<KnowledgeChunk> findKeywordCandidates(
        @Param("keyword") String keyword,
        Pageable pageable
    );

    List<KnowledgeChunk> findByDocument(KnowledgeDocument document);

    void deleteByDocument(KnowledgeDocument document);
}
