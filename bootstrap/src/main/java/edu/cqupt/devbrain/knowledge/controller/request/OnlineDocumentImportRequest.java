package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 在线文档导入请求，支持飞书文档和网页 URL。
 */
public record OnlineDocumentImportRequest(
        @NotBlank @Size(max = 32) String sourceType,
        @NotBlank @Size(max = 512) String sourceLocation,
        @Size(max = 128) String docName,
        String processMode,
        String chunkStrategy,
        String chunkConfig,
        String pipelineId,
        Integer scheduleEnabled,
        @Size(max = 128) String scheduleCron
) {
}
