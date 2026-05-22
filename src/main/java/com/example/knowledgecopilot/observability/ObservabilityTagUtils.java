package com.example.knowledgecopilot.observability;

import java.util.regex.Pattern;

public final class ObservabilityTagUtils {
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    );
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("\\b\\d{4,}\\b");

    private ObservabilityTagUtils() {}

    public static String normalizePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "unknown";
        }
        String uuidSanitized = UUID_PATTERN.matcher(requestPath).replaceAll("{id}");
        return LONG_NUMBER_PATTERN.matcher(uuidSanitized).replaceAll("{num}");
    }

    public static String trimTagValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
