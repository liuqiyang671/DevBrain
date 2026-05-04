package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Size;

/**
 * 手动触发文档分块时可覆盖的分块配置。
 *
 * @param chunkStrategy 分块策略，可为空
 * @param chunkConfig   分块配置 JSON 字符串，可为空
 */
public record DocumentParseRequest(

        @Size(max = 32, message = "切片策略不能超过 32 个字符")
        String chunkStrategy,

        @Size(max = 4096, message = "切片配置不能超过 4096 个字符")
        String chunkConfig
) {
}
