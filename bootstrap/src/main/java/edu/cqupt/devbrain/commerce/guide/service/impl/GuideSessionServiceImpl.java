package edu.cqupt.devbrain.commerce.guide.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideRecommendationDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideSessionDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideRecommendationMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideSessionMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.GuideSessionService;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 导购会话状态持久化服务实现。
 * <p>
 * 负责导购对话状态的 JSON 序列化持久化和反序列化恢复，以及推荐结果的存储。
 * <ul>
 *   <li><b>状态恢复</b> — restore() 从 t_guide_session 表读取 graphStateJson 并反序列化为 GuideState</li>
 *   <li><b>状态保存</b> — save() 将 GuideState 序列化后写入 t_guide_session（upsert 语义）</li>
 *   <li><b>推荐存储</b> — saveRecommendations() 先删除同 turnId 的旧推荐，再批量插入新推荐</li>
 * </ul>
 * <p>
 * 保存时同时更新 slotJson（槽位快照）、preferenceJson（图像引用等偏好）、stage（阶段：clarifying/recommended）。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideSessionService 接口
 * @see GuideSessionDO 会话实体
 * @see GuideRecommendationDO 推荐实体
 */
@Service
@RequiredArgsConstructor
public class GuideSessionServiceImpl implements GuideSessionService {

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 会话 Mapper */
    private final GuideSessionMapper guideSessionMapper;

    /** 推荐 Mapper */
    private final GuideRecommendationMapper guideRecommendationMapper;

    /**
     * 恢复会话状态。
     * <p>
     * 优先使用 conversationId 查找，sessionId 作为兜底；找不到或反序列化失败返回 null。
     */
    @Override
    public GuideState restore(String sessionId, String conversationId, String userId) {
        String key = StringUtils.hasText(conversationId) ? conversationId : sessionId;
        if (!StringUtils.hasText(key)) {
            return null;
        }
        GuideSessionDO session = guideSessionMapper.selectOne(Wrappers.lambdaQuery(GuideSessionDO.class)
                .eq(GuideSessionDO::getConversationId, key)
                .eq(GuideSessionDO::getUserId, userId)
                .eq(GuideSessionDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (session == null || !StringUtils.hasText(session.getGraphStateJson())) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(session.getGraphStateJson(), GuideState.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 保存会话状态（upsert 语义）。
     * <p>
     * 流程：查找已有会话 → 填充字段 → insert 或 update → 保存推荐结果。
     */
    @Override
    @Transactional
    public void save(GuideState state) {
        if (state == null || !StringUtils.hasText(state.getConversationId())) {
            return;
        }
        GuideSessionDO session = guideSessionMapper.selectOne(Wrappers.lambdaQuery(GuideSessionDO.class)
                .eq(GuideSessionDO::getConversationId, state.getConversationId())
                .eq(GuideSessionDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (session == null) {
            session = new GuideSessionDO();
            session.setConversationId(state.getConversationId());
            session.setUserId(state.getUserId());
            fillSession(session, state);
            guideSessionMapper.insert(session);
        } else {
            fillSession(session, state);
            guideSessionMapper.updateById(session);
        }
        saveRecommendations(state);
    }

    /** 将 GuideState 的关键字段填充到会话实体（stage、intent、slotJson、preferenceJson、graphStateJson） */
    private void fillSession(GuideSessionDO session, GuideState state) {
        session.setUserId(state.getUserId());
        session.setStage(StringUtils.hasText(state.getClarificationQuestion()) ? "clarifying" : "recommended");
        session.setIntent(state.getIntent() == null ? "unknown" : state.getIntent().getIntentType());
        session.setSlotJson(writeJson(state.getSlots()));
        session.setPreferenceJson(writeJson(Map.of(
                "imageRefs", state.getImageRefs() == null ? List.of() : state.getImageRefs()
        )));
        session.setGraphStateJson(writeJson(state));
    }

    /** 保存推荐结果：先删除同 turnId 的旧推荐，再按 rank 顺序批量插入新推荐 */
    private void saveRecommendations(GuideState state) {
        if (state.getRecommendations() == null || state.getRecommendations().isEmpty()) {
            return;
        }
        String turnId = StringUtils.hasText(state.getAgentRunId()) ? state.getAgentRunId() : state.getSessionId();
        guideRecommendationMapper.delete(Wrappers.lambdaQuery(GuideRecommendationDO.class)
                .eq(GuideRecommendationDO::getConversationId, state.getConversationId())
                .eq(GuideRecommendationDO::getTurnId, turnId));
        int rank = 1;
        for (GuideRecommendation recommendation : state.getRecommendations()) {
            GuideRecommendationDO entity = new GuideRecommendationDO();
            entity.setConversationId(state.getConversationId());
            entity.setTurnId(turnId);
            entity.setProductId(recommendation.getProductId());
            entity.setRankNo(rank++);
            entity.setScore(recommendation.getScore() == null ? null : BigDecimal.valueOf(recommendation.getScore()));
            entity.setReasonJson(writeJson(recommendation.getReasons()));
            entity.setEvidenceJson(writeJson(recommendation.getEvidences()));
            guideRecommendationMapper.insert(entity);
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("导购会话序列化失败", e, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
