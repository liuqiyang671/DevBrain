package edu.cqupt.devbrain.infra.ai.gateway.structured;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStructuredRequest<T> {

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    private Class<T> responseType;

    @Builder.Default
    private Map<String, Object> schema = Map.of();

    private Double temperature;

    private Integer maxTokens;

    private Long timeoutMillis;

    private String businessScene;

    private String runId;

    private String stepId;

    @Builder.Default
    private boolean fallbackAllowed = true;

    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}
