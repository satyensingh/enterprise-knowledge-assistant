package com.example.knowledgecopilot.ingestion.application;

import com.example.knowledgecopilot.ingestion.api.IngestionModule;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReindexScheduler {
    private final IngestionModule ingestionModule;

    public ReindexScheduler(IngestionModule ingestionModule) {
        this.ingestionModule = ingestionModule;
    }

    @PostConstruct
    public void indexOnStartup() {
        ingestionModule.reindexAllFiles("startup");
    }

    @Scheduled(
        initialDelayString = "${app.reindex.initial-delay-ms:60000}",
        fixedDelayString = "${app.reindex.fixed-delay-ms:900000}"
    )
    public void scheduledReindex() {
        ingestionModule.reindexAllFiles("scheduled");
    }
}
