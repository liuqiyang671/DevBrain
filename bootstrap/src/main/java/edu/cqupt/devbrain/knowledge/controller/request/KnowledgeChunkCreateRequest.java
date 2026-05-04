package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增单条分块请求。
 *
 * @param content 分块文本内容
 * @param index   块在文档中的序号，可选
 * @param chunkId 指定分块 ID，可选（不传则自动生成）
 */
public record KnowledgeChunkCreateRequest(
        @NotBlank(message = "content 不能为空")
        String content,

        Integer index,

        String chunkId
) {
}
