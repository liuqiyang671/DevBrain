package edu.cqupt.devbrain.knowledge.service;

import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkBatchRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkPageRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeChunkVO;
import edu.cqupt.devbrain.knowledge.controller.vo.PageResult;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;

import java.util.List;

/**
 * 知识库分块服务接口，管理分块的 CRUD 及向量库同步。
 */
public interface KnowledgeChunkService {

    /**
     * 分页查询某文档的 chunk。
     *
     * @param docId   文档 ID
     * @param request 分页参数
     * @return 分页结果
     */
    PageResult<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest request);

    /**
     * 查询某文档的所有 chunk。
     *
     * @param docId 文档 ID
     * @return chunk 列表
     */
    List<KnowledgeChunkDO> listByDocId(String docId);

    /**
     * 新增单条 chunk，同步写入向量库。
     *
     * @param docId   文档 ID
     * @param request 创建请求
     * @return 创建后的 chunk 视图
     */
    KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest request);

    /**
     * 批量创建 chunk。
     *
     * @param chunks        chunk 实体列表
     * @param syncToVector  是否同步写入向量库
     * @return 创建后的 chunk 实体列表
     */
    List<KnowledgeChunkDO> batchCreate(List<KnowledgeChunkDO> chunks, boolean syncToVector);

    /**
     * 更新 chunk 内容，同步更新向量库。
     *
     * @param docId   文档 ID
     * @param chunkId chunk ID
     * @param request 更新请求
     * @return 更新后的 chunk 视图
     */
    KnowledgeChunkVO update(String docId, String chunkId, KnowledgeChunkUpdateRequest request);

    /**
     * 删除单条 chunk，同步删除向量。
     *
     * @param docId   文档 ID
     * @param chunkId chunk ID
     */
    void delete(String docId, String chunkId);

    /**
     * 删除某文档的所有 chunk，同步删除向量。
     *
     * @param docId 文档 ID
     */
    void deleteByDocId(String docId);

    /**
     * 启用/禁用单条 chunk。
     *
     * @param chunkId chunk ID
     * @param enabled true 启用，false 禁用
     */
    void enableChunk(String chunkId, boolean enabled);

    /**
     * 批量启用/禁用 chunk。
     *
     * @param chunkIds chunk ID 列表
     * @param enabled  true 启用，false 禁用
     */
    void batchToggleEnabled(List<String> chunkIds, boolean enabled);

    /**
     * 按文档批量启用/禁用。
     *
     * @param docId   文档 ID
     * @param enabled true 启用，false 禁用
     */
    void updateEnabledByDocId(String docId, boolean enabled);
}
