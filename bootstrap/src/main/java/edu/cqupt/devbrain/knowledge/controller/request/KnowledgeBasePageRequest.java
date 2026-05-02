package edu.cqupt.devbrain.knowledge.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 知识库分页查询参数。
 */
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

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
