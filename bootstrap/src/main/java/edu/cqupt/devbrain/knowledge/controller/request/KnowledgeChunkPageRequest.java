package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分块分页查询参数。
 */
@Data
public class KnowledgeChunkPageRequest {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    /** 每页条数，最大 100。 */
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 10;

    /** 启用状态过滤：0 禁用，1 启用。可选。 */
    private Boolean enabled;
}
