package com.example.knowledgecopilot.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class EmbeddingJsonCodec {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EmbeddingJsonCodec() {
    }

    public static String toJson(float[] embedding) {
        if (embedding == null) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(embedding);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize embedding vector", e);
        }
    }

    public static float[] fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new float[0];
        }
        try {
            return OBJECT_MAPPER.readValue(json, float[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize embedding vector", e);
        }
    }
}
