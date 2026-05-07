package edu.cqupt.devbrain.rag.aop;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentSubmitAspectTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RBucket<Object> bucket = mock(RBucket.class);
    private final IdempotentSubmitAspect aspect = new IdempotentSubmitAspect(redissonClient);

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void aroundShouldSetNxByQuestionAndConversationId() throws Throwable {
        UserContext.set(new LoginUser("user-1", "alice", null, null, null, Set.of(), Set.of()));
        ProceedingJoinPoint joinPoint = joinPoint("chat", "后端咋部署", "conv-1");
        when(redissonClient.getBucket(org.mockito.ArgumentMatchers.startsWith("rag:chat:idempotent:user-1:")))
                .thenReturn(bucket);
        when(bucket.setIfAbsent("1", Duration.ofSeconds(10))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotation("chat"));

        assertEquals("ok", result);
        verify(bucket).setIfAbsent("1", Duration.ofSeconds(10));
        verify(joinPoint).proceed();
    }

    @Test
    void aroundShouldRejectDuplicateRequest() throws Throwable {
        UserContext.set(new LoginUser("user-1", "alice", null, null, null, Set.of(), Set.of()));
        ProceedingJoinPoint joinPoint = joinPoint("chat", "后端咋部署", "conv-1");
        when(redissonClient.getBucket(org.mockito.ArgumentMatchers.startsWith("rag:chat:idempotent:user-1:")))
                .thenReturn(bucket);
        when(bucket.setIfAbsent("1", Duration.ofSeconds(10))).thenReturn(false);

        assertThrows(ClientException.class, () -> aspect.around(joinPoint, annotation("chat")));

        verify(joinPoint, never()).proceed();
    }

    private IdempotentSubmit annotation(String methodName) throws NoSuchMethodException {
        return Target.class.getDeclaredMethod(methodName, String.class, String.class)
                .getAnnotation(IdempotentSubmit.class);
    }

    private ProceedingJoinPoint joinPoint(String methodName, String question, String conversationId)
            throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new Target());
        when(joinPoint.getArgs()).thenReturn(new Object[]{question, conversationId});
        when(signature.getName()).thenReturn(methodName);
        when(signature.getParameterTypes()).thenReturn(new Class<?>[]{String.class, String.class});
        when(signature.getMethod()).thenReturn(Target.class.getDeclaredMethod(methodName, String.class, String.class));
        return joinPoint;
    }

    private static class Target {

        @IdempotentSubmit(expireSeconds = 10)
        String chat(String question, String conversationId) {
            return "ok";
        }
    }
}
