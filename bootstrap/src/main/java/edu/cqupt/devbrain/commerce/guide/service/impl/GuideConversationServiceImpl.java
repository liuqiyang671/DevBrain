package edu.cqupt.devbrain.commerce.guide.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideMessageDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideRecommendationDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideSessionDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideMessageMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideRecommendationMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideSessionMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideMessageResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideRecommendationResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideSessionResp;
import edu.cqupt.devbrain.commerce.guide.service.GuideConversationService;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 导购会话历史服务实现。
 * <p>
 * 管理导购对话的完整生命周期，包括：
 * <ul>
 *   <li><b>消息追加</b> — appendUserMessage / appendAssistantMessage，支持幂等（clientMessageId 去重）</li>
 *   <li><b>会话查询</b> — pageSessions（分页）、detail（详情）、listMessages / listRecommendations</li>
 *   <li><b>会话归档</b> — archiveSession（生成摘要并标记归档）、restoreSession（恢复）</li>
 *   <li><b>会话删除</b> — deleteSession（物理删除）</li>
 * </ul>
 * <p>
 * 幂等机制：通过 clientMessageId 查询已有记录，避免网络重试导致消息重复。
 * 会话自动创建：追加消息时如果会话不存在，会自动创建（stage=started, intent=unknown）。
 * 会话标题：取第一条用户消息的前 24 字符，无用户消息时使用品类名称。
 * 归档摘要：取主题（首条用户消息）+ 最后一条 assistant 回答，截断到 88 字符。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideConversationService 接口
 * @see GuideSessionDO 会话实体
 * @see GuideMessageDO 消息实体
 * @see GuideRecommendationDO 推荐实体
 */
@Service
@RequiredArgsConstructor
public class GuideConversationServiceImpl implements GuideConversationService {

    /** JSON 序列化器（用于读取图像引用列表、推荐证据等 JSON 字段） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 会话 Mapper */
    private final GuideSessionMapper sessionMapper;

    /** 消息 Mapper */
    private final GuideMessageMapper messageMapper;

    /** 推荐 Mapper */
    private final GuideRecommendationMapper recommendationMapper;

    /** 追加用户消息（支持图像引用和幂等去重） */
    @Override
    public void appendUserMessage(String sessionId, String conversationId, String userId,
                                  String content, List<String> imageRefs, String clientMessageId, String agentRunId) {
        appendMessage(sessionId, conversationId, userId, "user", content, imageRefs, clientMessageId, agentRunId);
    }

    /** 追加助手消息（从 GuideState 提取回答草稿，不支持幂等去重） */
    @Override
    public void appendAssistantMessage(GuideState state, String agentRunId) {
        if (state == null) {
            return;
        }
        appendMessage(state.getSessionId(), state.getConversationId(), state.getUserId(), "assistant",
                state.getAnswerDraft(), List.of(), null, agentRunId);
    }

    @Override
    public IPage<GuideSessionResp> pageSessions(String userId, long pageNo, long pageSize) {
        IPage<GuideSessionDO> page = sessionMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.lambdaQuery(GuideSessionDO.class)
                        .eq(GuideSessionDO::getUserId, userId)
                        .eq(GuideSessionDO::getDeleted, 0)
                        .orderByDesc(GuideSessionDO::getUpdateTime));
        return page.convert(session -> toSessionResp(session, false));
    }

    @Override
    public GuideSessionResp detail(String sessionId, String userId) {
        GuideSessionDO session = requireSession(sessionId, userId);
        return toSessionResp(session, true);
    }

    @Override
    public List<GuideMessageResp> listMessages(String sessionId, String userId) {
        GuideSessionDO session = requireSession(sessionId, userId);
        return messages(session.getConversationId()).stream().map(this::toMessageResp).toList();
    }

    @Override
    public List<GuideRecommendationResp> listRecommendations(String sessionId, String userId) {
        GuideSessionDO session = requireSession(sessionId, userId);
        return recommendations(session.getConversationId()).stream().map(this::toRecommendationResp).toList();
    }

    @Override
    @Transactional
    public void archiveSession(String sessionId, String userId) {
        GuideSessionDO session = requireSession(sessionId, userId);
        List<GuideMessageDO> messages = messages(session.getConversationId());
        GuideSessionDO update = new GuideSessionDO();
        update.setArchived(1);
        update.setArchivedTime(new Date());
        update.setArchiveSummary(summarize(messages, session));
        sessionMapper.update(update, Wrappers.lambdaUpdate(GuideSessionDO.class)
                .eq(GuideSessionDO::getId, session.getId())
                .eq(GuideSessionDO::getUserId, userId)
                .eq(GuideSessionDO::getDeleted, 0));
    }

    @Override
    @Transactional
    public void restoreSession(String sessionId, String userId) {
        GuideSessionDO session = requireSession(sessionId, userId);
        GuideSessionDO update = new GuideSessionDO();
        update.setArchived(0);
        update.setArchivedTime(null);
        sessionMapper.update(update, Wrappers.lambdaUpdate(GuideSessionDO.class)
                .eq(GuideSessionDO::getId, session.getId())
                .eq(GuideSessionDO::getUserId, userId)
                .eq(GuideSessionDO::getDeleted, 0));
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId, String userId) {
        GuideSessionDO session = requireSession(sessionId, userId);
        sessionMapper.deleteById(session);
    }

    /**
     * 追加消息的核心方法。
     * <p>
     * 流程：参数校验 → 确保会话存在 → 幂等检查（clientMessageId 去重） → 构建并插入消息。
     */
    private void appendMessage(String sessionId, String conversationId, String userId, String role,
                               String content, List<String> imageRefs, String clientMessageId, String agentRunId) {
        // 必要字段校验，任一为空则静默跳过
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)
                || !StringUtils.hasText(role) || !StringUtils.hasText(content)) {
            return;
        }
        // 确保会话记录存在，不存在则自动创建
        ensureSession(sessionId, conversationId, userId);
        // 幂等检查：如果 clientMessageId 已存在，则跳过（防止网络重试导致重复）
        if (StringUtils.hasText(clientMessageId)) {
            Long count = messageMapper.selectCount(Wrappers.lambdaQuery(GuideMessageDO.class)
                    .eq(GuideMessageDO::getClientMessageId, clientMessageId)
                    .eq(GuideMessageDO::getUserId, userId)
                    .eq(GuideMessageDO::getDeleted, 0));
            if (count != null && count > 0) {
                return;
            }
        }
        GuideMessageDO message = new GuideMessageDO();
        message.setId(IdUtil.getSnowflakeNextIdStr());
        message.setConversationId(conversationId);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setImageRefsJson(writeJson(imageRefs == null ? List.of() : imageRefs));
        message.setClientMessageId(clientMessageId);
        message.setAgentRunId(agentRunId);
        messageMapper.insert(message);
    }

    /** 确保会话记录存在，不存在则自动创建（stage=started, intent=unknown） */
    private void ensureSession(String sessionId, String conversationId, String userId) {
        Long count = sessionMapper.selectCount(Wrappers.lambdaQuery(GuideSessionDO.class)
                .eq(GuideSessionDO::getConversationId, conversationId)
                .eq(GuideSessionDO::getDeleted, 0));
        if (count != null && count > 0) {
            return;
        }
        GuideSessionDO session = new GuideSessionDO();
        session.setId(StringUtils.hasText(sessionId) ? sessionId : IdUtil.getSnowflakeNextIdStr());
        session.setConversationId(conversationId);
        session.setUserId(userId);
        session.setStage("started");
        session.setIntent("unknown");
        sessionMapper.insert(session);
    }

    /** 查询会话（支持 sessionId 或 conversationId 查找），不存在则抛 ClientException */
    private GuideSessionDO requireSession(String sessionId, String userId) {
        GuideSessionDO session = sessionMapper.selectOne(Wrappers.lambdaQuery(GuideSessionDO.class)
                .and(wrapper -> wrapper.eq(GuideSessionDO::getId, sessionId)
                        .or()
                        .eq(GuideSessionDO::getConversationId, sessionId))
                .eq(GuideSessionDO::getUserId, userId)
                .eq(GuideSessionDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (session == null) {
            throw new ClientException("导购会话不存在或无权访问");
        }
        return session;
    }

    /**
     * 将会话实体转换为响应 DTO。
     * <p>
     * includeDetail=true 时加载完整消息列表和推荐列表；否则只取最后一条消息用于预览。
     */
    private GuideSessionResp toSessionResp(GuideSessionDO session, boolean includeDetail) {
        List<GuideMessageDO> messages = includeDetail ? messages(session.getConversationId()) : recentMessages(session.getConversationId(), 1);
        List<GuideRecommendationDO> recommendations = includeDetail ? recommendations(session.getConversationId()) : List.of();
        GuideMessageDO lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        GuideState state = includeDetail ? readState(session.getGraphStateJson()) : null;
        return new GuideSessionResp(
                session.getId(),
                session.getConversationId(),
                session.getUserId(),
                session.getStage(),
                session.getIntent(),
                title(messages, state),
                lastMessage == null ? null : lastMessage.getContent(),
                session.getCreateTime(),
                session.getUpdateTime(),
                Integer.valueOf(1).equals(session.getArchived()),
                session.getArchivedTime(),
                session.getArchiveSummary(),
                messageCount(session.getConversationId()),
                state,
                includeDetail ? messages.stream().map(this::toMessageResp).toList() : List.of(),
                recommendations.stream().map(this::toRecommendationResp).toList()
        );
    }

    private List<GuideMessageDO> messages(String conversationId) {
        return messageMapper.selectList(Wrappers.lambdaQuery(GuideMessageDO.class)
                .eq(GuideMessageDO::getConversationId, conversationId)
                .eq(GuideMessageDO::getDeleted, 0)
                .orderByAsc(GuideMessageDO::getCreateTime));
    }

    private List<GuideMessageDO> recentMessages(String conversationId, int limit) {
        return messageMapper.selectList(Wrappers.lambdaQuery(GuideMessageDO.class)
                .eq(GuideMessageDO::getConversationId, conversationId)
                .eq(GuideMessageDO::getDeleted, 0)
                .orderByDesc(GuideMessageDO::getCreateTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    private List<GuideRecommendationDO> recommendations(String conversationId) {
        return recommendationMapper.selectList(Wrappers.lambdaQuery(GuideRecommendationDO.class)
                .eq(GuideRecommendationDO::getConversationId, conversationId)
                .eq(GuideRecommendationDO::getDeleted, 0)
                .orderByDesc(GuideRecommendationDO::getCreateTime)
                .orderByAsc(GuideRecommendationDO::getRankNo));
    }

    private int messageCount(String conversationId) {
        Long count = messageMapper.selectCount(Wrappers.lambdaQuery(GuideMessageDO.class)
                .eq(GuideMessageDO::getConversationId, conversationId)
                .eq(GuideMessageDO::getDeleted, 0));
        return count == null ? 0 : count.intValue();
    }

    /** 生成会话标题：取首条用户消息前 24 字符，无用户消息时使用品类名称 */
    private String title(List<GuideMessageDO> messages, GuideState state) {
        return messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(GuideMessageDO::getContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(this::abbreviate)
                .orElseGet(() -> state == null || state.getSlots() == null || !StringUtils.hasText(state.getSlots().getCategory())
                        ? "导购会话"
                        : state.getSlots().getCategory() + " 导购");
    }

    /** 生成归档摘要：主题 + 最后一条 assistant 回答，截断到 88 字符 */
    private String summarize(List<GuideMessageDO> messages, GuideSessionDO session) {
        String topic = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(GuideMessageDO::getContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> {
                    GuideState state = readState(session.getGraphStateJson());
                    return state == null || state.getSlots() == null || !StringUtils.hasText(state.getSlots().getCategory())
                            ? "导购会话"
                            : state.getSlots().getCategory() + " 导购";
                });
        String answer = messages.stream()
                .filter(message -> "assistant".equals(message.getRole()))
                .map(GuideMessageDO::getContent)
                .filter(StringUtils::hasText)
                .reduce((ignored, latest) -> latest)
                .orElse(null);
        String summary = StringUtils.hasText(answer) ? topic + "；" + answer : topic;
        return summary.length() <= 88 ? summary : summary.substring(0, 88) + "...";
    }

    private GuideMessageResp toMessageResp(GuideMessageDO message) {
        return new GuideMessageResp(message.getId(), message.getConversationId(), message.getSessionId(),
                message.getRole(), message.getContent(), readStringList(message.getImageRefsJson()),
                message.getClientMessageId(), message.getAgentRunId(), message.getCreateTime());
    }

    private GuideRecommendationResp toRecommendationResp(GuideRecommendationDO recommendation) {
        return new GuideRecommendationResp(
                recommendation.getId(),
                recommendation.getConversationId(),
                recommendation.getTurnId(),
                recommendation.getProductId(),
                recommendation.getSkuId(),
                recommendation.getRankNo(),
                recommendation.getScore(),
                readStringList(recommendation.getReasonJson()),
                readMapList(recommendation.getEvidenceJson()),
                recommendation.getCreateTime()
        );
    }

    private GuideState readState(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, GuideState.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new ServiceException("导购消息序列化失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String abbreviate(String text) {
        String cleaned = text.trim();
        return cleaned.length() <= 24 ? cleaned : cleaned.substring(0, 24) + "...";
    }
}
