package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新分块内容请求。
 *
 * @param content 新的分块文本内容
 */
public record KnowledgeChunkUpdateRequest(
        @NotBlank(message = "content 不能为空")
        String content
) {
}
