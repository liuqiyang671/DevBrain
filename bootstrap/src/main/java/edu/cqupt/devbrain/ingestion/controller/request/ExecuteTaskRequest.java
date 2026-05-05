package edu.cqupt.devbrain.ingestion.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 执行摄入任务请求。
 *
 * @param pipelineId     流水线 ID
 * @param sourceType     来源类型：FILE / URL / FEISHU / S3
 * @param sourceLocation 来源地址、对象存储 key 或第三方文档标识
 * @param fileName       原始文件名
 * @param metadata       任务元数据
 */
public record ExecuteTaskRequest(
        @NotBlank(message = "pipelineId 不能为空")
        @Size(max = 20, message = "pipelineId 不能超过 20 个字符")
        String pipelineId,

        @NotBlank(message = "sourceType 不能为空")
        @Size(max = 20, message = "sourceType 不能超过 20 个字符")
        String sourceType,

        @NotBlank(message = "sourceLocation 不能为空")
        String sourceLocation,

        String fileName,

        Map<String, Object> metadata
) {
}
