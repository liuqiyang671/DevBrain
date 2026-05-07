package edu.cqupt.devbrain.rag.core.intent;

import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.rag.core.intent.dao.mapper.IntentNodeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultIntentClassifierTest {

    private final IntentNodeMapper intentNodeMapper = mock(IntentNodeMapper.class);
    private final LLMService llmService = mock(LLMService.class);
    private final DefaultIntentClassifier classifier = new DefaultIntentClassifier(intentNodeMapper, llmService);

    @Test
    void classifyTargetsShouldBuildTreePromptParseScoresAndSortDescending() {
        when(intentNodeMapper.selectList(null)).thenReturn(List.of(
                node("hr", null, "HR", "KB", "人事制度", null, null),
                node("attendance", "hr", "考勤", "KB", "请假、加班和考勤规则", "kb_hr_attendance", null),
                node("recruit", "hr", "招聘", "KB", "招聘流程", "kb_hr_recruit", null),
                node("it", null, "IT", "KB", "信息化服务", null, null),
                node("vpn", "it", "VPN", "KB", "虚拟专用网络连接", "kb_it_vpn", null)
        ));
        when(llmService.chat(anyString())).thenReturn("""
                {"scores":[{"id":"attendance","score":0.91},{"id":"vpn","score":0.22},{"id":"missing","score":0.99}]}
                """);

        List<NodeScore> result = classifier.classifyTargets("怎么请假");

        assertEquals(2, result.size());
        assertEquals("attendance", result.get(0).getNode().getId());
        assertEquals(0.91, result.get(0).getScore(), 0.0001);
        assertEquals("vpn", result.get(1).getNode().getId());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("hr | HR | 人事制度"));
        assertTrue(prompt.contains("attendance | 考勤 | 请假、加班和考勤规则"));
        assertTrue(prompt.contains("vpn | VPN | 虚拟专用网络连接"));
    }

    private IntentNode node(String id, String parentId, String name, String kind,
                            String description, String collectionName, String mcpToolId) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setName(name);
        node.setKind(kind);
        node.setDescription(description);
        node.setCollectionName(collectionName);
        node.setMcpToolId(mcpToolId);
        return node;
    }
}
