package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Size;

/**
 * 文档上传请求参数。
 * <p>
 * 文件本身通过 {@code MultipartFile} 接收，不在此 DTO 中。
 *
 * @param processMode   处理模式，默认 chunk
 * @param chunkStrategy 切片策略，可选
 * @param chunkConfig   切片配置，JSON 字符串，可选
 * @param pipelineId    关联的处理流水线 ID，可选
 */
public record DocumentUploadRequest(

        @Size(max = 32, message = "处理模式不能超过 32 个字符")
        String processMode,

        @Size(max = 32, message = "切片策略不能超过 32 个字符")
        String chunkStrategy,

        @Size(max = 4096, message = "切片配置不能超过 4096 个字符")
        String chunkConfig,

        @Size(max = 32, message = "流水线 ID 不能超过 32 个字符")
        String pipelineId
) {
}
