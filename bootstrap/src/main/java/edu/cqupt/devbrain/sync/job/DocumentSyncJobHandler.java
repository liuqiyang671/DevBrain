package edu.cqupt.devbrain.sync.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.sync.service.DocumentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 文档定时同步任务处理器，由 XXL-Job 调度执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSyncJobHandler {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentSyncService documentSyncService;

    /**
     * 定时同步入口，遍历所有已启用定时同步的文档，按 Cron 表达式判断是否到期并执行同步。
     */
    @XxlJob("documentSyncHandler")
    public void execute() {
        List<KnowledgeDocumentDO> documents = knowledgeDocumentMapper.selectSyncEnabledDocuments();
        if (documents.isEmpty()) {
            return;
        }
        log.info("开始定时文档同步，共 {} 个文档待检查", documents.size());

        int synced = 0;
        int skipped = 0;
        int failed = 0;

        for (KnowledgeDocumentDO doc : documents) {
            try {
                if (!isCronDue(doc.getScheduleCron(), doc.getLastSyncTime())) {
                    skipped++;
                    continue;
                }
                DocumentSyncService.SyncResult result = documentSyncService.sync(doc.getId());
                synced++;
                log.info("文档同步完成，docId={}, changed={}", doc.getId(), result.contentChanged());
            } catch (Exception e) {
                failed++;
                log.error("文档同步失败，docId={}", doc.getId(), e);
            }
        }

        log.info("定时文档同步完成：同步 {}，跳过 {}，失败 {}", synced, skipped, failed);
    }

    /**
     * 根据 Cron 表达式和上次同步时间判断当前是否需要执行同步。
     */
    private boolean isCronDue(String cron, Date lastSyncTime) {
        if (!StringUtils.hasText(cron)) {
            return true;
        }
        try {
            CronExpression expr = CronExpression.parse(cron);
            Instant now = Instant.now();
            Instant lastTime = lastSyncTime != null ? lastSyncTime.toInstant() : Instant.EPOCH;
            Instant nextRun = expr.next(lastTime);
            return nextRun != null && !now.isBefore(nextRun);
        } catch (Exception e) {
            log.warn("Cron 表达式解析失败: {}", cron, e);
            return true;
        }
    }
}
