package edu.cqupt.devbrain.ingestion.domain.result;

import lombok.Builder;
import lombok.Data;

/**
 * 单个摄入节点的执行结果。
 */
@Data
@Builder
public class NodeResult {

    /**
     * 节点是否执行成功。
     */
    private boolean success;

    /**
     * 流水线是否继续执行后续节点。
     */
    private boolean shouldContinue;

    /**
     * 成功或跳过时的说明消息。
     */
    private String message;

    /**
     * 失败原因。
     */
    private String error;

    /**
     * 创建成功且继续执行的结果。
     *
     * @return 节点执行结果
     */
    public static NodeResult ok() {
        return ok(null);
    }

    /**
     * 创建带消息的成功且继续执行结果。
     *
     * @param message 成功消息
     * @return 节点执行结果
     */
    public static NodeResult ok(String message) {
        return NodeResult.builder()
                .success(true)
                .shouldContinue(true)
                .message(message)
                .build();
    }

    /**
     * 创建跳过当前节点但继续执行后续节点的结果。
     *
     * @param reason 跳过原因
     * @return 节点执行结果
     */
    public static NodeResult skip(String reason) {
        return NodeResult.builder()
                .success(true)
                .shouldContinue(true)
                .message(reason)
                .build();
    }

    /**
     * 创建失败且终止执行的结果。
     *
     * @param error 失败原因
     * @return 节点执行结果
     */
    public static NodeResult fail(String error) {
        return NodeResult.builder()
                .success(false)
                .shouldContinue(false)
                .error(error)
                .build();
    }

    /**
     * 创建成功但终止后续执行的结果。
     *
     * @param reason 终止原因
     * @return 节点执行结果
     */
    public static NodeResult terminate(String reason) {
        return NodeResult.builder()
                .success(true)
                .shouldContinue(false)
                .message(reason)
                .build();
    }
}
