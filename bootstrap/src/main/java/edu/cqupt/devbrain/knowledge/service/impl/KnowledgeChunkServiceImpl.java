package edu.cqupt.devbrain.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.auth.core.DigestSupport;
import edu.cqupt.devbrain.core.chunk.VectorChunk;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkPageRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeChunkVO;
import edu.cqupt.devbrain.knowledge.controller.vo.PageResult;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeChunkDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeChunkMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeChunkService;
import edu.cqupt.devbrain.rag.core.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库分块服务实现，管理分块的 CRUD 并同步向量库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private final KnowledgeChunkMapper chunkMapper;
    private final VectorStoreService vectorStoreService;

    /**
     * 分页查询指定文档下的分块列表。
     */
    @Override
    public PageResult<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest request) {
        Page<KnowledgeChunkDO> page = new Page<>(request.getPageNo(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeChunkDO> wrapper = new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, docId)
                .eq(request.getEnabled() != null, KnowledgeChunkDO::getEnabled, request.getEnabled())
                .orderByAsc(KnowledgeChunkDO::getChunkIndex);

        IPage<KnowledgeChunkDO> result = chunkMapper.selectPage(page, wrapper);
        List<KnowledgeChunkVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 查询指定文档下的全部分块。
     */
    @Override
    public List<KnowledgeChunkDO> listByDocId(String docId) {
        return chunkMapper.selectByDocId(docId);
    }

    /**
     * 创建单个分块并同步到向量库。
     */
    @Override
    @Transactional
    public KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest request) {
        KnowledgeChunkDO entity = new KnowledgeChunkDO();
        entity.setId(request.chunkId() != null ? request.chunkId() : IdUtil.fastSimpleUUID());
        entity.setDocId(docId);
        entity.setChunkIndex(request.index());
        entity.setContent(request.content());
        entity.setContentHash(DigestSupport.sha256(request.content()));
        entity.setCharCount(request.content().length());
        entity.setEnabled(1);

        chunkMapper.insert(entity);
        syncChunkToVector(entity);

        log.info("创建分块 docId={}, chunkId={}", docId, entity.getId());
        return toVO(entity);
    }

    /**
     * 批量创建分块，可选同步到向量库。
     */
    @Override
    @Transactional
    public List<KnowledgeChunkDO> batchCreate(List<KnowledgeChunkDO> chunks, boolean syncToVector) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        for (KnowledgeChunkDO chunk : chunks) {
            if (chunk.getId() == null) {
                chunk.setId(IdUtil.fastSimpleUUID());
            }
            chunk.setContentHash(DigestSupport.sha256(chunk.getContent()));
            chunk.setCharCount(chunk.getContent().length());
            if (chunk.getEnabled() == null) {
                chunk.setEnabled(1);
            }
        }

        chunkMapper.insertBatch(chunks);

        if (syncToVector && !chunks.isEmpty()) {
            String kbId = chunks.get(0).getKbId();
            String docId = chunks.get(0).getDocId();
            String collectionName = "kb_" + kbId;
            List<VectorChunk> vectorChunks = chunks.stream().map(this::toVectorChunk).toList();
            vectorStoreService.indexDocumentChunks(collectionName, docId, vectorChunks);
            log.info("批量创建分块并同步向量库 docId={}, count={}", docId, chunks.size());
        }

        return chunks;
    }

    /**
     * 更新分块内容并同步向量库。
     */
    @Override
    @Transactional
    public KnowledgeChunkVO update(String docId, String chunkId, KnowledgeChunkUpdateRequest request) {
        KnowledgeChunkDO entity = chunkMapper.selectById(chunkId);
        if (entity == null || !entity.getDocId().equals(docId)) {
            throw new IllegalArgumentException("分块不存在: " + chunkId);
        }

        entity.setContent(request.content());
        entity.setContentHash(DigestSupport.sha256(request.content()));
        entity.setCharCount(request.content().length());
        chunkMapper.updateById(entity);

        syncChunkToVector(entity);

        log.info("更新分块 docId={}, chunkId={}", docId, chunkId);
        return toVO(entity);
    }

    /**
     * 删除单个分块并从向量库移除。
     */
    @Override
    @Transactional
    public void delete(String docId, String chunkId) {
        KnowledgeChunkDO entity = chunkMapper.selectById(chunkId);
        if (entity == null || !entity.getDocId().equals(docId)) {
            return;
        }

        chunkMapper.deleteById(chunkId);
        deleteChunkFromVector(entity);

        log.info("删除分块 docId={}, chunkId={}", docId, chunkId);
    }

    /**
     * 删除指定文档下的全部分块并清除对应向量。
     */
    @Override
    @Transactional
    public void deleteByDocId(String docId) {
        List<KnowledgeChunkDO> chunks = chunkMapper.selectByDocId(docId);
        chunkMapper.deleteByDocId(docId);

        if (!chunks.isEmpty()) {
            String kbId = chunks.get(0).getKbId();
            String collectionName = "kb_" + kbId;
            vectorStoreService.deleteDocumentVectors(collectionName, docId);
            log.info("删除文档全部分块 docId={}, count={}", docId, chunks.size());
        }
    }

    /**
     * 启用或禁用单个分块。
     */
    @Override
    @Transactional
    public void enableChunk(String chunkId, boolean enabled) {
        KnowledgeChunkDO entity = chunkMapper.selectById(chunkId);
        if (entity == null) {
            return;
        }
        entity.setEnabled(enabled ? 1 : 0);
        chunkMapper.updateById(entity);
    }

    /**
     * 批量启用或禁用分块。
     */
    @Override
    @Transactional
    public void batchToggleEnabled(List<String> chunkIds, boolean enabled) {
        if (chunkIds.isEmpty()) {
            return;
        }
        List<KnowledgeChunkDO> entities = chunkMapper.selectBatchIds(chunkIds);
        for (KnowledgeChunkDO entity : entities) {
            entity.setEnabled(enabled ? 1 : 0);
        }
        for (KnowledgeChunkDO entity : entities) {
            chunkMapper.updateById(entity);
        }
        log.info("批量{}分块 count={}", enabled ? "启用" : "禁用", entities.size());
    }

    /**
     * 按文档批量启用或禁用全部分块。
     */
    @Override
    @Transactional
    public void updateEnabledByDocId(String docId, boolean enabled) {
        LambdaQueryWrapper<KnowledgeChunkDO> wrapper = new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, docId);
        KnowledgeChunkDO update = new KnowledgeChunkDO();
        update.setEnabled(enabled ? 1 : 0);
        chunkMapper.update(update, wrapper);
        log.info("按文档批量{}分块 docId={}", enabled ? "启用" : "禁用", docId);
    }

    /**
     * 将单个分块同步到向量库（更新或新增）。
     */
    private void syncChunkToVector(KnowledgeChunkDO entity) {
        if (entity.getKbId() == null) {
            return;
        }
        String collectionName = "kb_" + entity.getKbId();
        VectorChunk vectorChunk = toVectorChunk(entity);
        vectorStoreService.updateChunk(collectionName, entity.getDocId(), vectorChunk);
    }

    /**
     * 从向量库删除单个分块。
     */
    private void deleteChunkFromVector(KnowledgeChunkDO entity) {
        if (entity.getKbId() == null) {
            return;
        }
        String collectionName = "kb_" + entity.getKbId();
        vectorStoreService.deleteChunkById(collectionName, entity.getId());
    }

    /**
     * KnowledgeChunkDO → VectorChunk 转换。
     */
    private VectorChunk toVectorChunk(KnowledgeChunkDO entity) {
        VectorChunk chunk = new VectorChunk();
        chunk.setChunkId(entity.getId());
        chunk.setIndex(entity.getChunkIndex());
        chunk.setContent(entity.getContent());
        return chunk;
    }

    /**
     * KnowledgeChunkDO → KnowledgeChunkVO 转换。
     */
    private KnowledgeChunkVO toVO(KnowledgeChunkDO entity) {
        return new KnowledgeChunkVO(
                entity.getId(),
                entity.getKbId(),
                entity.getDocId(),
                entity.getChunkIndex(),
                entity.getContent(),
                entity.getContentHash(),
                entity.getCharCount(),
                entity.getTokenCount(),
                entity.getEnabled(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
