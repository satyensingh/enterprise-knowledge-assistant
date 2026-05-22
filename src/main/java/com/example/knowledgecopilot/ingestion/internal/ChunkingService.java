package com.example.knowledgecopilot.ingestion.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {
    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding DEFAULT_ENCODING = ENCODING_REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private final AppProperties properties;

    public ChunkingService(AppProperties properties) {
        this.properties = properties;
    }

    public List<String> chunk(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        int chunkSize = properties.getChunking().getChunkSize();
        int overlap = properties.getChunking().getOverlap();
        int step = Math.max(1, chunkSize - overlap);
        IntArrayList tokens = DEFAULT_ENCODING.encode(input);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < tokens.size()) {
            int end = Math.min(tokens.size(), start + chunkSize);
            IntArrayList chunkTokens = new IntArrayList(end - start);
            for (int i = start; i < end; i++) {
                chunkTokens.add(tokens.get(i));
            }
            String chunk = DEFAULT_ENCODING.decode(chunkTokens);
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == tokens.size()) {
                break;
            }
            start += step;
        }

        return chunks;
    }
}
