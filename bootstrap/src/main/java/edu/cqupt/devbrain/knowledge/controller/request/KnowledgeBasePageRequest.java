package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 知识库分页查询参数。
 */
@Data
public class KnowledgeBasePageRequest {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    /** 每页条数，最大 100，防止一次查询过多数据。 */
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 10;

    /** 搜索关键字，匹配知识库名称、描述和 collectionName。 */
    private String keyword;

    /** 状态过滤：enabled / disabled。 */
    private String status;
}
