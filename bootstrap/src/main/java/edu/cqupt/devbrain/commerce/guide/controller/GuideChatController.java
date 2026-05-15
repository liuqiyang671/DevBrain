package edu.cqupt.devbrain.commerce.guide.controller;

import edu.cqupt.devbrain.commerce.guide.dto.req.GuideChatReq;
import edu.cqupt.devbrain.commerce.guide.service.GuideChatService;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.rag.aop.ChatQueueLimiter;
import edu.cqupt.devbrain.rag.aop.ChatRateLimit;
import edu.cqupt.devbrain.rag.aop.IdempotentSubmit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 导购对话控制器。
 * <p>
 * 提供导购对话的RESTful API，包括流式对话和停止对话功能。
 * 这是导购系统的前端入口，负责接收用户的导购请求并返回流式响应。
 * <p>
 * 主要接口：
 * <ul>
 *   <li><b>POST /commerce/guide/chat/stream</b> - 流式导购对话（SSE）</li>
 *   <li><b>POST /commerce/guide/chat/stop</b> - 停止指定会话的导购对话</li>
 * </ul>
 * <p>
 * 安全防护：
 * <ul>
 *   <li><b>限流</b>：每个用户60秒内最多5次请求（@ChatRateLimit）</li>
 *   <li><b>并发控制</b>：最多10个并发请求（@ChatQueueLimiter）</li>
 *   <li><b>幂等提交</b>：10秒内相同请求视为重复（@IdempotentSubmit）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@RestController
@RequiredArgsConstructor
public class GuideChatController {

    /** 导购对话服务，负责处理导购对话的核心逻辑 */
    private final GuideChatService guideChatService;

    /**
     * 流式导购对话（SSE）。
     * <p>
     * 该接口使用Server-Sent Events（SSE）实现流式响应，支持实时推送导购过程中的各类事件。
     * 事件类型包括：
     * <ul>
     *   <li>SESSION - 会话信息</li>
     *   <li>SEARCHING - 正在检索状态</li>
     *   <li>INTENT - 意图识别结果</li>
     *   <li>TRACE - 决策轨迹</li>
     *   <li>CLARIFICATION - 追问澄清</li>
     *   <li>PRODUCT_CARD - 商品卡片</li>
     *   <li>CITATION - 引用证据</li>
     *   <li>ANSWER - 回答增量</li>
     *   <li>ANSWER_DONE - 回答完成</li>
     *   <li>ERROR - 错误信息</li>
     * </ul>
     *
     * @param request 导购聊天请求，包含消息内容、会话ID、图片ID等
     * @return SSE发射器，客户端通过它接收流式事件
     */
    @PostMapping(value = "/commerce/guide/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @ChatRateLimit(limit = 5, windowSeconds = 60)
    @ChatQueueLimiter(maxConcurrent = 10, waitMillis = 0)
    @IdempotentSubmit(expireSeconds = 10)
    public SseEmitter stream(@RequestBody @Valid GuideChatReq request) {
        return guideChatService.streamChat(request);
    }

    /**
     * 停止指定会话的导购对话。
     * <p>
     * 通过会话ID停止正在执行的导购任务。
     * 停止操作是异步的，任务会在下一个检查点被中断。
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @PostMapping("/commerce/guide/chat/stop")
    public Result<Void> stop(@RequestParam String sessionId) {
        guideChatService.stop(sessionId);
        return Results.success();
    }

}
