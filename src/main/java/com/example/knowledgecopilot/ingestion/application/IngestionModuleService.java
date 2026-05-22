package com.example.knowledgecopilot.ingestion.application;

import com.example.knowledgecopilot.entity.KnowledgeDocument;
import com.example.knowledgecopilot.ingestion.api.IngestionModule;
import com.example.knowledgecopilot.ingestion.api.NormalizedSourceItem;
import com.example.knowledgecopilot.ingestion.api.SourceConnector;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.ingestion.internal.DocumentIngestionService;
import com.example.knowledgecopilot.repository.KnowledgeChunkRepository;
import com.example.knowledgecopilot.repository.KnowledgeDocumentRepository;
import com.example.knowledgecopilot.retrieval.api.VectorStorePort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IngestionModuleService implements IngestionModule {
    private static final Logger log = LoggerFactory.getLogger(IngestionModuleService.class);

    private final List<SourceConnector> sourceConnectors;
    private final DocumentIngestionService documentIngestionService;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStorePort vectorStore;
    private final MeterRegistry meterRegistry;

    public IngestionModuleService(
        List<SourceConnector> sourceConnectors,
        DocumentIngestionService documentIngestionService,
        KnowledgeDocumentRepository documentRepository,
        KnowledgeChunkRepository chunkRepository,
        VectorStorePort vectorStore,
        MeterRegistry meterRegistry
    ) {
        this.sourceConnectors = sourceConnectors;
        this.documentIngestionService = documentIngestionService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void reindexAllFiles(String trigger) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String status = "success";
        if (sourceConnectors.isEmpty()) {
            log.warn("Reindex trigger={} skipped because no connectors are configured", trigger);
            meterRegistry.counter(
                MetricNames.INGESTION_REINDEX_TOTAL,
                "trigger", safeTag(trigger),
                "status", "skipped_no_connectors"
            ).increment();
            return;
        }

        List<String> activeSourcePaths = new ArrayList<>();
        boolean atLeastOneConnectorProcessed = false;
        try {
            for (SourceConnector connector : sourceConnectors) {
                Timer.Sample connectorSample = Timer.start(meterRegistry);
                String connectorStatus = "success";
                try {
                    if (!connector.testConnection()) {
                        connectorStatus = "unavailable";
                        log.warn(
                            "Reindex trigger={} skipped connector={} because testConnection failed",
                            trigger,
                            connector.connectorType()
                        );
                        meterRegistry.counter(
                            MetricNames.INGESTION_REINDEX_TOTAL,
                            "trigger", safeTag(trigger),
                            "status", "connector_unavailable",
                            "connector", safeTag(connector.connectorType())
                        ).increment();
                        continue;
                    }

                    atLeastOneConnectorProcessed = true;
                    List<NormalizedSourceItem> items = connector.fullSync();
                    DistributionSummary.builder(MetricNames.INGESTION_CONNECTOR_ITEMS)
                        .baseUnit("items")
                        .tag("trigger", safeTag(trigger))
                        .tag("connector", safeTag(connector.connectorType()))
                        .register(meterRegistry)
                        .record(items.size());

                    log.info(
                        "Reindex trigger={} connector={} found {} items",
                        trigger,
                        connector.connectorType(),
                        items.size()
                    );

                    for (NormalizedSourceItem item : items) {
                        if (item.deleted()) {
                            connector.markDeleted(item.externalId());
                            documentRepository.findBySourcePath(item.externalId())
                                .ifPresent(documentRepository::delete);
                            meterRegistry.counter(
                                MetricNames.INGESTION_CONNECTOR_DOCUMENTS_TOTAL,
                                "trigger", safeTag(trigger),
                                "status", "deleted",
                                "source_type", safeTag(item.sourceType())
                            ).increment();
                            continue;
                        }

                        try {
                            documentIngestionService.ingest(item);
                            activeSourcePaths.add(item.externalId());
                            meterRegistry.counter(
                                MetricNames.INGESTION_CONNECTOR_DOCUMENTS_TOTAL,
                                "trigger", safeTag(trigger),
                                "status", "indexed",
                                "source_type", safeTag(item.sourceType())
                            ).increment();
                            log.info("Indexed item: {}", item.externalId());
                        } catch (Exception e) {
                            meterRegistry.counter(
                                MetricNames.INGESTION_CONNECTOR_DOCUMENTS_TOTAL,
                                "trigger", safeTag(trigger),
                                "status", "failed",
                                "source_type", safeTag(item.sourceType())
                            ).increment();
                            log.error("Failed to index item: {}", item.externalId(), e);
                        }
                    }
                } catch (RuntimeException ex) {
                    connectorStatus = "failure";
                    throw ex;
                } finally {
                    connectorSample.stop(
                        Timer.builder(MetricNames.INGESTION_CONNECTOR_SYNC_LATENCY)
                            .tag("trigger", safeTag(trigger))
                            .tag("connector", safeTag(connector.connectorType()))
                            .tag("status", connectorStatus)
                            .register(meterRegistry)
                    );
                }
            }

            if (!atLeastOneConnectorProcessed) {
                status = "skipped_all_connectors_unavailable";
                return;
            }

            pruneDeletedFiles(activeSourcePaths, trigger);
        } catch (RuntimeException ex) {
            status = "failure";
            throw ex;
        } finally {
            meterRegistry.counter(
                MetricNames.INGESTION_REINDEX_TOTAL,
                "trigger", safeTag(trigger),
                "status", status
            ).increment();
            timerSample.stop(
                Timer.builder(MetricNames.INGESTION_REINDEX_LATENCY)
                    .tag("trigger", safeTag(trigger))
                    .tag("status", status)
                    .register(meterRegistry)
            );
        }
    }

    private void pruneDeletedFiles(List<String> sourcePaths, String trigger) {
        Set<String> activeSourcePaths = sourcePaths.stream().collect(Collectors.toSet());

        List<KnowledgeDocument> staleDocuments = documentRepository.findAll().stream()
            .filter(document -> !activeSourcePaths.contains(document.getSourcePath()))
            .toList();

        if (staleDocuments.isEmpty()) {
            return;
        }

        log.info("Reindex trigger={} pruning {} stale documents", trigger, staleDocuments.size());
        List<String> staleChunkIds = staleDocuments.stream()
            .flatMap(document -> chunkRepository.findByDocument(document).stream())
            .map(chunk -> chunk.getId().toString())
            .toList();
        vectorStore.deleteByIds(staleChunkIds);
        documentRepository.deleteAll(staleDocuments);
        meterRegistry.counter(
            MetricNames.INGESTION_PRUNED_DOCUMENTS_TOTAL,
            "trigger", safeTag(trigger)
        ).increment(staleDocuments.size());
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
