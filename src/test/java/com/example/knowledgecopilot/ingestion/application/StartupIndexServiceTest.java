package com.example.knowledgecopilot.ingestion.application;

import com.example.knowledgecopilot.entity.KnowledgeDocument;
import com.example.knowledgecopilot.ingestion.api.NormalizedSourceItem;
import com.example.knowledgecopilot.ingestion.api.SourceConnector;
import com.example.knowledgecopilot.ingestion.internal.DocumentIngestionService;
import com.example.knowledgecopilot.repository.KnowledgeChunkRepository;
import com.example.knowledgecopilot.repository.KnowledgeDocumentRepository;
import com.example.knowledgecopilot.retrieval.api.VectorStorePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StartupIndexServiceTest {

    @Test
    void prunesDocumentsWhoseSourceFilesAreMissing() {
        SourceConnector sourceConnector = mock(SourceConnector.class);
        DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        VectorStorePort vectorStore = mock(VectorStorePort.class);

        Path activeFile = Path.of("/knowledge-base/active.txt");
        KnowledgeDocument staleDocument = document("/knowledge-base/stale.txt");
        KnowledgeDocument activeDocument = document(activeFile.toAbsolutePath().toString());

        when(sourceConnector.connectorType()).thenReturn("LOCAL_FILE_SYSTEM");
        when(sourceConnector.testConnection()).thenReturn(true);
        when(sourceConnector.fullSync()).thenReturn(List.of(sourceItem(activeFile)));
        when(documentRepository.findAll()).thenReturn(List.of(staleDocument, activeDocument));
        when(chunkRepository.findByDocument(any())).thenReturn(List.of());

        IngestionModuleService service = new IngestionModuleService(
            List.of(sourceConnector),
            documentIngestionService,
            documentRepository,
            chunkRepository,
            vectorStore,
            new SimpleMeterRegistry()
        );

        service.reindexAllFiles("scheduled");

        ArgumentCaptor<NormalizedSourceItem> itemCaptor = ArgumentCaptor.forClass(NormalizedSourceItem.class);
        verify(documentIngestionService).ingest(itemCaptor.capture());
        assertEquals(activeFile.toAbsolutePath().toString(), itemCaptor.getValue().externalId());
        verify(documentRepository).deleteAll(List.of(staleDocument));
        verify(documentRepository, never()).deleteAll(List.of(activeDocument));
    }

    @Test
    void skipsReindexAndPruningWhenRootFolderIsMissing() {
        SourceConnector sourceConnector = mock(SourceConnector.class);
        DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        VectorStorePort vectorStore = mock(VectorStorePort.class);

        when(sourceConnector.connectorType()).thenReturn("LOCAL_FILE_SYSTEM");
        when(sourceConnector.testConnection()).thenReturn(false);

        IngestionModuleService service = new IngestionModuleService(
            List.of(sourceConnector),
            documentIngestionService,
            documentRepository,
            chunkRepository,
            vectorStore,
            new SimpleMeterRegistry()
        );

        service.reindexAllFiles("scheduled");

        verify(sourceConnector, never()).fullSync();
        verifyNoInteractions(documentIngestionService);
        verifyNoInteractions(documentRepository);
    }

    private NormalizedSourceItem sourceItem(Path path) {
        return new NormalizedSourceItem(
            path.toAbsolutePath().toString(),
            path,
            path.getFileName().toString(),
            "LOCAL_FILE_SYSTEM",
            Instant.parse("2026-05-14T00:00:00Z"),
            false,
            null,
            null
        );
    }

    private KnowledgeDocument document(String sourcePath) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID());
        document.setSourceType("FILE_SYSTEM");
        document.setSourcePath(sourcePath);
        document.setFileName(Path.of(sourcePath).getFileName().toString());
        document.setTitle(document.getFileName());
        document.setContentType("text/plain");
        document.setChecksum("checksum");
        document.setUpdatedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setIndexedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setMetadataJson("{}");
        return document;
    }
}
