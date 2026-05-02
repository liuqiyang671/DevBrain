package edu.cqupt.devbrain.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseCreateRequest;
import edu.cqupt.devbrain.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import edu.cqupt.devbrain.knowledge.controller.vo.KnowledgeBaseVO;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.knowledge.service.KnowledgeBaseDocumentGuard;
import edu.cqupt.devbrain.knowledge.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 知识库服务实现 —— 负责校验、唯一性检查、分页查询和逻辑删除。
 * <p>
 * Controller 只负责收参和返回包装，所有领域规则都集中在这里，便于后续文档、
 * Chunk 和向量模块复用同一套知识库约束。
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    private static final Pattern COLLECTION_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentGuard documentGuard;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper, KnowledgeBaseDocumentGuard documentGuard) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentGuard = documentGuard;
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(KnowledgeBaseCreateRequest request) {
        String userId = UserContext.requireUser().userId();
        String collectionName = cleanRequired(request.collectionName(), "collectionName 不能为空");
        ensureCollectionNameValid(collectionName);
        // collectionName 会用于后续向量集合命名，必须在创建入口做全局唯一保护。
        ensureCollectionNameAvailable(collectionName, null);

        KnowledgeBaseDO knowledgeBase = new KnowledgeBaseDO();
        knowledgeBase.setName(cleanRequired(request.name(), "知识库名称不能为空"));
        knowledgeBase.setDescription(clean(request.description()));
        knowledgeBase.setEmbeddingModel(cleanRequired(request.embeddingModel(), "Embedding 模型不能为空"));
        knowledgeBase.setCollectionName(collectionName);
        knowledgeBase.setStatus(StringUtils.hasText(request.status()) ? request.status().trim() : STATUS_ENABLED);
        ensureStatusValid(knowledgeBase.getStatus());
        knowledgeBase.setCreatedBy(userId);
        knowledgeBase.setUpdatedBy(userId);
        knowledgeBaseMapper.insert(knowledgeBase);
        log.info("Knowledge base created: id={}, collectionName={}", knowledgeBase.getId(), collectionName);
        return toVO(knowledgeBase, 0L);
    }

    @Override
    public IPage<KnowledgeBaseVO> page(long pageNo, long pageSize, String keyword, String status) {
        // Service 层保留分页边界兜底，避免绕过 Controller 校验时查询过大数据集。
        long current = Math.max(1, pageNo);
        long size = Math.min(Math.max(1, pageSize), 100);
        String cleanedKeyword = clean(keyword);
        String cleanedStatus = clean(status);
        if (StringUtils.hasText(cleanedStatus)) {
            ensureStatusValid(cleanedStatus);
        }

        IPage<KnowledgeBaseDO> result = knowledgeBaseMapper.selectPage(new Page<>(current, size),
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        // 虽然 @TableLogic 会生效，这里保留显式条件让业务语义更直观。
                        .eq(KnowledgeBaseDO::getDeleted, 0)
                        .eq(StringUtils.hasText(cleanedStatus), KnowledgeBaseDO::getStatus, cleanedStatus)
                        .and(StringUtils.hasText(cleanedKeyword), wrapper -> wrapper
                                .like(KnowledgeBaseDO::getName, cleanedKeyword)
                                .or()
                                .like(KnowledgeBaseDO::getDescription, cleanedKeyword)
                                .or()
                                .like(KnowledgeBaseDO::getCollectionName, cleanedKeyword))
                        .orderByDesc(KnowledgeBaseDO::getUpdateTime));
        return result.convert(each -> toVO(each, documentGuard.countActiveDocuments(each.getId())));
    }

    @Override
    public KnowledgeBaseVO detail(String id) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(id);
        return toVO(knowledgeBase, documentGuard.countActiveDocuments(knowledgeBase.getId()));
    }

    @Override
    @Transactional
    public KnowledgeBaseVO update(String id, KnowledgeBaseUpdateRequest request) {
        // collectionName 与向量集合绑定，创建后禁止修改，避免后续文档和向量数据失联。
        if (request.collectionName() != null) {
            throw new ClientException("collectionName 创建后不允许修改");
        }
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(id);
        if (request.name() != null) {
            knowledgeBase.setName(cleanRequired(request.name(), "知识库名称不能为空"));
        }
        if (request.description() != null) {
            knowledgeBase.setDescription(clean(request.description()));
        }
        if (request.embeddingModel() != null) {
            knowledgeBase.setEmbeddingModel(cleanRequired(request.embeddingModel(), "Embedding 模型不能为空"));
        }
        if (request.status() != null) {
            String status = cleanRequired(request.status(), "状态不能为空");
            ensureStatusValid(status);
            knowledgeBase.setStatus(status);
        }
        knowledgeBase.setUpdatedBy(UserContext.requireUser().userId());
        knowledgeBaseMapper.updateById(knowledgeBase);
        log.info("Knowledge base updated: id={}", id);
        return toVO(knowledgeBase, documentGuard.countActiveDocuments(knowledgeBase.getId()));
    }

    @Override
    @Transactional
    public void delete(String id) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(id);
        long documentCount = documentGuard.countActiveDocuments(knowledgeBase.getId());
        if (documentCount > 0) {
            throw new ClientException("当前知识库下存在文档，请先删除文档后再删除知识库");
        }
        // 通过 MyBatis-Plus @TableLogic 执行逻辑删除，不直接物理删除数据。
        knowledgeBase.setUpdatedBy(UserContext.requireUser().userId());
        knowledgeBaseMapper.deleteById(knowledgeBase);
        log.info("Knowledge base deleted logically: id={}", id);
    }

    /**
     * 查询并确认知识库存在。
     * <p>
     * 对外统一把不存在和已删除都视为不可访问，避免泄露历史数据状态。
     */
    private KnowledgeBaseDO requireKnowledgeBase(String id) {
        if (!StringUtils.hasText(id)) {
            throw new ClientException("知识库 ID 不能为空");
        }
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectById(id);
        if (knowledgeBase == null || Integer.valueOf(1).equals(knowledgeBase.getDeleted())) {
            throw new ClientException("知识库不存在或已删除");
        }
        return knowledgeBase;
    }

    /**
     * 校验 collectionName 在未删除知识库中未被占用。
     */
    private void ensureCollectionNameAvailable(String collectionName, String excludeId) {
        Long count = knowledgeBaseMapper.selectCount(Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .eq(KnowledgeBaseDO::getCollectionName, collectionName)
                .eq(KnowledgeBaseDO::getDeleted, 0)
                .ne(StringUtils.hasText(excludeId), KnowledgeBaseDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new ClientException("collectionName 已存在：" + collectionName);
        }
    }

    /**
     * 校验 collectionName 满足向量集合命名要求：字母开头，只含字母、数字、下划线和中划线。
     */
    private void ensureCollectionNameValid(String collectionName) {
        if (!COLLECTION_NAME_PATTERN.matcher(collectionName).matches()) {
            throw new ClientException("collectionName 必须以字母开头，且只能包含字母、数字、下划线和中划线");
        }
    }

    /**
     * 校验知识库启停状态。
     */
    private void ensureStatusValid(String status) {
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new ClientException("状态只能为 enabled 或 disabled");
        }
    }

    /**
     * 清理字符串并校验必填字段。
     */
    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new ClientException(message);
        }
        return cleaned;
    }

    /**
     * 统一去除用户输入前后空白，保留 null 语义用于区分“未传字段”。
     */
    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 将数据库实体转换为前端视图对象，避免 DO 直接暴露给接口层。
     */
    private KnowledgeBaseVO toVO(KnowledgeBaseDO knowledgeBase, Long documentCount) {
        return new KnowledgeBaseVO(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getEmbeddingModel(),
                knowledgeBase.getCollectionName(),
                knowledgeBase.getStatus(),
                documentCount == null ? 0L : documentCount,
                knowledgeBase.getCreatedBy(),
                knowledgeBase.getUpdatedBy(),
                knowledgeBase.getCreateTime(),
                knowledgeBase.getUpdateTime()
        );
    }
}
