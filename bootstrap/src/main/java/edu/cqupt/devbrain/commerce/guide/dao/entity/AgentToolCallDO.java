package edu.cqupt.devbrain.commerce.guide.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cqupt.devbrain.knowledge.dao.typehandler.JsonbStringTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * Agent 工具调用记录实体。
 * <p>
 * 对应 t_agent_tool_call 表，记录一次工具调用的详细信息：
 * <ul>
 *   <li><b>关联信息</b> — runId（运行 ID）、stepId（步骤 ID）</li>
 *   <li><b>工具信息</b> — toolName、toolVersion</li>
 *   <li><b>调用数据</b> — argumentsJson（入参）、resultJson（结果）、observation（摘要）</li>
 *   <li><b>执行结果</b> — status、durationMs、errorMessage</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.observability.GuideAgentCallStatus 调用状态枚举
 */
@Data
@TableName(value = "t_agent_tool_call", autoResultMap = true)
public class AgentToolCallDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String runId;

    private String stepId;

    private String toolName;

    private String toolVersion;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String argumentsJson;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String resultJson;

    private String observation;

    private String status;

    private Long durationMs;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
