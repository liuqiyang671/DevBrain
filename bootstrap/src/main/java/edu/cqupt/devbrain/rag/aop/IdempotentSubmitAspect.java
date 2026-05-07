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
 * Redis SET NX idempotent guard for duplicate chat submissions.
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

    private String requestFingerprint(ProceedingJoinPoint joinPoint) {
        String question = ChatAopSupport.requestParamOrArg(joinPoint, "question", 0);
        String conversationId = ChatAopSupport.requestParamOrArg(joinPoint, "conversationId", 1);
        if (!StringUtils.hasText(conversationId)) {
            conversationId = "new";
        }
        return ChatAopSupport.md5(question + "|" + conversationId);
    }
}
