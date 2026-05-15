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
 * Agent 步骤记录实体。
 * <p>
 * 对应 t_agent_step 表，记录一次 Agent 运行中的单个步骤：
 * <ul>
 *   <li><b>步骤信息</b> — stepNo（步骤序号）、action（工具名）、thought（思考过程）</li>
 *   <li><b>执行结果</b> — observation（观测摘要）、status、durationMs、errorMessage</li>
 *   <li><b>状态快照</b> — stateBeforeHash、stateAfterHash（步骤前后的状态哈希）</li>
 *   <li><b>参数</b> — argumentsJson（JSONB 格式的工具调用参数）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.observability.GuideAgentStepStatus 步骤状态枚举
 */
@Data
@TableName(value = "t_agent_step", autoResultMap = true)
public class AgentStepDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String runId;

    private Integer stepNo;

    private String action;

    private String thought;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String argumentsJson;

    private String observation;

    private String status;

    private Long durationMs;

    private String errorMessage;

    private String stateBeforeHash;

    private String stateAfterHash;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
