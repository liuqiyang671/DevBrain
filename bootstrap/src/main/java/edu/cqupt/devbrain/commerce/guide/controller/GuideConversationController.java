package edu.cqupt.devbrain.commerce.guide.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.commerce.guide.dto.resp.AgentMemoryResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideMessageResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideRecommendationResp;
import edu.cqupt.devbrain.commerce.guide.dto.resp.GuideSessionResp;
import edu.cqupt.devbrain.commerce.guide.service.GuideConversationService;
import edu.cqupt.devbrain.commerce.guide.service.GuideMemoryService;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 导购会话与长期记忆查询接口。
 * <p>
 * 提供会话管理和记忆管理的 REST API：
 * <ul>
 *   <li><b>会话管理</b> — 分页查询、详情、消息列表、推荐列表、归档、恢复、删除</li>
 *   <li><b>记忆管理</b> — 查询用户长期记忆、删除记忆</li>
 * </ul>
 * <p>
 * 所有接口都通过 {@link UserContext} 获取当前用户身份，
 * 并通过 {@link GuideConversationService} 和 {@link GuideMemoryService} 操作数据。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideConversationService 会话服务
 * @see GuideMemoryService 记忆服务
 */
@RestController
@RequiredArgsConstructor
public class GuideConversationController {

    /** 会话服务 */
    private final GuideConversationService conversationService;

    /** 记忆服务 */
    private final GuideMemoryService memoryService;

    @GetMapping("/commerce/guide/sessions")
    public Result<IPage<GuideSessionResp>> pageSessions(@RequestParam(defaultValue = "1") long pageNo,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        return Results.success(conversationService.pageSessions(UserContext.requireUser().userId(), pageNo, pageSize));
    }

    @GetMapping("/commerce/guide/sessions/{sessionId}")
    public Result<GuideSessionResp> detail(@PathVariable String sessionId) {
        return Results.success(conversationService.detail(sessionId, UserContext.requireUser().userId()));
    }

    @GetMapping("/commerce/guide/sessions/{sessionId}/messages")
    public Result<List<GuideMessageResp>> messages(@PathVariable String sessionId) {
        return Results.success(conversationService.listMessages(sessionId, UserContext.requireUser().userId()));
    }

    @GetMapping("/commerce/guide/sessions/{sessionId}/recommendations")
    public Result<List<GuideRecommendationResp>> recommendations(@PathVariable String sessionId) {
        return Results.success(conversationService.listRecommendations(sessionId, UserContext.requireUser().userId()));
    }

    @PostMapping("/commerce/guide/sessions/{sessionId}/archive")
    public Result<Void> archiveSession(@PathVariable String sessionId) {
        conversationService.archiveSession(sessionId, UserContext.requireUser().userId());
        return Results.success();
    }

    @PostMapping("/commerce/guide/sessions/{sessionId}/restore")
    public Result<Void> restoreSession(@PathVariable String sessionId) {
        conversationService.restoreSession(sessionId, UserContext.requireUser().userId());
        return Results.success();
    }

    @DeleteMapping("/commerce/guide/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        conversationService.deleteSession(sessionId, UserContext.requireUser().userId());
        return Results.success();
    }

    @GetMapping("/commerce/guide/memory")
    public Result<List<AgentMemoryResp>> memory() {
        String userId = UserContext.requireUser().userId();
        return Results.success(memoryService.listByUser(userId).stream()
                .map(memory -> new AgentMemoryResp(memory.getId(), memory.getMemoryType(), memory.getMemoryKey(),
                        memory.getMemoryValue(), memory.getConfidence(), memory.getSource(), memory.getLastUsedTime(),
                        memory.getCreateTime(), memory.getUpdateTime()))
                .toList());
    }

    @DeleteMapping("/commerce/guide/memory/{memoryId}")
    public Result<Void> deleteMemory(@PathVariable String memoryId) {
        memoryService.delete(UserContext.requireUser().userId(), memoryId);
        return Results.success();
    }
}
