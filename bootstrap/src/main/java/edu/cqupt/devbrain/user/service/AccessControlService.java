package edu.cqupt.devbrain.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.dao.entity.ResourceDO;
import edu.cqupt.devbrain.user.dao.mapper.ResourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访问控制服务 —— 基于 RBAC 模型实现接口级别的权限校验。
 * <p>
 * 核心逻辑：根据请求的 HTTP 方法和路径，在资源规则表中查找匹配的规则，
 * 然后判断当前用户是否具备规则要求的权限码。
 * <p>
 * <b>权限判定流程</b>：
 * <ol>
 *   <li>查找匹配的资源规则（按 HTTP 方法 + Ant 路径匹配）</li>
 *   <li>未匹配到规则或规则标记为公开访问（publicAccess=1）→ 放行</li>
 *   <li>规则未绑定权限码（permissionCode 为空）→ 仅需登录即可访问</li>
 *   <li>用户为管理员（admin 角色）→ 放行</li>
 *   <li>用户权限集合中包含规则要求的权限码 → 放行</li>
 *   <li>以上均不满足 → 抛出权限不足异常</li>
 * </ol>
 * <p>
 * <b>缓存机制</b>：资源规则按 HTTP 方法分组缓存，TTL 为 60 秒，
 * 资源变更时通过 {@link #clearResourceCache()} 手动刷新缓存。
 */
@Service
public class AccessControlService {

    private static final long CACHE_TTL_MS = 60_000;

    private final ResourceMapper resourceMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, CacheEntry> resourceCache = new ConcurrentHashMap<>();

    public AccessControlService(ResourceMapper resourceMapper) {
        this.resourceMapper = resourceMapper;
    }

    /**
     * 检查当前用户是否有权访问指定路径。
     * <p>
     * 权限判定优先级：公开资源 > 无权限码要求 > 管理员 > 权限码匹配。
     *
     * @param method HTTP 方法
     * @param path   请求路径
     * @param user   当前登录用户
     * @throws ClientException 权限不足时抛出 FORIDDEN 异常
     */
    public void checkAccess(String method, String path, LoginUser user) {
        ResourceDO resource = findMatchingResource(method, path);
        if (resource == null || Integer.valueOf(1).equals(resource.getPublicAccess())) {
            return;
        }
        if (!StringUtils.hasText(resource.getPermissionCode())) {
            return;
        }
        if (user.isAdmin() || user.permissions().contains(resource.getPermissionCode())) {
            return;
        }
        throw new ClientException("权限不足", BaseErrorCode.FORBIDDEN);
    }

    /**
     * 清除资源规则缓存，在资源增删改后调用，使新规则立即生效。
     */
    public void clearResourceCache() {
        resourceCache.clear();
    }

    /**
     * 查找与请求匹配的资源规则。
     * <p>
     * 先按 HTTP 方法从缓存获取资源列表，再使用 Ant 路径匹配器逐个匹配。
     * 当多个规则匹配时，选择路径模式最长的（更具体的规则优先）。
     *
     * @param method HTTP 方法
     * @param path   请求路径
     * @return 匹配的资源规则，无匹配时返回 null
     */
    private ResourceDO findMatchingResource(String method, String path) {
        String key = method.toUpperCase();
        CacheEntry entry = resourceCache.get(key);
        if (entry == null || entry.isExpired()) {
            List<ResourceDO> resources = resourceMapper.selectList(
                    Wrappers.lambdaQuery(ResourceDO.class).eq(ResourceDO::getHttpMethod, key));
            entry = new CacheEntry(resources);
            resourceCache.put(key, entry);
        }
        return entry.resources().stream()
                .filter(resource -> pathMatcher.match(resource.getPathPattern(), path))
                .max(Comparator.comparingInt(resource -> resource.getPathPattern().length()))
                .orElse(null);
    }

    /**
     * 缓存条目 —— 包含资源列表和过期时间。
     * <p>
     * 使用 {@link ConcurrentHashMap} 存储，支持并发读取，TTL 到期后重新加载。
     */
    private record CacheEntry(List<ResourceDO> resources, long expireAt) {
        CacheEntry(List<ResourceDO> resources) {
            this(resources, System.currentTimeMillis() + CACHE_TTL_MS);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
