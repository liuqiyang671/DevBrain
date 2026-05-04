package edu.cqupt.devbrain.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.knowledge.controller.request.DocumentEnabledRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeDocumentPageRequest;
import edu.cqupt.devbrain.knowledge.controller.request.OnlineDocumentImportRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档控制器 -- 提供文档上传 REST 接口。
 * <p>
 * 外部完整路径由 `server.servlet.context-path=/api/devbrain` 拼接而来，
 * 因此前端访问路径为 `/api/devbrain/knowledge-base/{kbId}/docs/upload`。
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 分页查询全局文档列表。
     *
     * @param request 查询参数
     * @return 文档分页结果
     */
    @GetMapping("/knowledge-documents")
    public Result<IPage<DocumentVO>> page(@Valid KnowledgeDocumentPageRequest request) {
        return Results.success(knowledgeDocumentService.page(
                request.getPageNo(),
                request.getPageSize(),
                request.getKbId(),
                request.getKeyword(),
                request.getStatus(),
                request.getEnabled()
        ));
    }

    /**
     * 查询指定知识库下的文档列表。
     *
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    @GetMapping("/knowledge-base/{kbId}/docs")
    public Result<List<DocumentVO>> list(@PathVariable String kbId) {
        return Results.success(knowledgeDocumentService.listByKnowledgeBase(kbId));
    }

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
    @PostMapping(value = "/knowledge-base/{kbId}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentVO> upload(@PathVariable String kbId,
                                     @RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false, defaultValue = "chunk") String processMode,
                                     @RequestParam(required = false) String chunkStrategy,
                                     @RequestParam(required = false) String chunkConfig,
                                     @RequestParam(required = false) String pipelineId) {
        return Results.success(knowledgeDocumentService.upload(
                kbId, file, processMode, chunkStrategy, chunkConfig, pipelineId));
    }

    /**
     * 从在线来源导入文档到指定知识库。
     *
     * @param kbId    知识库 ID
     * @param request 在线文档来源和处理配置
     * @return 文档视图对象
     */
    @PostMapping(value = "/knowledge-base/{kbId}/docs/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<DocumentVO> importOnline(@PathVariable String kbId,
                                           @RequestBody @Valid OnlineDocumentImportRequest request) {
        return Results.success(knowledgeDocumentService.importOnline(kbId, request));
    }

    /**
     * 启用或禁用文档。
     *
     * @param kbId    知识库 ID
     * @param docId   文档 ID
     * @param request 启停请求
     * @return 更新后的文档
     */
    @PutMapping("/knowledge-base/{kbId}/docs/{docId}/enabled")
    public Result<DocumentVO> updateEnabled(@PathVariable String kbId,
                                            @PathVariable String docId,
                                            @RequestBody DocumentEnabledRequest request) {
        return Results.success(knowledgeDocumentService.updateEnabled(kbId, docId, request.enabled()));
    }

    /**
     * 逻辑删除文档。
     *
     * @param kbId  知识库 ID
     * @param docId 文档 ID
     * @return 空结果
     */
    @DeleteMapping("/knowledge-base/{kbId}/docs/{docId}")
    public Result<Void> delete(@PathVariable String kbId, @PathVariable String docId) {
        knowledgeDocumentService.delete(kbId, docId);
        return Results.success();
    }
}
