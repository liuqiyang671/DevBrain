package edu.cqupt.devbrain.infra.ai.gateway.structured;

import java.util.Map;

public record AiCallRecord(
        String callId,
        String runId,
        String stepId,
        String businessScene,
        String provider,
        String model,
        boolean stream,
        Double temperature,
        Integer maxTokens,
        Integer inputTokens,
        Integer outputTokens,
        long durationMs,
        String status,
        String errorMessage,
        String prompt,
        String response,
        Map<String, Object> metadata
) {

    public AiCallRecord {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
