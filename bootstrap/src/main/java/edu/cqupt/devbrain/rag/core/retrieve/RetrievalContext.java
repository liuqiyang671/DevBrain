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
 * 检索上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalContext {

    private String kbContext;

    private String mcpContext;

    private Map<String, List<RetrievedChunk>> intentChunks;

    public boolean isEmpty() {
        boolean noKb = !StringUtils.hasText(kbContext);
        boolean noMcp = !StringUtils.hasText(mcpContext);
        boolean noChunks = intentChunks == null || intentChunks.values().stream()
                .allMatch(chunks -> chunks == null || chunks.isEmpty());
        return noKb && noMcp && noChunks;
    }
}
