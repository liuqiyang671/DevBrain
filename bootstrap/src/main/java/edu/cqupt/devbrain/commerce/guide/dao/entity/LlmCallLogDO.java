package edu.cqupt.devbrain.commerce.guide.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cqupt.devbrain.knowledge.dao.typehandler.JsonbStringTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * LLM 调用日志实体。
 * <p>
 * 对应 t_llm_call_log 表，记录每次 LLM 调用的详细信息：
 * <ul>
 *   <li><b>关联信息</b> — runId（运行 ID）、stepId（步骤 ID）</li>
 *   <li><b>模型信息</b> — provider、model、businessScene</li>
 *   <li><b>调用参数</b> — stream、temperature、maxTokens</li>
 *   <li><b>用量统计</b> — inputTokens、outputTokens、durationMs</li>
 *   <li><b>内容摘要</b> — promptHash、promptSummary、responseHash、responseSummary</li>
 *   <li><b>执行结果</b> — status、errorMessage</li>
 *   <li><b>扩展数据</b> — metadataJson（JSONB 格式的元数据）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.observability.GuideAiCallObserver 调用观察者
 */
@Data
@TableName(value = "t_llm_call_log", autoResultMap = true)
public class LlmCallLogDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String runId;

    private String stepId;

    private String businessScene;

    private String provider;

    private String model;

    private Integer stream;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer inputTokens;

    private Integer outputTokens;

    private Long durationMs;

    private String status;

    private String errorMessage;

    private String promptHash;

    private String promptSummary;

    private String responseHash;

    private String responseSummary;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
