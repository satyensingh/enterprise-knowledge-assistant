package com.example.knowledgecopilot.ingestion.internal;

import com.example.knowledgecopilot.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FileScannerService {
    private final AppProperties properties;

    public FileScannerService(AppProperties properties) {
        this.properties = properties;
    }

    public List<Path> scanAllFiles() {
        Path root = rootFolder();
        List<String> extensions = properties.getIngestion().getSupportedExtensions();

        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> hasSupportedExtension(path, extensions))
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan files from root folder: " + root, e);
        }
    }

    public boolean rootExists() {
        return Files.exists(rootFolder());
    }

    public Path rootFolder() {
        return Path.of(properties.getIngestion().getRootFolder());
    }

    private boolean hasSupportedExtension(Path path, List<String> extensions) {
        String fileName = path.getFileName().toString().toLowerCase();
        return extensions.stream().anyMatch(ext -> fileName.endsWith("." + ext.toLowerCase()));
    }
}
