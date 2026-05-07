package edu.cqupt.devbrain.rag.aop;

import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed fixed-window rate limiter for chat endpoints.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public class ChatRateLimitAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(chatRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, ChatRateLimit chatRateLimit) throws Throwable {
        if (chatRateLimit.limit() <= 0 || chatRateLimit.windowSeconds() <= 0) {
            return joinPoint.proceed();
        }
        String key = "rag:chat:rate:" + ChatAopSupport.userId() + ":" + ChatAopSupport.methodKey(joinPoint);
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            long count = counter.incrementAndGet();
            counter.expire(Duration.ofSeconds(chatRateLimit.windowSeconds()));
            if (count > chatRateLimit.limit()) {
                throw new ClientException("提问过于频繁，请稍后再试");
            }
        } catch (ClientException ex) {
            throw ex;
        } catch (Throwable ex) {
            log.warn("RAG chat rate limit check failed, key={}", key, ex);
        }
        return joinPoint.proceed();
    }
}
