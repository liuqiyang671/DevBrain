package edu.cqupt.devbrain.rag.aop;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRateLimitAspectTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RAtomicLong counter = mock(RAtomicLong.class);
    private final ChatRateLimitAspect aspect = new ChatRateLimitAspect(redissonClient);

    @AfterEach
    void clearContext() {
        UserContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aroundShouldUseUserContextAndSetExpireForFirstRequest() throws Throwable {
        UserContext.set(new LoginUser("user-1", "alice", null, null, null, Set.of(), Set.of()));
        ProceedingJoinPoint joinPoint = joinPoint("limited");
        when(redissonClient.getAtomicLong("rag:chat:rate:user-1:limited")).thenReturn(counter);
        when(counter.incrementAndGet()).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotation("limited"));

        assertEquals("ok", result);
        verify(counter).expire(Duration.ofSeconds(30));
        verify(joinPoint).proceed();
    }

    @Test
    void aroundShouldFallbackToRequestHeaderWhenUserContextIsMissing() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint joinPoint = joinPoint("limited");
        when(redissonClient.getAtomicLong("rag:chat:rate:header-user:limited")).thenReturn(counter);
        when(counter.incrementAndGet()).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotation("limited"));

        assertEquals("ok", result);
    }

    @Test
    void aroundShouldRejectWhenLimitExceeded() throws Throwable {
        UserContext.set(new LoginUser("user-1", "alice", null, null, null, Set.of(), Set.of()));
        ProceedingJoinPoint joinPoint = joinPoint("limited");
        when(redissonClient.getAtomicLong("rag:chat:rate:user-1:limited")).thenReturn(counter);
        when(counter.incrementAndGet()).thenReturn(6L);

        assertThrows(ClientException.class, () -> aspect.around(joinPoint, annotation("limited")));

        verify(joinPoint, never()).proceed();
    }

    private ChatRateLimit annotation(String methodName) throws NoSuchMethodException {
        return Target.class.getDeclaredMethod(methodName).getAnnotation(ChatRateLimit.class);
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

        @ChatRateLimit(limit = 5, windowSeconds = 30)
        String limited() {
            return "ok";
        }
    }
}
