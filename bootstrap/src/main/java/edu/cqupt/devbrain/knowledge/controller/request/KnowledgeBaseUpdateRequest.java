package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新知识库请求。collectionName 创建后禁止修改，若传入则由 Service 明确拒绝。
 *
 * @param name           新知识库名称
 * @param collectionName 禁止修改字段，仅用于识别非法请求
 * @param embeddingModel 新 Embedding 模型标识
 * @param description    新描述
 * @param status         新状态：enabled / disabled
 */
public record KnowledgeBaseUpdateRequest(
        @Size(max = 128, message = "知识库名称不能超过 128 个字符")
        String name,

        String collectionName,

        @Size(max = 64, message = "Embedding 模型不能超过 64 个字符")
        String embeddingModel,

        @Size(max = 512, message = "描述不能超过 512 个字符")
        String description,

        @Pattern(regexp = "enabled|disabled", message = "状态只能为 enabled 或 disabled")
        String status
) {
}
