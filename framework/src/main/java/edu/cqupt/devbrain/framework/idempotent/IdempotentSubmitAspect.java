package edu.cqupt.devbrain.framework.idempotent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 防重复提交 AOP 切面
 * <p>
 * 拦截标记了 {@link IdempotentSubmit} 注解的方法，通过 Redisson 分布式锁实现防重复提交逻辑。
 * </p>
 * <p>
 * 处理流程：
 * <ol>
 *   <li>根据注解配置生成分布式锁的 Key</li>
 *   <li>尝试获取 Redisson 分布式锁（非阻塞）</li>
 *   <li>如果获取锁失败，说明请求正在处理中，抛出 {@link ClientException} 提示用户稍后重试</li>
 *   <li>如果获取锁成功，执行目标方法，执行完毕后释放锁</li>
 * </ol>
 * </p>
 * <p>
 * 该组件仅在 Spring 容器中存在 {@link RedissonClient} Bean 时生效。
 * </p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public final class IdempotentSubmitAspect {

    /** Redisson 客户端，用于获取分布式锁 */
    private final RedissonClient redissonClient;

    /** Gson 实例，用于将方法参数序列化为 JSON（计算 MD5 时使用） */
    private final Gson gson = new Gson();

    /**
     * 环绕通知：拦截标记了 {@link IdempotentSubmit} 注解的方法
     * <p>
     * 核心防重复提交逻辑：
     * <ol>
     *   <li>获取方法上的 @IdempotentSubmit 注解</li>
     *   <li>根据注解配置（自定义 Key 或默认策略）构建分布式锁的 Key</li>
     *   <li>尝试获取 Redisson 分布式锁</li>
     *   <li>获取锁失败则抛出异常，获取成功则执行目标方法并最终释放锁</li>
     * </ol>
     * </p>
     *
     * @param joinPoint 连接点，包含目标方法的信息
     * @return 目标方法的执行结果
     * @throws Throwable 如果目标方法执行异常或获取锁失败
     */
    @Around("@annotation(edu.cqupt.devbrain.framework.idempotent.IdempotentSubmit)")
    public Object idempotentSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        IdempotentSubmit idempotentSubmit = getIdempotentSubmitAnnotation(joinPoint);
        // 获取分布式锁标识
        String lockKey = buildLockKey(joinPoint, idempotentSubmit);
        RLock lock = redissonClient.getLock(lockKey);
        // 尝试获取锁，获取锁失败就意味着已经重复提交，直接抛出异常
        if (!lock.tryLock()) {
            throw new ClientException(idempotentSubmit.message());
        }
        Object result;
        try {
            // 执行标记了防重复提交注解的方法原逻辑
            result = joinPoint.proceed();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * 从连接点获取目标方法上的 {@link IdempotentSubmit} 注解
     *
     * @param joinPoint 连接点
     * @return IdempotentSubmit 注解实例
     * @throws NoSuchMethodException 如果找不到目标方法
     */
    public static IdempotentSubmit getIdempotentSubmitAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(IdempotentSubmit.class);
    }

    /**
     * 获取当前 HTTP 请求的 Servlet 路径
     * <p>
     * 用于构建默认的幂等锁 Key，确保同一接口的请求使用相同的锁前缀。
     * </p>
     *
     * @return 当前请求的 Servlet 路径
     */
    private String getServletPath() {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.requireNonNull(sra).getRequest().getServletPath();
    }

    /**
     * 获取当前登录用户的 ID
     * <p>
     * 从用户上下文中获取当前登录用户 ID，用于构建幂等锁 Key，
     * 确保不同用户的相同请求可以并行处理。
     * </p>
     *
     * @return 当前登录用户的 ID
     */
    private String getCurrentUserId() {
        return UserContext.getUserId();
    }

    /**
     * 计算方法参数的 MD5 哈希值
     * <p>
     * 将方法参数序列化为 JSON 后计算 MD5，用于区分不同参数组合的请求。
     * </p>
     *
     * @param joinPoint 连接点
     * @return 方法参数的 MD5 哈希值（十六进制字符串）
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(gson.toJson(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建分布式锁的 Key
     * <p>
     * Key 生成策略：
     * <ul>
     *   <li>如果注解指定了自定义 key（SpEL 表达式），则解析 SpEL 表达式生成 Key，格式：idempotent-submit:key:{spelResult}</li>
     *   <li>否则使用默认策略，格式：idempotent-submit:path:{servletPath}:currentUserId:{userId}:md5:{argsMD5}</li>
     * </ul>
     * </p>
     *
     * @param joinPoint         连接点
     * @param idempotentSubmit  幂等注解实例
     * @return 分布式锁的 Key
     */
    private String buildLockKey(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) {
        if (StrUtil.isNotBlank(idempotentSubmit.key())) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object keyValue = SpELUtil.parseKey(idempotentSubmit.key(), signature.getMethod(), joinPoint.getArgs());
            return String.format("idempotent-submit:key:%s", keyValue);
        }
        return String.format(
                "idempotent-submit:path:%s:currentUserId:%s:md5:%s",
                getServletPath(),
                getCurrentUserId(),
                calcArgsMD5(joinPoint)
        );
    }
}
