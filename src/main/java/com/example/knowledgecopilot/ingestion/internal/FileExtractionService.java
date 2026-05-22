package com.example.knowledgecopilot.ingestion.internal;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class FileExtractionService {
    private final Tika tika = new Tika();

    public String extractText(Path path) {
        try {
            String text = tika.parseToString(path);
            return text == null ? "" : text.trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text from file: " + path, e);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected extraction error for file: " + path, e);
        }
    }

    public String detectContentType(Path path) {
        try {
            return tika.detect(path);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
