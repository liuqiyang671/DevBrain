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

    /**
     * 根据检索上下文构建系统 Prompt。
     */
    public String buildSystemPrompt(PromptContext context) {
        PromptBuildPlan plan = plan(context);
        return renderTemplate(plan.getBaseTemplate(), slots(plan));
    }

    /**
     * 构建完整的结构化消息列表：system Prompt + 历史消息 + 证据体 + 用户问题。
     *
     * @param context      检索上下文
     * @param history      对话历史
     * @param question     用户原始问题
     * @param subQuestions 改写后的子问题列表
     */
    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        // 确保 context 中包含 question
        PromptContext effectiveContext = enrichQuestion(context, question);
        List<ChatMessage> messages = new ArrayList<>();
        // 1. system 消息：包含角色设定和工具使用规则
        messages.add(ChatMessage.system(buildSystemPrompt(effectiveContext)));
        // 2. 历史消息：多轮对话上下文
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        // 3. user 消息：证据体 + 用户问题拼接为一条消息
        String evidenceBody = buildEvidenceBody(effectiveContext);
        String userQuestion = buildUserQuestion(question, subQuestions);
        String userContent = joinNonBlank(List.of(evidenceBody, userQuestion));
        messages.add(ChatMessage.user(userContent));
        return messages;
    }

    PromptBuildPlan plan(PromptContext context) {
        PromptContext safeContext = context == null ? PromptContext.builder().build() : context;
        // 1. 判断 Prompt 场景（KB_ONLY / MCP_ONLY / MIXED / EMPTY）
        PromptScene scene = sceneOf(safeContext);
        // 2. 根据场景和单意图自定义模板选择最终模板路径
        return PromptBuildPlan.builder()
                .scene(scene)
                .baseTemplate(planPrompt(scene, safeContext))
                .mcpContext(blankToEmpty(safeContext.getMcpContext()))
                .kbContext(blankToEmpty(safeContext.getKbContext()))
                .question(blankToEmpty(safeContext.getQuestion()))
                .build();
    }

    /** 组装证据体，将 MCP 和知识库上下文拼接为结构化文本。 */
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

    /** 构建用户问题部分，多个子问题时编号展示。 */
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

    /** 根据上下文中是否有 MCP 和知识库内容判断 Prompt 场景。 */
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

    /**
     * 根据场景选择 Prompt 模板，单意图自定义模板优先于场景默认模板。
     */
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

    /**
     * 当只有一个意图节点时，优先使用该节点自定义的 Prompt 模板。
     */
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

    /**
     * 确保 PromptContext 中包含 question，缺失时从外部补充。
     */
    private PromptContext enrichQuestion(PromptContext context, String question) {
        if (context == null) {
            return PromptContext.builder().question(question).build();
        }
        // 已有 question 则直接返回
        if (StringUtils.hasText(context.getQuestion())) {
            return context;
        }
        // question 为空时，保留其他字段并补充 question
        return PromptContext.builder()
                .question(question)
                .mcpContext(context.getMcpContext())
                .kbContext(context.getKbContext())
                .mcpIntents(context.getMcpIntents())
                .kbIntents(context.getKbIntents())
                .intentChunks(context.getIntentChunks())
                .build();
    }

    /**
     * 加载模板并替换 {key} 占位符。
     */
    private String renderTemplate(String path, Map<String, Object> slots) {
        String template = templateLoader.load(path);
        String result = template == null ? "" : template;
        // 逐个替换占位符
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result.strip();
    }

    /** 将 PromptBuildPlan 中的字段封装为模板插槽 Map。 */
    private Map<String, Object> slots(PromptBuildPlan plan) {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("question", blankToEmpty(plan.getQuestion()));
        slots.put("mcpContext", blankToEmpty(plan.getMcpContext()));
        slots.put("kbContext", blankToEmpty(plan.getKbContext()));
        slots.put("scene", plan.getScene().name());
        return slots;
    }

    /**
     * 获取有效子问题列表：去重、去空白，为空时使用原始问题兜底。
     */
    private List<String> effectiveQuestions(String question, List<String> subQuestions) {
        List<String> result = new ArrayList<>();
        if (subQuestions != null) {
            subQuestions.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .forEach(result::add);
        }
        // 子问题全为空时使用原始问题
        if (result.isEmpty() && StringUtils.hasText(question)) {
            result.add(question.trim());
        }
        return result;
    }

    /** 将非空白的文本段用双换行拼接。 */
    private String joinNonBlank(List<String> parts) {
        return parts.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    /** null 安全的字符串转换，null 时返回空字符串。 */
    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
