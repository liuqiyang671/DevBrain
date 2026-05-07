package edu.cqupt.devbrain.rag.core.retrieve;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 检索上下文，汇总知识库和 MCP 工具的检索结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalContext {

    /** 格式化后的知识库检索上下文文本。 */
    private String kbContext;

    /** 格式化后的 MCP 工具执行上下文文本。 */
    private String mcpContext;

    /** 意图 ID 到检索分块的映射，用于 Prompt 构建。 */
    private Map<String, List<RetrievedChunk>> intentChunks;

    public boolean isEmpty() {
        boolean noKb = !StringUtils.hasText(kbContext);
        boolean noMcp = !StringUtils.hasText(mcpContext);
        boolean noChunks = intentChunks == null || intentChunks.values().stream()
                .allMatch(chunks -> chunks == null || chunks.isEmpty());
        return noKb && noMcp && noChunks;
    }
}
