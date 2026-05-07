package edu.cqupt.devbrain.rag.core.prompt;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Prompt 构建上下文。
 */
@Data
@Builder
public class PromptContext {

    private String question;

    private String mcpContext;

    private String kbContext;

    private List<NodeScore> mcpIntents;

    private List<NodeScore> kbIntents;

    private Map<String, List<RetrievedChunk>> intentChunks;

    public boolean hasMcp() {
        return StringUtils.hasText(mcpContext);
    }

    public boolean hasKb() {
        return StringUtils.hasText(kbContext);
    }
}
