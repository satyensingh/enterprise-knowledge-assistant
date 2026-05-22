package com.example.knowledgecopilot.ingestion.application;

import com.example.knowledgecopilot.ingestion.api.NormalizedSourceItem;
import com.example.knowledgecopilot.ingestion.api.SourceConnector;
import com.example.knowledgecopilot.ingestion.internal.FileScannerService;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LocalFileSourceConnector implements SourceConnector {
    private final FileScannerService fileScannerService;

    public LocalFileSourceConnector(FileScannerService fileScannerService) {
        this.fileScannerService = fileScannerService;
    }

    @Override
    public String connectorType() {
        return "LOCAL_FILE_SYSTEM";
    }

    @Override
    public boolean testConnection() {
        return fileScannerService.rootExists();
    }

    @Override
    public List<NormalizedSourceItem> fullSync() {
        return fileScannerService.scanAllFiles().stream()
            .map(this::toSourceItem)
            .toList();
    }

    @Override
    public List<NormalizedSourceItem> incrementalSync(Instant since) {
        return fullSync().stream()
            .filter(item -> !item.updatedAt().isBefore(since))
            .toList();
    }

    @Override
    public Optional<NormalizedSourceItem> fetchItem(String externalId) {
        Path path = Path.of(externalId);
        if (!path.toFile().exists()) {
            return Optional.empty();
        }
        return Optional.of(toSourceItem(path));
    }

    @Override
    public Set<String> fetchPermissions(String externalId) {
        return Set.of("public");
    }

    @Override
    public void markDeleted(String externalId) {
    }

    private NormalizedSourceItem toSourceItem(Path path) {
        Path absolutePath = path.toAbsolutePath();
        Instant updatedAt;
        try {
            FileTime lastModified = java.nio.file.Files.getLastModifiedTime(absolutePath);
            updatedAt = lastModified.toInstant();
        } catch (Exception e) {
            updatedAt = Instant.now();
        }

        String title = absolutePath.getFileName() == null
            ? absolutePath.toString()
            : absolutePath.getFileName().toString();

        return new NormalizedSourceItem(
            absolutePath.toString(),
            absolutePath,
            title,
            connectorType(),
            updatedAt,
            false,
            null,
            null
        );
    }
}
