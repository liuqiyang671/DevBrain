package edu.cqupt.devbrain.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBasePageRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeBaseVO;
import edu.cqupt.devbrain.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库控制器 —— 提供知识库 CRUD REST 接口。
 * <p>
 * 外部完整路径由 `server.servlet.context-path=/api/devbrain` 拼接而来，
 * 因此前端访问路径为 `/api/devbrain/knowledge-base`。
 */
@RestController
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 创建知识库。
     *
     * @param request 创建请求，包含名称、Embedding 模型和 collectionName
     * @return 创建后的知识库视图对象
     */
    @PostMapping("/knowledge-base")
    public Result<KnowledgeBaseVO> create(@RequestBody @Valid KnowledgeBaseCreateRequest request) {
        return Results.success(knowledgeBaseService.create(request));
    }

    /**
     * 分页查询知识库列表。
     * <p>
     * 支持关键字模糊搜索和状态过滤，分页边界由 Service 再兜底裁剪。
     *
     * @param request 分页查询参数
     * @return 知识库分页结果
     */
    @GetMapping("/knowledge-base")
    public Result<IPage<KnowledgeBaseVO>> page(@Valid KnowledgeBasePageRequest request) {
        return Results.success(knowledgeBaseService.page(
                request.getPageNo(),
                request.getPageSize(),
                request.getKeyword(),
                request.getStatus()
        ));
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库详情
     */
    @GetMapping("/knowledge-base/{id}")
    public Result<KnowledgeBaseVO> detail(@PathVariable String id) {
        return Results.success(knowledgeBaseService.detail(id));
    }

    /**
     * 更新知识库基础信息。
     * <p>
     * collectionName 创建后禁止修改，若请求体传入该字段会由 Service 返回明确错误。
     *
     * @param id      知识库 ID
     * @param request 更新请求
     * @return 更新后的知识库视图对象
     */
    @PutMapping("/knowledge-base/{id}")
    public Result<KnowledgeBaseVO> update(@PathVariable String id,
                                          @RequestBody @Valid KnowledgeBaseUpdateRequest request) {
        return Results.success(knowledgeBaseService.update(id, request));
    }

    /**
     * 逻辑删除知识库。
     * <p>
     * 删除前会检查知识库下是否仍存在未删除文档，避免孤儿文档和向量集合引用。
     *
     * @param id 知识库 ID
     * @return 空结果
     */
    @DeleteMapping("/knowledge-base/{id}")
    public Result<Void> delete(@PathVariable String id) {
        knowledgeBaseService.delete(id);
        return Results.success();
    }
}
