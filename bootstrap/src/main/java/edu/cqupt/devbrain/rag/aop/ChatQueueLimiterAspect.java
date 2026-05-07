package edu.cqupt.devbrain.rag.aop;

import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Redis 信号量的 Chat 流式接口并发控制切面。
 * <p>
 * 当请求方法返回 SseEmitter 时，信号量会在 SSE 流结束后释放，而非方法返回时释放。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public class ChatQueueLimiterAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(chatQueueLimiter)")
    public Object around(ProceedingJoinPoint joinPoint, ChatQueueLimiter chatQueueLimiter) throws Throwable {
        int permits = Math.max(1, chatQueueLimiter.maxConcurrent());
        String queueKey = StringUtils.hasText(chatQueueLimiter.key())
                ? chatQueueLimiter.key()
                : ChatAopSupport.methodKey(joinPoint);
        RSemaphore semaphore = redissonClient.getSemaphore("rag:chat:queue:" + queueKey);
        semaphore.trySetPermits(permits);
        boolean acquired = acquire(semaphore, chatQueueLimiter.waitMillis());
        if (!acquired) {
            throw new ClientException("当前问答请求较多，请稍后再试");
        }
        boolean releaseOnExit = true;
        try {
            Object result = joinPoint.proceed();
            if (result instanceof SseEmitter emitter) {
                releaseOnExit = false;
                releaseWhenSseEnds(emitter, semaphore);
            }
            return result;
        } finally {
            if (releaseOnExit) {
                semaphore.release();
            }
        }
    }

    /**
     * 尝试获取信号量许可。
     */
    private boolean acquire(RSemaphore semaphore, long waitMillis) throws InterruptedException {
        if (waitMillis > 0) {
            return semaphore.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        }
        return semaphore.tryAcquire();
    }

    /**
     * SSE 流结束时释放信号量，通过 AtomicBoolean 保证只释放一次。
     */
    private void releaseWhenSseEnds(SseEmitter emitter, RSemaphore semaphore) {
        AtomicBoolean released = new AtomicBoolean(false);
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        };
        emitter.onCompletion(release);
        emitter.onTimeout(release);
        emitter.onError(throwable -> release.run());
    }
}
