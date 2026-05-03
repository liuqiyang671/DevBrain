package edu.cqupt.devbrain.knowledge.controller;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档控制器 -- 提供文档上传 REST 接口。
 * <p>
 * 外部完整路径由 `server.servlet.context-path=/api/devbrain` 拼接而来，
 * 因此前端访问路径为 `/api/devbrain/knowledge-base/{kbId}/docs/upload`。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge-base/{kbId}/docs")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 上传文档到指定知识库。
     * <p>
     * Controller 只做参数接收和简单非空判断，文件校验、存储、入库等逻辑由 Service 处理。
     *
     * @param kbId         知识库 ID
     * @param file         上传的文件
     * @param processMode  处理模式，默认 chunk
     * @param chunkStrategy 切片策略，可选
     * @param chunkConfig  切片配置 JSON 字符串，可选
     * @param pipelineId   流水线 ID，可选
     * @return 文档视图对象
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentVO> upload(@PathVariable String kbId,
                                     @RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false, defaultValue = "chunk") String processMode,
                                     @RequestParam(required = false) String chunkStrategy,
                                     @RequestParam(required = false) String chunkConfig,
                                     @RequestParam(required = false) String pipelineId) {
        return Results.success(knowledgeDocumentService.upload(
                kbId, file, processMode, chunkStrategy, chunkConfig, pipelineId));
    }
}
