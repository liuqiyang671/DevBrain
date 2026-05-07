package edu.cqupt.devbrain.rag.core.prompt;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RAGPromptServiceTest {

    private final StubPromptTemplateLoader templateLoader = new StubPromptTemplateLoader();
    private final RAGPromptService promptService = new RAGPromptService(templateLoader);

    @Test
    void buildSystemPromptShouldUseKbDefaultTemplateForKbOnlyScene() {
        templateLoader.templates.put("answer-chat-kb.st", "KB 默认：{question}\n{kbContext}");

        String result = promptService.buildSystemPrompt(PromptContext.builder()
                .question("后端怎么部署")
                .kbContext("<documents>部署文档</documents>")
                .kbIntents(List.of(score("kb-dev", "KB", null)))
                .build());

        assertEquals("KB 默认：后端怎么部署\n<documents>部署文档</documents>", result);
    }

    @Test
    void buildSystemPromptShouldUseSingleIntentCustomTemplateWhenAvailable() {
        templateLoader.templates.put("custom/devops.st", "自定义模板：{question}");

        String result = promptService.buildSystemPrompt(PromptContext.builder()
                .question("部署方式")
                .kbContext("<documents>部署文档</documents>")
                .kbIntents(List.of(score("kb-dev", "KB", "custom/devops.st")))
                .build());

        assertEquals("custom/devops.st", templateLoader.loadedPaths.get(0));
        assertEquals("自定义模板：部署方式", result);
    }

    @Test
    void buildSystemPromptShouldUseDefaultTemplateWhenMultipleIntentsHaveCustomTemplates() {
        templateLoader.templates.put("answer-chat-mcp-kb-mixed.st", "混合默认：{mcpContext}|{kbContext}");

        String result = promptService.buildSystemPrompt(PromptContext.builder()
                .question("部署并查询流水线")
                .kbContext("<documents>部署文档</documents>")
                .mcpContext("<tool-data>流水线状态</tool-data>")
                .kbIntents(List.of(score("kb-dev", "KB", "custom/devops.st")))
                .mcpIntents(List.of(score("mcp-ci", "MCP", "custom/ci.st")))
                .build());

        assertEquals("answer-chat-mcp-kb-mixed.st", templateLoader.loadedPaths.get(0));
        assertEquals("混合默认：<tool-data>流水线状态</tool-data>|<documents>部署文档</documents>", result);
    }

    @Test
    void buildStructuredMessagesShouldAppendHistoryAndOneUserMessageWithEvidenceAndQuestions() {
        templateLoader.templates.put("answer-chat-mcp-kb-mixed.st", "混合系统提示：{question}");
        templateLoader.sections.put("context-format.st#mcp-evidence", "<tool-data>\n{mcpContext}\n</tool-data>");
        templateLoader.sections.put("context-format.st#kb-evidence", "<documents>\n{kbContext}\n</documents>");
        templateLoader.sections.put("context-format.st#multi-questions", "<questions>\n{questions}\n</questions>");

        PromptContext context = PromptContext.builder()
                .question("部署和流水线")
                .mcpContext("流水线状态：成功")
                .kbContext("部署步骤：Docker Compose")
                .kbIntents(List.of(score("kb-dev", "KB", null)))
                .mcpIntents(List.of(score("mcp-ci", "MCP", null)))
                .intentChunks(Map.of("kb-dev", List.of(new RetrievedChunk("c1", "部署步骤", 0.9f))))
                .build();

        List<ChatMessage> messages = promptService.buildStructuredMessages(
                context,
                List.of(ChatMessage.system("历史摘要"), ChatMessage.user("上一问")),
                "部署和流水线",
                List.of("后端怎么部署", "流水线状态是什么")
        );

        assertEquals(4, messages.size());
        assertEquals(ChatMessage.Role.SYSTEM, messages.get(0).getRole());
        assertEquals("混合系统提示：部署和流水线", messages.get(0).getContent());
        assertEquals("历史摘要", messages.get(1).getContent());
        assertEquals("上一问", messages.get(2).getContent());
        assertEquals(ChatMessage.Role.USER, messages.get(3).getRole());
        assertTrue(messages.get(3).getContent().contains("<tool-data>"));
        assertTrue(messages.get(3).getContent().contains("<documents>"));
        assertTrue(messages.get(3).getContent().contains("1. 后端怎么部署"));
        assertTrue(messages.get(3).getContent().contains("2. 流水线状态是什么"));
    }

    @Test
    void buildStructuredMessagesShouldRenderSingleQuestionWhenNoEvidence() {
        templateLoader.templates.put("answer-chat-empty.st", "空上下文：{question}");
        templateLoader.sections.put("context-format.st#single-question", "<question>{question}</question>");

        List<ChatMessage> messages = promptService.buildStructuredMessages(
                PromptContext.builder().question("你是谁").build(),
                List.of(),
                "你是谁",
                List.of("你是谁")
        );

        assertEquals(2, messages.size());
        assertEquals("空上下文：你是谁", messages.get(0).getContent());
        assertEquals("<question>你是谁</question>", messages.get(1).getContent());
    }

    private NodeScore score(String id, String kind, String promptTemplate) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(id);
        node.setKind(kind);
        node.setPromptTemplate(promptTemplate);
        return new NodeScore(node, 0.9);
    }

    private static final class StubPromptTemplateLoader implements PromptTemplateLoader {

        private final Map<String, String> templates = new LinkedHashMap<>();
        private final Map<String, String> sections = new LinkedHashMap<>();
        private final List<String> loadedPaths = new java.util.ArrayList<>();

        @Override
        public String load(String path) {
            loadedPaths.add(path);
            return templates.get(path);
        }

        @Override
        public String renderSection(String path, String section, Map<String, Object> slots) {
            String template = sections.get(path + "#" + section);
            return render(template, slots);
        }

        private String render(String template, Map<String, Object> slots) {
            if (template == null) {
                return "";
            }
            String result = template;
            for (Map.Entry<String, Object> entry : slots.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            return result;
        }
    }
}
