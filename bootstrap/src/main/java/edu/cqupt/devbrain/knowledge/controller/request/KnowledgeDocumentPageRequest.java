package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 文档分页查询参数。
 */
@Data
public class KnowledgeDocumentPageRequest {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    /** 每页条数，最大 100，防止一次查询过多数据。 */
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 10;

    /** 知识库 ID 过滤，可为空。 */
    private String kbId;

    /** 搜索关键字，匹配文档名称。 */
    private String keyword;

    /** 处理状态过滤：pending / processing / completed / failed。 */
    private String status;

    /** 启用状态过滤：0 禁用，1 启用。 */
    private Integer enabled;
}
