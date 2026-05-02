package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库请求。
 *
 * @param name           知识库名称，面向用户展示
 * @param embeddingModel Embedding 模型标识，后续文档向量化使用
 * @param collectionName 向量集合名称，创建后禁止修改
 * @param description    知识库描述，可为空
 * @param status         状态，未传时默认为 enabled
 */
public record KnowledgeBaseCreateRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过 128 个字符")
        String name,

        @NotBlank(message = "Embedding 模型不能为空")
        @Size(max = 64, message = "Embedding 模型不能超过 64 个字符")
        String embeddingModel,

        @NotBlank(message = "collectionName 不能为空")
        @Size(max = 64, message = "collectionName 不能超过 64 个字符")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "collectionName 必须以字母开头，且只能包含字母、数字、下划线和中划线")
        String collectionName,

        @Size(max = 512, message = "描述不能超过 512 个字符")
        String description,

        @Pattern(regexp = "enabled|disabled", message = "状态只能为 enabled 或 disabled")
        String status
) {
}
