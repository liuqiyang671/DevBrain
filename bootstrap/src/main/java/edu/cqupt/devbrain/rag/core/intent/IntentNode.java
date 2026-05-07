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

    /** 节点名称。 */
    private String name;

    /** 节点类型：KB（知识库）/ MCP（工具调用）/ SYSTEM（系统意图）。 */
    private String kind;

    /** 节点描述，用于 LLM 意图分类。 */
    private String description;

    /** KB 类型节点关联的向量集合名称。 */
    private String collectionName;

    /** MCP 类型节点关联的工具 ID。 */
    private String mcpToolId;

    /** 自定义 Prompt 模板路径，单意图命中时优先使用。 */
    private String promptTemplate;

    /** 参数提取 Prompt 模板路径。 */
    private String paramPromptTemplate;

    /** 该节点的默认检索条数。 */
    private Integer topK;

    /** 子节点列表，非数据库字段，由内存构建树时填充。 */
    @TableField(exist = false)
    private List<IntentNode> children = new ArrayList<>();
}
