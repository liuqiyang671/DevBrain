package edu.cqupt.devbrain.rag.core.prompt;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG Prompt 组装服务。
 */
@Service
public class RAGPromptService {

    static final String KB_TEMPLATE = "answer-chat-kb.st";
    static final String MCP_TEMPLATE = "answer-chat-mcp.st";
    static final String MIXED_TEMPLATE = "answer-chat-mcp-kb-mixed.st";
    static final String EMPTY_TEMPLATE = "answer-chat-empty.st";
    static final String CONTEXT_FORMAT_TEMPLATE = "context-format.st";

    private final PromptTemplateLoader templateLoader;

    public RAGPromptService(PromptTemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
    }

    public String buildSystemPrompt(PromptContext context) {
        PromptBuildPlan plan = plan(context);
        return renderTemplate(plan.getBaseTemplate(), slots(plan));
    }

    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        PromptContext effectiveContext = enrichQuestion(context, question);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(effectiveContext)));
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        String evidenceBody = buildEvidenceBody(effectiveContext);
        String userQuestion = buildUserQuestion(question, subQuestions);
        String userContent = joinNonBlank(List.of(evidenceBody, userQuestion));
        messages.add(ChatMessage.user(userContent));
        return messages;
    }

    PromptBuildPlan plan(PromptContext context) {
        PromptContext safeContext = context == null ? PromptContext.builder().build() : context;
        PromptScene scene = sceneOf(safeContext);
        return PromptBuildPlan.builder()
                .scene(scene)
                .baseTemplate(planPrompt(scene, safeContext))
                .mcpContext(blankToEmpty(safeContext.getMcpContext()))
                .kbContext(blankToEmpty(safeContext.getKbContext()))
                .question(blankToEmpty(safeContext.getQuestion()))
                .build();
    }

    String buildEvidenceBody(PromptContext context) {
        if (context == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (context.hasMcp()) {
            parts.add(templateLoader.renderSection(CONTEXT_FORMAT_TEMPLATE, "mcp-evidence",
                    Map.of("mcpContext", context.getMcpContext())));
        }
        if (context.hasKb()) {
            parts.add(templateLoader.renderSection(CONTEXT_FORMAT_TEMPLATE, "kb-evidence",
                    Map.of("kbContext", context.getKbContext())));
        }
        return joinNonBlank(parts);
    }

    String buildUserQuestion(String question, List<String> subQuestions) {
        List<String> effectiveQuestions = effectiveQuestions(question, subQuestions);
        if (effectiveQuestions.size() <= 1) {
            return templateLoader.renderSection(CONTEXT_FORMAT_TEMPLATE, "single-question",
                    Map.of("question", effectiveQuestions.isEmpty() ? "" : effectiveQuestions.get(0)));
        }
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < effectiveQuestions.size(); i++) {
            if (i > 0) {
                numbered.append('\n');
            }
            numbered.append(i + 1).append(". ").append(effectiveQuestions.get(i));
        }
        return templateLoader.renderSection(CONTEXT_FORMAT_TEMPLATE, "multi-questions",
                Map.of("questions", numbered.toString()));
    }

    private PromptScene sceneOf(PromptContext context) {
        boolean hasMcp = context.hasMcp();
        boolean hasKb = context.hasKb();
        if (hasMcp && hasKb) {
            return PromptScene.MIXED;
        }
        if (hasKb) {
            return PromptScene.KB_ONLY;
        }
        if (hasMcp) {
            return PromptScene.MCP_ONLY;
        }
        return PromptScene.EMPTY;
    }

    private String planPrompt(PromptScene scene, PromptContext context) {
        String customTemplate = singleIntentTemplate(context);
        if (StringUtils.hasText(customTemplate)) {
            return customTemplate;
        }
        return switch (scene) {
            case KB_ONLY -> KB_TEMPLATE;
            case MCP_ONLY -> MCP_TEMPLATE;
            case MIXED -> MIXED_TEMPLATE;
            case EMPTY -> EMPTY_TEMPLATE;
        };
    }

    private String singleIntentTemplate(PromptContext context) {
        List<NodeScore> allIntents = new ArrayList<>();
        if (context.getKbIntents() != null) {
            allIntents.addAll(context.getKbIntents());
        }
        if (context.getMcpIntents() != null) {
            allIntents.addAll(context.getMcpIntents());
        }
        List<IntentNode> nodes = allIntents.stream()
                .filter(score -> score != null && score.getNode() != null)
                .map(NodeScore::getNode)
                .toList();
        if (nodes.size() != 1) {
            return null;
        }
        return nodes.get(0).getPromptTemplate();
    }

    private PromptContext enrichQuestion(PromptContext context, String question) {
        if (context == null) {
            return PromptContext.builder().question(question).build();
        }
        if (StringUtils.hasText(context.getQuestion())) {
            return context;
        }
        return PromptContext.builder()
                .question(question)
                .mcpContext(context.getMcpContext())
                .kbContext(context.getKbContext())
                .mcpIntents(context.getMcpIntents())
                .kbIntents(context.getKbIntents())
                .intentChunks(context.getIntentChunks())
                .build();
    }

    private String renderTemplate(String path, Map<String, Object> slots) {
        String template = templateLoader.load(path);
        String result = template == null ? "" : template;
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result.strip();
    }

    private Map<String, Object> slots(PromptBuildPlan plan) {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("question", blankToEmpty(plan.getQuestion()));
        slots.put("mcpContext", blankToEmpty(plan.getMcpContext()));
        slots.put("kbContext", blankToEmpty(plan.getKbContext()));
        slots.put("scene", plan.getScene().name());
        return slots;
    }

    private List<String> effectiveQuestions(String question, List<String> subQuestions) {
        List<String> result = new ArrayList<>();
        if (subQuestions != null) {
            subQuestions.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .forEach(result::add);
        }
        if (result.isEmpty() && StringUtils.hasText(question)) {
            result.add(question.trim());
        }
        return result;
    }

    private String joinNonBlank(List<String> parts) {
        return parts.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
