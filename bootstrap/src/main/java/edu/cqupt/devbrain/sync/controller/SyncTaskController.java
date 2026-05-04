package edu.cqupt.devbrain.sync.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.sync.controller.request.ScheduleConfigRequest;
import edu.cqupt.devbrain.sync.controller.vo.SyncHistoryVO;
import edu.cqupt.devbrain.sync.controller.vo.SyncTaskOverviewVO;
import edu.cqupt.devbrain.sync.service.DocumentSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档同步任务控制器，提供定时同步配置、手动触发、历史查询等 REST 接口。
 */
@RestController
@RequiredArgsConstructor
public class SyncTaskController {

    private final DocumentSyncService documentSyncService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    /**
     * 更新文档的定时同步配置（来源类型、来源地址、Cron 表达式等）。
     */
    @PutMapping("/knowledge-base/{kbId}/docs/{docId}/schedule")
    public Result<DocumentVO> updateSchedule(@PathVariable String kbId,
                                             @PathVariable String docId,
                                             @RequestBody @Valid ScheduleConfigRequest request) {
        return Results.success(documentSyncService.updateSchedule(kbId, docId, request));
    }

    /**
     * 手动触发单个文档的同步任务。
     */
    @PostMapping("/sync-tasks/{docId}/trigger")
    public Result<DocumentSyncService.SyncResult> triggerSync(@PathVariable String docId) {
        return Results.success(documentSyncService.sync(docId));
    }

    /**
     * 分页查询文档的同步历史记录。
     */
    @GetMapping("/sync-tasks/{docId}/history")
    public Result<IPage<SyncHistoryVO>> getSyncHistory(@PathVariable String docId,
                                                       @RequestParam(defaultValue = "1") long pageNo,
                                                       @RequestParam(defaultValue = "10") long pageSize) {
        return Results.success(documentSyncService.getSyncHistory(docId, pageNo, pageSize));
    }

    /**
     * 查询所有已启用定时同步的文档概览列表。
     */
    @GetMapping("/sync-tasks/overview")
    public Result<List<SyncTaskOverviewVO>> getSyncTaskOverview() {
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<KnowledgeDocumentDO>()
                .eq(KnowledgeDocumentDO::getDeleted, 0)
                .eq(KnowledgeDocumentDO::getScheduleEnabled, 1)
                .in(KnowledgeDocumentDO::getSourceType, "feishu", "url")
                .orderByDesc(KnowledgeDocumentDO::getUpdateTime);
        List<KnowledgeDocumentDO> documents = knowledgeDocumentMapper.selectList(wrapper);

        List<SyncTaskOverviewVO> overview = documents.stream()
                .map(d -> new SyncTaskOverviewVO(
                        d.getId(), d.getDocName(), d.getKbId(),
                        d.getSourceType(), d.getSourceLocation(),
                        d.getScheduleEnabled(), d.getScheduleCron(),
                        d.getLastSyncTime(), d.getLastContentHash()))
                .toList();

        return Results.success(overview);
    }
}
