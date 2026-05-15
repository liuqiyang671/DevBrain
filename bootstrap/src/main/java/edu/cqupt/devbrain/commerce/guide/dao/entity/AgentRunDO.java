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
 * Agent 运行记录实体。
 * <p>
 * 对应 t_agent_run 表，记录一次 Agent 运行的完整生命周期信息：
 * <ul>
 *   <li><b>关联信息</b> — conversationId、sessionId、userId</li>
 *   <li><b>运行配置</b> — scene（场景）、engineName（引擎名）</li>
 *   <li><b>执行结果</b> — status、totalSteps、finalAction、errorMessage</li>
 *   <li><b>时间信息</b> — startedAt、finishedAt</li>
 *   <li><b>扩展数据</b> — metadataJson（JSONB 格式的元数据）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunStatus 运行状态枚举
 */
@Data
@TableName(value = "t_agent_run", autoResultMap = true)
public class AgentRunDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String conversationId;

    private String sessionId;

    private String userId;

    private String scene;

    private String engineName;

    private String status;

    private Date startedAt;

    private Date finishedAt;

    private Integer totalSteps;

    private String finalAction;

    private String errorMessage;

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
