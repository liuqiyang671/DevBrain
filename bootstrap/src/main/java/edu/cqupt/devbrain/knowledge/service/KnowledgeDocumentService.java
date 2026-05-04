package edu.cqupt.devbrain.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.knowledge.controller.request.OnlineDocumentImportRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务接口 -- 定义文档上传和管理业务能力。
 */
public interface KnowledgeDocumentService {

    /**
     * 查询指定知识库下的未删除文档列表。
     *
     * @param kbId 知识库 ID
     * @return 文档列表，按更新时间倒序
     */
    List<DocumentVO> listByKnowledgeBase(String kbId);

    /**
     * 分页查询全局文档列表。
     *
     * @param pageNo   页码，最小按 1 处理
     * @param pageSize 每页大小，最大按 100 处理
     * @param kbId     知识库过滤，可为空
     * @param keyword  文档名称关键字，可为空
     * @param status   处理状态，可为空
     * @param enabled  启用状态，可为空
     * @return 文档分页结果
     */
    IPage<DocumentVO> page(long pageNo, long pageSize, String kbId, String keyword, String status, Integer enabled);

    /**
     * 上传文档到指定知识库。
     * <p>
     * 负责文件存储（S3/MinIO）、数据库记录插入、文档状态初始化、
     * 文件类型校验、切片配置解析等完整业务流程。
     *
     * @param kbId         知识库 ID
     * @param file         上传的文件
     * @param processMode  处理模式，默认 chunk
     * @param chunkStrategy 切片策略，可为空
     * @param chunkConfig  切片配置 JSON 字符串，可为空
     * @param pipelineId   流水线 ID，可为空
     * @return 文档视图对象
     */
    DocumentVO upload(String kbId, MultipartFile file, String processMode,
                      String chunkStrategy, String chunkConfig, String pipelineId);

    /**
     * 从在线来源导入文档到指定知识库。
     *
     * @param kbId    知识库 ID
     * @param request 在线来源和处理配置
     * @return 文档视图对象
     */
    DocumentVO importOnline(String kbId, OnlineDocumentImportRequest request);

    /**
     * 更新文档启用状态。
     *
     * @param kbId    知识库 ID
     * @param docId   文档 ID
     * @param enabled 启用状态：0 禁用，1 启用
     * @return 更新后的文档视图对象
     */
    DocumentVO updateEnabled(String kbId, String docId, Integer enabled);

    /**
     * 逻辑删除文档，并尽量清理对象存储文件。
     *
     * @param kbId  知识库 ID
     * @param docId 文档 ID
     */
    void delete(String kbId, String docId);
}
