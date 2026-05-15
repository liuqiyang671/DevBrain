package edu.cqupt.devbrain.commerce.guide.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Agent 长期记忆实体。
 * <p>
 * 对应 t_agent_memory 表，存储用户的长期偏好和记忆：
 * <ul>
 *   <li><b>记忆类型</b> — memoryType（如 preference、history 等）</li>
 *   <li><b>记忆键值</b> — memoryKey（如 brand_preference）、memoryValue（如 "小米"）</li>
 *   <li><b>置信度</b> — confidence（0-1，表示记忆的可靠程度）</li>
 *   <li><b>来源</b> — source（记忆来源，如 user_explicit、inferred）</li>
 *   <li><b>使用时间</b> — lastUsedTime（最后一次使用时间，用于 LRU 淘汰）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.service.GuideMemoryService 记忆服务
 */
@Data
@TableName("t_agent_memory")
public class AgentMemoryDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String memoryType;

    private String memoryKey;

    private String memoryValue;

    private BigDecimal confidence;

    private String source;

    private Date lastUsedTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
