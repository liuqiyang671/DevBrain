package edu.cqupt.devbrain.sync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.knowledge.controller.vo.DocumentVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.service.DocumentParseService;
import edu.cqupt.devbrain.knowledge.storage.FileStorageService;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapter;
import edu.cqupt.devbrain.sync.adapter.DocumentSourceAdapterRegistry;
import edu.cqupt.devbrain.sync.adapter.FetchedContent;
import edu.cqupt.devbrain.sync.controller.request.ScheduleConfigRequest;
import edu.cqupt.devbrain.sync.controller.vo.SyncHistoryVO;
import edu.cqupt.devbrain.sync.dao.entity.DocumentSyncHistoryDO;
import edu.cqupt.devbrain.sync.dao.mapper.DocumentSyncHistoryMapper;
import edu.cqupt.devbrain.sync.service.DocumentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

/**
 * 文档同步服务实现，负责拉取远程文档内容、比对哈希、上传存储并触发重新解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSyncServiceImpl implements DocumentSyncService {

    private static final String LOCK_PREFIX = "devbrain:sync:lock:";
    private static final long LOCK_WAIT_MS = 5000;
    private static final long LOCK_LEASE_MS = 300000;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentSyncHistoryMapper syncHistoryMapper;
    private final DocumentSourceAdapterRegistry adapterRegistry;
    private final FileStorageService fileStorageService;
    private final DocumentParseService documentParseService;
    private final RedissonClient redissonClient;

    /**
     * 执行文档同步：拉取内容、哈希比对、上传文件并触发重新解析。
     */
    @Override
    public SyncResult sync(String docId) {
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || document.getDeleted() != 0) {
            throw new ClientException("文档不存在或已删除");
        }
        if (!StringUtils.hasText(document.getSourceType()) || "file".equals(document.getSourceType())) {
            throw new ClientException("该文档不是在线同步类型");
        }
        if (!StringUtils.hasText(document.getSourceLocation())) {
            throw new ClientException("文档来源地址为空");
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + docId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException("获取同步锁被中断");
        }
        if (!acquired) {
            throw new ClientException("该文档正在同步中，请稍后再试");
        }

        long startTime = System.currentTimeMillis();
        String contentHash = null;
        boolean changed = false;
        try {
            DocumentSourceAdapter adapter = adapterRegistry.requireAdapter(document.getSourceType());
            FetchedContent fetched = adapter.fetchContent(document.getSourceLocation());

            contentHash = sha256(fetched.text());
            if (contentHash.equals(document.getLastContentHash())) {
                saveSyncHistory(docId, contentHash, 0, "success", null, System.currentTimeMillis() - startTime);
                return new SyncResult(false, "内容未变更，跳过解析");
            }

            byte[] bytes = fetched.text().getBytes(StandardCharsets.UTF_8);
            String objectKey = "sync/" + docId + "/" + System.currentTimeMillis() + ".txt";
            String fileUrl = fileStorageService.upload(objectKey,
                    new ByteArrayInputStream(bytes), "text/plain", bytes.length);

            document.setFileUrl(fileUrl);
            document.setFileType("txt");
            document.setFileSize((long) bytes.length);
            document.setLastContentHash(contentHash);
            document.setLastSyncTime(new Date());
            knowledgeDocumentMapper.updateById(document);

            documentParseService.parseAndChunk(docId);

            saveSyncHistory(docId, contentHash, 1, "success", null, System.currentTimeMillis() - startTime);
            return new SyncResult(true, "内容已更新并重新解析");

        } catch (Exception e) {
            log.error("文档同步失败，docId={}", docId, e);
            saveSyncHistory(docId, contentHash, 0, "failed", e.getMessage(), System.currentTimeMillis() - startTime);
            if (e instanceof ClientException clientException) {
                throw clientException;
            }
            if (e instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ClientException("文档同步失败: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 分页查询文档同步历史。
     */
    @Override
    public IPage<SyncHistoryVO> getSyncHistory(String docId, long pageNo, long pageSize) {
        Page<DocumentSyncHistoryDO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<DocumentSyncHistoryDO> wrapper = new LambdaQueryWrapper<DocumentSyncHistoryDO>()
                .eq(DocumentSyncHistoryDO::getDocId, docId)
                .orderByDesc(DocumentSyncHistoryDO::getCreateTime);
        IPage<DocumentSyncHistoryDO> result = syncHistoryMapper.selectPage(page, wrapper);
        return result.convert(this::toSyncHistoryVO);
    }

    /**
     * 更新文档的定时同步配置。
     */
    @Override
    public DocumentVO updateSchedule(String kbId, String docId, ScheduleConfigRequest request) {
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || document.getDeleted() != 0) {
            throw new ClientException("文档不存在或已删除");
        }
        if (!kbId.equals(document.getKbId())) {
            throw new ClientException("文档不属于该知识库");
        }

        if (!"file".equals(request.sourceType())) {
            if (!StringUtils.hasText(request.sourceLocation())) {
                throw new ClientException("在线文档来源地址不能为空");
            }
        }

        if (request.scheduleEnabled() == 1 && StringUtils.hasText(request.scheduleCron())) {
            try {
                org.springframework.scheduling.support.CronExpression.parse(request.scheduleCron());
            } catch (IllegalArgumentException e) {
                throw new ClientException("Cron 表达式格式错误: " + e.getMessage());
            }
        }

        document.setSourceType(request.sourceType());
        document.setSourceLocation(request.sourceLocation());
        document.setScheduleEnabled(request.scheduleEnabled());
        document.setScheduleCron(request.scheduleCron());
        knowledgeDocumentMapper.updateById(document);

        return toDocumentVO(document);
    }

    /**
     * 保存一条同步历史记录。
     */
    private void saveSyncHistory(String docId, String contentHash, int contentChanged,
                                 String status, String errorMessage, long durationMs) {
        try {
            DocumentSyncHistoryDO history = new DocumentSyncHistoryDO();
            history.setDocId(docId);
            history.setContentHash(contentHash);
            history.setContentChanged(contentChanged);
            history.setSyncStatus(status);
            history.setErrorMessage(errorMessage);
            history.setDurationMs(durationMs);
            syncHistoryMapper.insert(history);
        } catch (Exception e) {
            log.warn("保存同步历史失败，docId={}", docId, e);
        }
    }

    /**
     * 将同步历史 DO 转换为 VO。
     */
    private SyncHistoryVO toSyncHistoryVO(DocumentSyncHistoryDO h) {
        return new SyncHistoryVO(
                h.getId(), h.getDocId(), h.getSyncStatus(), h.getContentHash(),
                h.getContentChanged(), h.getErrorMessage(), h.getDurationMs(), h.getCreateTime()
        );
    }

    /**
     * 将文档 DO 转换为 VO。
     */
    private DocumentVO toDocumentVO(KnowledgeDocumentDO d) {
        return new DocumentVO(
                d.getId(), d.getKbId(), d.getDocName(), d.getEnabled(), d.getChunkCount(),
                d.getFileUrl(), d.getFileType(), d.getFileSize(), d.getProcessMode(),
                d.getStatus(), d.getSourceType(), d.getSourceLocation(),
                d.getChunkStrategy(), d.getChunkConfig(), d.getPipelineId(),
                d.getCreateTime(), d.getUpdateTime(),
                d.getScheduleEnabled(), d.getScheduleCron(),
                d.getLastSyncTime(), d.getLastContentHash()
        );
    }

    /**
     * 计算文本的 SHA-256 哈希值。
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("SHA-256 算法不可用", e, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
