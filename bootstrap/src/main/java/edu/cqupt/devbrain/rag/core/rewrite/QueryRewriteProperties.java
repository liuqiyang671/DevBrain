package edu.cqupt.devbrain.rag.core.rewrite;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询改写配置，绑定 {@code rag.rewrite}。
 */
@Data
@ConfigurationProperties(prefix = "rag.rewrite")
public class QueryRewriteProperties {

    /** 是否启用查询改写。 */
    private boolean enabled = true;

    /** 传入 Prompt 的最近对话轮数；一轮包含 user 和 assistant 两条消息。 */
    private int historyTurns = 2;

    /** 标准术语 -> 别名列表。 */
    private Map<String, List<String>> termMappings = new LinkedHashMap<>();
}
