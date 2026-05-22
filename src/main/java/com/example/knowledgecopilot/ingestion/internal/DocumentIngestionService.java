package com.example.knowledgecopilot.ingestion.internal;

import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.entity.KnowledgeDocument;
import com.example.knowledgecopilot.ingestion.api.NormalizedSourceItem;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.KnowledgeChunkRepository;
import com.example.knowledgecopilot.repository.KnowledgeDocumentRepository;
import com.example.knowledgecopilot.retrieval.api.EmbeddingPort;
import com.example.knowledgecopilot.retrieval.api.VectorRecord;
import com.example.knowledgecopilot.retrieval.api.VectorStorePort;
import com.example.knowledgecopilot.util.HashUtil;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentIngestionService {
    private final FileExtractionService fileExtractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStore;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final MeterRegistry meterRegistry;

    public DocumentIngestionService(
        FileExtractionService fileExtractionService,
        ChunkingService chunkingService,
        EmbeddingPort embeddingPort,
        VectorStorePort vectorStore,
        KnowledgeDocumentRepository documentRepository,
        KnowledgeChunkRepository chunkRepository,
        MeterRegistry meterRegistry
    ) {
        this.fileExtractionService = fileExtractionService;
        this.chunkingService = chunkingService;
        this.embeddingPort = embeddingPort;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void ingest(Path path) {
        Path absolutePath = path.toAbsolutePath();
        ingest(new NormalizedSourceItem(
            absolutePath.toString(),
            absolutePath,
            absolutePath.getFileName() == null ? absolutePath.toString() : absolutePath.getFileName().toString(),
            "FILE_SYSTEM",
            null,
            false,
            null,
            null
        ));
    }

    @Transactional
    public void ingest(NormalizedSourceItem sourceItem) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String status = "indexed";
        try {
            String sourcePath = sourceItem.externalId();
            String extractedText;
            String checksum;
            String contentType;
            Instant updatedAt = sourceItem.updatedAt();
            String title = sourceItem.title();
            String fileName = sourceItem.title();

            if (sourceItem.path() != null && (sourceItem.content() == null || sourceItem.content().isBlank())) {
                Path path = sourceItem.path().toAbsolutePath();
                checksum = HashUtil.sha256(path);
                extractedText = fileExtractionService.extractText(path);
                contentType = fileExtractionService.detectContentType(path);
                fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
                if (title == null || title.isBlank()) {
                    title = fileName;
                }
                if (updatedAt == null) {
                    try {
                        updatedAt = Files.getLastModifiedTime(path).toInstant();
                    } catch (Exception e) {
                        updatedAt = Instant.now();
                    }
                }
            } else {
                extractedText = sourceItem.content() == null ? "" : sourceItem.content().trim();
                checksum = HashUtil.sha256(sourcePath + "\n" + extractedText);
                contentType = (sourceItem.contentType() == null || sourceItem.contentType().isBlank())
                    ? "text/plain"
                    : sourceItem.contentType();
                if (title == null || title.isBlank()) {
                    title = sourcePath;
                }
                if (fileName == null || fileName.isBlank()) {
                    fileName = title;
                }
                if (updatedAt == null) {
                    updatedAt = Instant.now();
                }
            }

            Optional<KnowledgeDocument> existingOpt = documentRepository.findBySourcePath(sourcePath);
            if (existingOpt.isPresent() && existingOpt.get().getChecksum().equals(checksum)) {
                status = "unchanged";
                meterRegistry.counter(
                    MetricNames.INGESTION_DOCUMENTS_TOTAL,
                    "status", status,
                    "source_type", safeTag(sourceItem.sourceType())
                ).increment();
                return;
            }

            if (extractedText.isBlank()) {
                status = "empty_content";
                meterRegistry.counter(
                    MetricNames.INGESTION_DOCUMENTS_TOTAL,
                    "status", status,
                    "source_type", safeTag(sourceItem.sourceType())
                ).increment();
                return;
            }

            KnowledgeDocument document = existingOpt.orElseGet(KnowledgeDocument::new);
            if (document.getId() == null) {
                document.setId(UUID.randomUUID());
            }

            document.setSourceType(sourceItem.sourceType());
            document.setSourcePath(sourcePath);
            document.setFileName(fileName);
            document.setTitle(title);
            document.setContentType(contentType);
            document.setChecksum(checksum);
            document.setIndexedAt(Instant.now());
            document.setUpdatedAt(updatedAt);
            document.setMetadataJson("{}");

            document = documentRepository.save(document);
            List<KnowledgeChunk> existingChunks = chunkRepository.findByDocument(document);
            if (!existingChunks.isEmpty()) {
                vectorStore.deleteByIds(existingChunks.stream().map(chunk -> chunk.getId().toString()).toList());
                chunkRepository.deleteAll(existingChunks);
            }

            List<String> chunks = chunkingService.chunk(extractedText);
            DistributionSummary.builder(MetricNames.INGESTION_CHUNKS_TOTAL)
                .baseUnit("chunks")
                .tag("source_type", safeTag(sourceItem.sourceType()))
                .register(meterRegistry)
                .record(chunks.size());
            List<VectorRecord> vectorRecords = new java.util.ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] embedding = embeddingPort.embed(chunkText);
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setId(UUID.randomUUID());
                chunk.setDocument(document);
                chunk.setChunkIndex(i);
                chunk.setContent(chunkText);
                chunk.setEmbedding(embedding);
                chunk.setCitationLabel(document.getFileName() + "#chunk-" + i);
                chunk.setMetadataJson("{}");
                chunkRepository.save(chunk);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("documentId", document.getId().toString());
                metadata.put("sourcePath", document.getSourcePath());
                metadata.put("sourceType", document.getSourceType());
                metadata.put("chunkIndex", i);
                vectorRecords.add(new VectorRecord(
                    chunk.getId().toString(),
                    embedding,
                    chunkText,
                    metadata
                ));
            }
            vectorStore.upsertBatch(vectorRecords);
            meterRegistry.counter(
                MetricNames.INGESTION_DOCUMENTS_TOTAL,
                "status", status,
                "source_type", safeTag(sourceItem.sourceType())
            ).increment();
        } catch (RuntimeException ex) {
            status = "failed";
            meterRegistry.counter(
                MetricNames.INGESTION_DOCUMENTS_TOTAL,
                "status", status,
                "source_type", safeTag(sourceItem.sourceType())
            ).increment();
            throw ex;
        } finally {
            timerSample.stop(
                Timer.builder(MetricNames.INGESTION_DOCUMENT_LATENCY)
                    .tag("status", status)
                    .tag("source_type", safeTag(sourceItem.sourceType()))
                    .register(meterRegistry)
            );
        }
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
