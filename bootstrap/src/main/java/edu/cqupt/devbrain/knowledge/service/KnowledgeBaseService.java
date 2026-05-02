package edu.cqupt.devbrain.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeBaseVO;

/**
 * 知识库服务接口 —— 定义知识库 CRUD 业务能力。
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库，并记录当前登录用户为创建人和更新人。
     *
     * @param request 创建请求
     * @return 创建后的知识库视图对象
     */
    KnowledgeBaseVO create(KnowledgeBaseCreateRequest request);

    /**
     * 分页查询知识库。
     *
     * @param pageNo   页码，最小按 1 处理
     * @param pageSize 每页大小，最大按 100 处理
     * @param keyword  关键字，匹配名称、描述和 collectionName
     * @param status   状态过滤，可为空
     * @return 知识库分页结果
     */
    IPage<KnowledgeBaseVO> page(long pageNo, long pageSize, String keyword, String status);

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库详情
     */
    KnowledgeBaseVO detail(String id);

    /**
     * 更新知识库基础信息。
     *
     * @param id      知识库 ID
     * @param request 更新请求
     * @return 更新后的知识库视图对象
     */
    KnowledgeBaseVO update(String id, KnowledgeBaseUpdateRequest request);

    /**
     * 逻辑删除知识库。
     *
     * @param id 知识库 ID
     */
    void delete(String id);
}
