package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 批量操作分块请求。
 *
 * @param chunkIds 待操作的分块 ID 列表
 * @param enabled  目标启用状态：1 启用，0 禁用
 */
public record KnowledgeChunkBatchRequest(
        @NotEmpty(message = "chunkIds 不能为空")
        List<String> chunkIds,

        @NotNull(message = "enabled 不能为空")
        Integer enabled
) {
}
