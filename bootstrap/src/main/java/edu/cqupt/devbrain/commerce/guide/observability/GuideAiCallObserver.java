package edu.cqupt.devbrain.commerce.guide.observability;

import edu.cqupt.devbrain.infra.ai.gateway.structured.AiCallObserver;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiCallRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 调用观察者 — 将 infra-ai 层的模型调用记录写入导购 Agent 的 LLM 调用账本。
 * <p>
 * 实现 {@link AiCallObserver} 接口，在每次 LLM 调用完成时回调 {@link #onComplete}，
 * 将调用信息（provider、model、token 用量、耗时等）通过 {@link GuideAgentObservationService}
 * 持久化到 t_llm_call_log 表。
 * <p>
 * 用于：
 * <ul>
 *   <li><b>成本监控</b> — 统计 token 用量和调用次数</li>
 *   <li><b>性能分析</b> — 追踪 LLM 调用耗时</li>
 *   <li><b>调试</b> — 查看 Prompt 和响应的摘要</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see AiCallObserver infra-ai 层的调用观察者接口
 * @see GuideAgentObservationService 观测数据持久化服务
 */
@Component
public class GuideAiCallObserver implements AiCallObserver {

    /** 观测数据持久化服务 */
    private final GuideAgentObservationService observationService;

    public GuideAiCallObserver(GuideAgentObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * LLM 调用完成回调。
     * <p>
     * 将调用记录写入 LLM 调用账本。忽略 runId 为空的记录。
     */
        if (record == null || !StringUtils.hasText(record.runId())) {
            return;
        }
        observationService.recordLlmCall(
                context(record),
                record.stepId(),
                record.businessScene(),
                record.stream(),
                record.durationMs(),
                record.status(),
                record.errorMessage(),
                record.prompt(),
                record.response(),
                metadata(record)
        );
    }

    private GuideAgentRunContext context(AiCallRecord record) {
        return new GuideAgentRunContext(
                record.runId(),
                null,
                null,
                null,
                null,
                null,
                CancellationToken.none(),
                GuideAgentStepListener.NOOP
        );
    }

    private Map<String, Object> metadata(AiCallRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(record.metadata());
        metadata.put("callId", record.callId());
        putIfPresent(metadata, "provider", record.provider());
        putIfPresent(metadata, "model", record.model());
        putIfPresent(metadata, "temperature", record.temperature());
        putIfPresent(metadata, "maxTokens", record.maxTokens());
        putIfPresent(metadata, "inputTokens", record.inputTokens());
        putIfPresent(metadata, "outputTokens", record.outputTokens());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
