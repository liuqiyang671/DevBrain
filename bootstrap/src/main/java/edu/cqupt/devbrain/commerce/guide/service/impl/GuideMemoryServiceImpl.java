package edu.cqupt.devbrain.commerce.guide.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentMemoryDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.AgentMemoryMapper;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.service.GuideMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购长期记忆服务实现。
 * <p>
 * 管理用户的长期购物偏好记忆，支持：
 * <ul>
 *   <li><b>CRUD 操作</b> — listByUser / upsert / delete（软删除）</li>
 *   <li><b>记忆提取</b> — extractMemories 从 GuideState 的槽位中提取偏好信息</li>
 *   <li><b>记忆持久化</b> — persistExplicitMemories 批量提取并 upsert 到数据库</li>
 * </ul>
 * <p>
 * 记忆类型：
 * <ul>
 *   <li>preferred_brand — 品牌偏好（如"我想要华为"）</li>
 *   <li>avoid_brand — 品牌排除（如"不要苹果"）</li>
 *   <li>budget_range — 预算范围（JSON 格式，含 budgetMin/budgetMax）</li>
 *   <li>scenario — 使用场景（如"办公用"、"打游戏"）</li>
 * </ul>
 * <p>
 * upsert 策略：按 userId + memoryType + memoryKey 唯一，存在则更新值和置信度，不存在则插入。
 * 记忆按品类归类（memoryKey = category），同一品类下的同类偏好会被覆盖。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideMemoryService 接口
 * @see AgentMemoryDO 记忆实体
 */
@Service
@RequiredArgsConstructor
public class GuideMemoryServiceImpl implements GuideMemoryService {

    /** 记忆 Mapper */
    private final AgentMemoryMapper memoryMapper;

    @Override
    public List<AgentMemoryDO> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return memoryMapper.selectList(Wrappers.lambdaQuery(AgentMemoryDO.class)
                .eq(AgentMemoryDO::getUserId, userId)
                .eq(AgentMemoryDO::getDeleted, 0)
                .orderByDesc(AgentMemoryDO::getUpdateTime));
    }

    /**
     * 新增或更新记忆。
     * <p>
     * 按 userId + memoryType + memoryKey 唯一查找：
     * - 不存在 → 插入新记录
     * - 已存在 → 更新 memoryValue、confidence、source 和 lastUsedTime
     */
    @Override
    @Transactional
    public void upsert(String userId, String memoryType, String memoryKey, String memoryValue,
                       BigDecimal confidence, String source) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(memoryType)
                || !StringUtils.hasText(memoryKey) || !StringUtils.hasText(memoryValue)) {
            return;
        }
        AgentMemoryDO existing = memoryMapper.selectOne(Wrappers.lambdaQuery(AgentMemoryDO.class)
                .eq(AgentMemoryDO::getUserId, userId)
                .eq(AgentMemoryDO::getMemoryType, memoryType)
                .eq(AgentMemoryDO::getMemoryKey, memoryKey)
                .eq(AgentMemoryDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null) {
            AgentMemoryDO memory = new AgentMemoryDO();
            memory.setId(IdUtil.getSnowflakeNextIdStr());
            memory.setUserId(userId);
            memory.setMemoryType(memoryType);
            memory.setMemoryKey(memoryKey);
            memory.setMemoryValue(memoryValue);
            memory.setConfidence(confidence);
            memory.setSource(source);
            memoryMapper.insert(memory);
        } else {
            existing.setMemoryValue(memoryValue);
            existing.setConfidence(confidence);
            existing.setSource(source);
            existing.setLastUsedTime(new Date());
            memoryMapper.updateById(existing);
        }
    }

    @Override
    @Transactional
    public void delete(String userId, String memoryId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(memoryId)) {
            return;
        }
        AgentMemoryDO update = new AgentMemoryDO();
        update.setDeleted(1);
        memoryMapper.update(update, Wrappers.lambdaUpdate(AgentMemoryDO.class)
                .eq(AgentMemoryDO::getId, memoryId)
                .eq(AgentMemoryDO::getUserId, userId)
                .eq(AgentMemoryDO::getDeleted, 0));
    }

    /**
     * 从 GuideState 的槽位中提取可持久化的记忆。
     * <p>
     * 提取规则：
     * - brandPreference 非空 → preferred_brand 记忆
     * - attributes["avoidBrand"] 非空 → avoid_brand 记忆
     * - budgetMin 或 budgetMax 非空 → budget_range 记忆（JSON 格式）
     * - scenario 非空 → scenario 记忆
     * 所有记忆的 memoryKey 为当前品类（category），置信度固定 0.9。
     */
    @Override
    public List<AgentMemoryDO> extractMemories(GuideState state) {
        if (state == null || !StringUtils.hasText(state.getUserId()) || state.getSlots() == null) {
            return List.of();
        }
        GuideSlotState slots = state.getSlots();
        List<AgentMemoryDO> memories = new ArrayList<>();
        String category = valueOrDefault(slots.getCategory(), "default");
        if (StringUtils.hasText(slots.getBrandPreference())) {
            memories.add(memory(state.getUserId(), "preferred_brand", category,
                    slots.getBrandPreference(), "explicit"));
        }
        String avoidBrand = slots.getAttributes() == null ? null : slots.getAttributes().get("avoidBrand");
        if (StringUtils.hasText(avoidBrand)) {
            memories.add(memory(state.getUserId(), "avoid_brand", category, avoidBrand, "explicit"));
        }
        if (slots.getBudgetMin() != null || slots.getBudgetMax() != null) {
            memories.add(memory(state.getUserId(), "budget_range", category, budgetJson(slots), "explicit"));
        }
        if (StringUtils.hasText(slots.getScenario())) {
            memories.add(memory(state.getUserId(), "scenario", category, slots.getScenario(), "explicit"));
        }
        return memories;
    }

    @Override
    @Transactional
    public void persistExplicitMemories(GuideState state) {
        for (AgentMemoryDO memory : extractMemories(state)) {
            upsert(memory.getUserId(), memory.getMemoryType(), memory.getMemoryKey(), memory.getMemoryValue(),
                    memory.getConfidence(), memory.getSource());
        }
    }

    /** 构建记忆实体（置信度固定 0.9） */
    private AgentMemoryDO memory(String userId, String type, String key, String value, String source) {
        AgentMemoryDO memory = new AgentMemoryDO();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setConfidence(new BigDecimal("0.9000"));
        memory.setSource(source);
        return memory;
    }

    private String budgetJson(GuideSlotState slots) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            if (slots.getBudgetMin() != null) {
                value.put("budgetMin", slots.getBudgetMin());
            }
            if (slots.getBudgetMax() != null) {
                value.put("budgetMax", slots.getBudgetMax());
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
