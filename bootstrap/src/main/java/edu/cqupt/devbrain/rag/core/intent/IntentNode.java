package edu.cqupt.devbrain.rag.core.intent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图节点实体，对应 t_intent_node。
 */
@Data
@TableName("t_intent_node")
public class IntentNode {

    @TableId
    private String id;

    /** 父节点 ID，根节点为空。 */
    private String parentId;

    private String name;

    /** 节点类型：KB / MCP / SYSTEM。 */
    private String kind;

    private String description;

    private String collectionName;

    private String mcpToolId;

    private String promptTemplate;

    private String paramPromptTemplate;

    private Integer topK;

    @TableField(exist = false)
    private List<IntentNode> children = new ArrayList<>();
}
