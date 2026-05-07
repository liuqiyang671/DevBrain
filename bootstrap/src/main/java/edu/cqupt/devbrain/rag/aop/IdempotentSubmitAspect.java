package edu.cqupt.devbrain.rag.aop;

import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 基于 Redis SET NX 的 Chat 提交幂等保护切面。
 * <p>
 * 通过用户 ID + 请求指纹（问题 + 会话 ID 的 MD5）生成幂等 key，
 * 在过期时间内重复请求会被拒绝。
 */
@Aspect
@Component("ragIdempotentSubmitAspect")
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public class IdempotentSubmitAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(idempotentSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) throws Throwable {
        int expireSeconds = Math.max(1, idempotentSubmit.expireSeconds());
        String key = "rag:chat:idempotent:" + ChatAopSupport.userId() + ":" + requestFingerprint(joinPoint);
        org.redisson.api.RBucket<Object> bucket = redissonClient.getBucket(key);
        if (!bucket.setIfAbsent("1", Duration.ofSeconds(expireSeconds))) {
            throw new ClientException(idempotentSubmit.message());
        }
        return joinPoint.proceed();
    }

    /**
     * 生成请求指纹：对 question 和 conversationId 拼接后取 MD5。
     */
    private String requestFingerprint(ProceedingJoinPoint joinPoint) {
        String question = ChatAopSupport.requestParamOrArg(joinPoint, "question", 0);
        String conversationId = ChatAopSupport.requestParamOrArg(joinPoint, "conversationId", 1);
        if (!StringUtils.hasText(conversationId)) {
            conversationId = "new";
        }
        return ChatAopSupport.md5(question + "|" + conversationId);
    }
}
