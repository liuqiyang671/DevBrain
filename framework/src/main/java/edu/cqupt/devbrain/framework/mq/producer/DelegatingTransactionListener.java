package edu.cqupt.devbrain.framework.mq.producer;

import edu.cqupt.devbrain.framework.mq.MessageWrapper;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * 通用的 RocketMQ 事务消息监听器，统一处理事务消息的本地事务执行和事务回查。
 * <p>
 * 职责：
 * <ul>
 *     <li>接收 half 消息后，根据 txId 查找并执行对应的本地事务逻辑（per-message）</li>
 *     <li>在 Broker 发起事务回查时，根据 topic 查找对应的 {@link TransactionChecker} 判断事务状态</li>
 *     <li>本地事务在 Spring 事务模板中执行，确保与数据库操作的原子性</li>
 *     <li>通过 {@link #registerChecker(String, TransactionChecker)} 按 topic 注册回查处理器</li>
 * </ul>
 */
@RocketMQTransactionListener
public class DelegatingTransactionListener implements RocketMQLocalTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(DelegatingTransactionListener.class);

    static final String HEADER_TX_ID = "TRANSACTION_CONTEXT_ID";
    static final String HEADER_TOPIC = "TRANSACTION_TOPIC";

    /**
     * 本地事务执行逻辑，per-message，仅当前实例有效
     */
    private final ConcurrentMap<String, Consumer<Object>> localTransactionMap = new ConcurrentHashMap<>();

    /**
     * 事务回查逻辑，per-topic，所有实例共享（Spring Bean 注册）
     */
    private final ConcurrentMap<String, TransactionChecker> checkerMap = new ConcurrentHashMap<>();

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 注册单条消息的本地事务执行逻辑，执行完成后自动移除。
     */
    public void registerLocalTransaction(String txId, Consumer<Object> localTransaction) {
        localTransactionMap.put(txId, localTransaction);
    }

    /**
     * 按 topic 注册事务回查处理器，用于 Broker 回查时判断本地事务状态。
     */
    public void registerChecker(String topic, TransactionChecker checker) {
        checkerMap.put(topic, checker);
    }

    /**
     * 执行本地事务：从消息头中提取 txId，查找对应的本地事务逻辑并在 Spring 事务中执行。
     * 执行成功返回 COMMIT，失败返回 ROLLBACK。
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        String txId = (String) message.getHeaders().get(HEADER_TX_ID);
        Consumer<Object> localTransaction = txId != null ? localTransactionMap.remove(txId) : null;
        if (localTransaction == null) {
            log.error("[事务消息] 未找到本地事务逻辑, txId={}", txId);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> localTransaction.accept(arg));
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("[事务消息] 本地事务执行失败, txId={}", txId, e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 事务回查：根据消息头中的 topic 查找对应的 checker，委托其判断本地事务是否已提交。
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        String topic = (String) message.getHeaders().get(HEADER_TOPIC);
        TransactionChecker checker = topic != null ? checkerMap.get(topic) : null;
        if (checker == null) {
            log.warn("[事务消息] 回查时未找到 topic={} 对应的 checker, 默认 ROLLBACK", topic);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            MessageWrapper<?> wrapper = (MessageWrapper<?>) message.getPayload();
            boolean committed = checker.check(wrapper);
            RocketMQLocalTransactionState state = committed
                    ? RocketMQLocalTransactionState.COMMIT
                    : RocketMQLocalTransactionState.ROLLBACK;
            log.info("[事务消息] 回查结果: topic={}, state={}", topic, state);
            return state;
        } catch (Exception e) {
            log.error("[事务消息] 回查异常, topic={}", topic, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
