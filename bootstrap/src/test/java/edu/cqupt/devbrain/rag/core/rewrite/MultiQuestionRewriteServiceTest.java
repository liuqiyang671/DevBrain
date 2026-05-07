package edu.cqupt.devbrain.rag.core.rewrite;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiQuestionRewriteServiceTest {

    private final LLMService llmService = mock(LLMService.class);
    private final QueryRewriteProperties properties = new QueryRewriteProperties();
    private final QueryTermMappingService termMappingService = new ConfigQueryTermMappingService(properties);
    private final MultiQuestionRewriteService rewriteService =
            new MultiQuestionRewriteService(llmService, termMappingService, properties);

    @Test
    void rewriteWithSplitShouldNormalizeTermsBuildPromptAndParseJson() {
        properties.setTermMappings(Map.of("虚拟专用网络", List.of("VPN", "vpn", "翻墙工具")));
        when(llmService.chat(anyString())).thenReturn("""
                {"rewritten": "虚拟专用网络连接配置流程", "subQuestions": ["虚拟专用网络连接配置流程"]}
                """);

        RewriteResult result = rewriteService.rewriteWithSplit("VPN 咋连？", List.of(
                ChatMessage.user("校园网怎么登录？"),
                ChatMessage.assistant("请使用统一身份认证登录。")
        ));

        assertEquals("虚拟专用网络连接配置流程", result.rewrittenQuestion());
        assertEquals(List.of("虚拟专用网络连接配置流程"), result.subQuestions());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("虚拟专用网络"));
        assertTrue(prompt.contains("校园网怎么登录？"));
        assertTrue(prompt.contains("请使用统一身份认证登录。"));
    }

    @Test
    void rewriteWithSplitShouldUseOnlyRecentTwoTurnsAsHistoryContext() {
        when(llmService.chat(anyString())).thenReturn("""
                {"rewritten": "Redis 使用方式", "subQuestions": ["Redis 使用方式"]}
                """);

        rewriteService.rewriteWithSplit("它怎么用？", List.of(
                ChatMessage.user("第一轮用户"),
                ChatMessage.assistant("第一轮助手"),
                ChatMessage.user("第二轮用户"),
                ChatMessage.assistant("第二轮助手"),
                ChatMessage.user("Redis 是什么？"),
                ChatMessage.assistant("Redis 是内存数据库。")
        ));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("第二轮用户"));
        assertTrue(prompt.contains("第二轮助手"));
        assertTrue(prompt.contains("Redis 是什么？"));
        assertTrue(prompt.contains("Redis 是内存数据库。"));
        assertTrue(!prompt.contains("第一轮用户"));
        assertTrue(!prompt.contains("第一轮助手"));
    }

    @Test
    void rewriteWithSplitShouldFallbackToRuleSplitWhenJsonParsingFails() {
        when(llmService.chat(anyString())).thenReturn("not-json");

        RewriteResult result = rewriteService.rewriteWithSplit("招聘流程是啥？薪资怎么算？", List.of());

        assertEquals("招聘流程是啥", result.rewrittenQuestion());
        assertEquals(List.of("招聘流程是啥", "薪资怎么算"), result.subQuestions());
    }
}
