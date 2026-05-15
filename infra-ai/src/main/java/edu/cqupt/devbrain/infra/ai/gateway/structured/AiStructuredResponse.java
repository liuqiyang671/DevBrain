package edu.cqupt.devbrain.infra.ai.gateway.structured;

import lombok.Builder;

import java.util.List;

@Builder
public record AiStructuredResponse<T>(
        T value,
        String rawContent,
        String provider,
        String model,
        long durationMs,
        String callId,
        List<String> parseWarnings
) {

    public AiStructuredResponse {
        parseWarnings = parseWarnings == null ? List.of() : List.copyOf(parseWarnings);
    }
}
