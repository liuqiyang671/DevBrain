package edu.cqupt.devbrain.commerce.guide.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 导购回答生成配置。
 * <p>
 * 绑定配置前缀 {@code commerce.guide.answer}，控制 LLM 回答生成的行为：
 * <ul>
 *   <li><b>llmEnabled</b> — 是否启用 LLM 生成回答（关闭后使用模板生成）</li>
 *   <li><b>promptLocation</b> — 回答生成的 System Prompt 模板位置</li>
 *   <li><b>temperature</b> — LLM 温度参数（0.25 适合生成自然但稳定的回答）</li>
 *   <li><b>maxTokens</b> — 单次生成的最大 token 数</li>
 *   <li><b>timeoutMillis</b> — LLM 调用超时时间</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@ConfigurationProperties(prefix = "commerce.guide.answer")
public class GuideAnswerProperties {

    /** 是否启用 LLM 生成回答。关闭后使用模板拼接生成回答 */
    private boolean llmEnabled = true;

    /** 回答生成的 System Prompt 模板位置 */
    private String promptLocation = "classpath:prompts/guide/final-answer-system.md";

    /** LLM 温度参数，0.25 适合生成自然但稳定的回答 */
    private double temperature = 0.25D;

    /** 单次生成的最大 token 数 */
    private int maxTokens = 700;

    /** LLM 调用超时时间（毫秒） */
    private long timeoutMillis = 12_000L;
}
