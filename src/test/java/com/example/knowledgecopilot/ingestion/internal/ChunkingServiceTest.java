package com.example.knowledgecopilot.ingestion.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    @Test
    void chunksByTokensAndPreservesOverlap() {
        AppProperties properties = new AppProperties();
        properties.getChunking().setChunkSize(20);
        properties.getChunking().setOverlap(5);

        ChunkingService service = new ChunkingService(properties);
        String input = "The quick brown fox jumps over the lazy dog. ".repeat(20);

        List<String> chunks = service.chunk(input);

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(ENCODING.encode(chunk).size() <= 20);
        }

        IntArrayList first = ENCODING.encode(chunks.get(0));
        IntArrayList second = ENCODING.encode(chunks.get(1));

        assertTokensEqual(first, second, 5);
    }

    private void assertTokensEqual(IntArrayList left, IntArrayList right, int overlap) {
        assertTrue(left.size() >= overlap);
        assertTrue(right.size() >= overlap);

        for (int i = 0; i < overlap; i++) {
            assertEquals(left.get(left.size() - overlap + i), right.get(i));
        }
    }
}
