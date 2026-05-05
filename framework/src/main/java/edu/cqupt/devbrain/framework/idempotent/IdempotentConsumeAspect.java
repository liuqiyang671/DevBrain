package edu.cqupt.devbrain.framework.idempotent;

import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 防重复消费 AOP 切面
 * <p>
 * 拦截标记了 {@link IdempotentConsume} 注解的方法，通过 Redis + Lua 脚本实现消息消费的幂等控制。
 * </p>
 * <p>
 * 处理流程：
 * <ol>
 *   <li>根据注解配置（前缀 + SpEL 表达式）生成唯一消费标识 Key</li>
 *   <li>通过 Lua 脚本原子性地检查并设置消费状态为 CONSUMING（消费中）</li>
 *   <li>如果 Key 已存在且状态为 CONSUMING，抛出异常（触发延迟重试）</li>
 *   <li>如果 Key 已存在且状态为 CONSUMED，直接跳过（消息已成功消费）</li>
 *   <li>执行目标方法，成功后将状态更新为 CONSUMED；失败则删除 Key 允许重新消费</li>
 * </ol>
 * </p>
 * <p>
 * 该组件仅在 Spring 容器中存在 {@link StringRedisTemplate} Bean 时生效。
 * </p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public final class IdempotentConsumeAspect {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumeAspect.class);

    /** Redis 操作模板，用于执行 Lua 脚本和管理消费状态 */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 脚本：原子性地检查并设置消费状态
     * <p>
     * 使用 Redis 的 SET NX GET PX 命令：
     * <ul>
     *   <li>NX - 仅在 Key 不存在时设置，保证原子性</li>
     *   <li>GET - 返回 Key 的旧值（如果存在）</li>
     *   <li>PX - 设置过期时间（毫秒）</li>
     * </ul>
     * 返回值：
     * <ul>
     *   <li>nil - Key 不存在，首次消费，设置成功</li>
     *   <li>"0" - Key 存在且值为 CONSUMING，表示正在消费中</li>
     *   <li>"1" - Key 存在且值为 CONSUMED，表示已消费完成</li>
     * </ul>
     * </p>
     */
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local value = ARGV[1]
            local expire_time_ms = ARGV[2]
            return redis.call('SET', key, value, 'NX', 'GET', 'PX', expire_time_ms)
            """;

    /**
     * 环绕通知：拦截标记了 {@link IdempotentConsume} 注解的方法
     * <p>
     * 核心防重复消费逻辑：
     * <ol>
     *   <li>解析注解配置，生成唯一消费标识 Key</li>
     *   <li>通过 Lua 脚本原子性地检查/设置消费状态</li>
     *   <li>根据消费状态决定是否执行目标方法</li>
     *   <li>消费成功后更新状态为 CONSUMED，消费失败则删除 Key 允许重试</li>
     * </ol>
     * </p>
     *
     * @param joinPoint 连接点，包含目标方法的信息
     * @return 目标方法的执行结果，如果消息已消费则返回 null
     * @throws Throwable 如果目标方法执行异常或消息正在被重复消费
     */
    @Around("@annotation(edu.cqupt.devbrain.framework.idempotent.IdempotentConsume)")
    public Object idempotentConsume(ProceedingJoinPoint joinPoint) throws Throwable {
        IdempotentConsume idempotentConsume = getIdempotentConsumeAnnotation(joinPoint);
        String uniqueKey = idempotentConsume.keyPrefix()
                + SpELUtil.parseKey(idempotentConsume.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        long keyTimeoutSeconds = idempotentConsume.keyTimeout();

        String absentAndGet = stringRedisTemplate.execute(
                RedisScript.of(LUA_SCRIPT, String.class),
                List.of(uniqueKey),
                IdempotentConsumeStatusEnum.CONSUMING.getCode(),
                String.valueOf(TimeUnit.SECONDS.toMillis(keyTimeoutSeconds))
        );

        // 如果已有消费中状态，提示延迟消费；已完成则直接跳过
        boolean errorFlag = IdempotentConsumeStatusEnum.isError(absentAndGet);
        if (errorFlag) {
            log.warn("[{}] MQ repeated consumption, wait for delayed retry.", uniqueKey);
            throw new ServiceException(String.format("消息消费者幂等异常，幂等标识：%s", uniqueKey), BaseErrorCode.SERVICE_ERROR);
        }
        if (IdempotentConsumeStatusEnum.CONSUMED.getCode().equals(absentAndGet)) {
            log.info("[{}] MQ consumption already completed, skip.", uniqueKey);
            return null;
        }

        try {
            Object result = joinPoint.proceed();
            stringRedisTemplate.opsForValue().set(
                    uniqueKey,
                    IdempotentConsumeStatusEnum.CONSUMED.getCode(),
                    keyTimeoutSeconds,
                    TimeUnit.SECONDS
            );
            return result;
        } catch (Throwable ex) {
            stringRedisTemplate.delete(uniqueKey);
            throw ex;
        }
    }

    /**
     * 从连接点获取目标方法上的 {@link IdempotentConsume} 注解
     *
     * @param joinPoint 连接点
     * @return IdempotentConsume 注解实例
     * @throws NoSuchMethodException 如果找不到目标方法
     */
    public static IdempotentConsume getIdempotentConsumeAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(IdempotentConsume.class);
    }
}
