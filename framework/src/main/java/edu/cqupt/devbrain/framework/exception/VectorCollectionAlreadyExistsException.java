package edu.cqupt.devbrain.framework.exception;

/**
 * 向量集合已存在异常。
 * <p>当尝试创建一个已存在的向量集合（Collection）时抛出此异常。
 * 不同向量存储后端对此的处理策略不同：PgVector 的 ensure 逻辑是幂等的，不会抛出该异常；
 * Milvus 等需要显式创建 collection 的后端可用它区分”集合已存在”和其他远程调用失败，
 * 以便上层进行幂等重试或忽略处理。</p>
 */
public class VectorCollectionAlreadyExistsException extends RuntimeException {

    /**
     * 基于消息构造向量集合已存在异常
     *
     * @param message 错误消息
     */
    public VectorCollectionAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * 基于消息和原始异常构造向量集合已存在异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public VectorCollectionAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
