package edu.cqupt.devbrain.knowledge.service;

import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档服务接口 -- 定义文档上传和管理业务能力。
 */
public interface KnowledgeDocumentService {

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
}
