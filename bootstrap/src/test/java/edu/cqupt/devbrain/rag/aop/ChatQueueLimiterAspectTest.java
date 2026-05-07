package edu.cqupt.devbrain.rag.aop;

import edu.cqupt.devbrain.framework.exception.ClientException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatQueueLimiterAspectTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RSemaphore semaphore = mock(RSemaphore.class);
    private final ChatQueueLimiterAspect aspect = new ChatQueueLimiterAspect(redissonClient);

    @Test
    void aroundShouldAcquireSemaphoreAndReleaseWhenSseCompletes() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("stream");
        CapturingEmitter emitter = new CapturingEmitter();
        when(redissonClient.getSemaphore("rag:chat:queue:stream")).thenReturn(semaphore);
        when(semaphore.tryAcquire(50, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(emitter);

        Object result = aspect.around(joinPoint, annotation("stream"));

        assertSame(emitter, result);
        verify(semaphore).trySetPermits(10);
        emitter.runCompletion();
        verify(semaphore).release();
    }

    @Test
    void aroundShouldRejectWhenNoPermitIsAvailable() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("stream");
        when(redissonClient.getSemaphore("rag:chat:queue:stream")).thenReturn(semaphore);
        when(semaphore.tryAcquire(50, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThrows(ClientException.class, () -> aspect.around(joinPoint, annotation("stream")));

        verify(joinPoint, never()).proceed();
        verify(semaphore, never()).release();
    }

    private ChatQueueLimiter annotation(String methodName) throws NoSuchMethodException {
        return Target.class.getDeclaredMethod(methodName).getAnnotation(ChatQueueLimiter.class);
    }

    private ProceedingJoinPoint joinPoint(String methodName) throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new Target());
        when(signature.getName()).thenReturn(methodName);
        when(signature.getParameterTypes()).thenReturn(new Class<?>[0]);
        when(signature.getMethod()).thenReturn(Target.class.getDeclaredMethod(methodName));
        return joinPoint;
    }

    private static class Target {

        @ChatQueueLimiter(maxConcurrent = 10, waitMillis = 50)
        SseEmitter stream() {
            return new SseEmitter();
        }
    }

    private static final class CapturingEmitter extends SseEmitter {

        private Runnable completion;

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completion = callback;
        }

        void runCompletion() {
            completion.run();
        }
    }
}
