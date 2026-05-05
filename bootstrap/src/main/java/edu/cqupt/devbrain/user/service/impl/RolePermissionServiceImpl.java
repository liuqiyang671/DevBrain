package edu.cqupt.devbrain.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.user.controller.request.PermissionAssignRequest;
import edu.cqupt.devbrain.user.controller.request.PermissionRequest;
import edu.cqupt.devbrain.user.controller.request.ResourceRequest;
import edu.cqupt.devbrain.user.controller.request.RoleRequest;
import edu.cqupt.devbrain.user.controller.vo.PermissionVO;
import edu.cqupt.devbrain.user.controller.vo.ResourceVO;
import edu.cqupt.devbrain.user.controller.vo.RoleVO;
import edu.cqupt.devbrain.user.dao.entity.PermissionDO;
import edu.cqupt.devbrain.user.dao.entity.ResourceDO;
import edu.cqupt.devbrain.user.dao.entity.RoleDO;
import edu.cqupt.devbrain.user.dao.mapper.PermissionMapper;
import edu.cqupt.devbrain.user.dao.mapper.ResourceMapper;
import edu.cqupt.devbrain.user.dao.mapper.RoleMapper;
import edu.cqupt.devbrain.user.service.AccessControlService;
import edu.cqupt.devbrain.user.service.RolePermissionService;
import edu.cqupt.devbrain.user.service.UserDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色权限服务实现 —— 处理角色、权限码和资源访问规则的 CRUD 核心业务逻辑。
 * <p>
 * <b>业务规则</b>：
 * <ul>
 *   <li>admin 和 user 为内置角色，不允许删除</li>
 *   <li>角色/权限码删除时自动清理关联关系</li>
 *   <li>资源变更时自动刷新访问控制缓存</li>
 *   <li>权限分配为全量替换模式</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final ResourceMapper resourceMapper;
    private final UserDirectoryService directoryService;
    private final AccessControlService accessControlService;

    /**
     * 查询所有角色列表，按角色编码升序排列。
     */
    @Override
    public List<RoleVO> roles() {
        return roleMapper.selectList(Wrappers.lambdaQuery(RoleDO.class).orderByAsc(RoleDO::getRoleCode))
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    /**
     * 创建新角色。
     */
    @Override
    public RoleVO createRole(RoleRequest request) {
        log.info("Creating role: roleCode={}", request.roleCode());
        RoleDO role = new RoleDO();
        role.setRoleCode(clean(request.roleCode()));
        role.setRoleName(clean(request.roleName()));
        role.setDescription(clean(request.description()));
        roleMapper.insert(role);
        log.info("Role created successfully: id={}, roleCode={}", role.getId(), role.getRoleCode());
        return toRoleVO(role);
    }

    /**
     * 更新角色信息。
     */
    @Override
    public RoleVO updateRole(String id, RoleRequest request) {
        RoleDO role = loadRole(id);
        role.setRoleCode(clean(request.roleCode()));
        role.setRoleName(clean(request.roleName()));
        role.setDescription(clean(request.description()));
        roleMapper.updateById(role);
        return toRoleVO(role);
    }

    /**
     * 删除角色。
     * <p>
     * 内置角色（admin、user）不允许删除，防止系统权限体系崩溃。
     * 删除后自动清理关联的用户-角色和角色-权限关系。
     */
    @Override
    @Transactional
    public void deleteRole(String id) {
        log.info("Deleting role: id={}", id);
        RoleDO role = loadRole(id);
        if ("admin".equals(role.getRoleCode()) || "user".equals(role.getRoleCode())) {
            throw new ClientException("内置角色不允许删除");
        }
        roleMapper.deleteById(id);
        directoryService.removeRoleRelations(id);
        log.info("Role deleted successfully: id={}, roleCode={}", id, role.getRoleCode());
    }

    /**
     * 为角色分配权限码（全量替换模式）。
     */
    @Override
    @Transactional
    public void assignPermissions(String id, PermissionAssignRequest request) {
        loadRole(id);
        directoryService.assignPermissions(id, request.permissionCodes());
    }

    /**
     * 查询所有权限码列表，按权限编码升序排列。
     */
    @Override
    public List<PermissionVO> permissions() {
        return permissionMapper.selectList(Wrappers.lambdaQuery(PermissionDO.class).orderByAsc(PermissionDO::getPermissionCode))
                .stream().map(this::toPermissionVO).toList();
    }

    /**
     * 创建新权限码。
     */
    @Override
    public PermissionVO createPermission(PermissionRequest request) {
        log.info("Creating permission: permissionCode={}", request.permissionCode());
        PermissionDO permission = new PermissionDO();
        permission.setPermissionCode(clean(request.permissionCode()));
        permission.setPermissionName(clean(request.permissionName()));
        permission.setDescription(clean(request.description()));
        permissionMapper.insert(permission);
        log.info("Permission created successfully: id={}, permissionCode={}", permission.getId(), permission.getPermissionCode());
        return toPermissionVO(permission);
    }

    /**
     * 更新权限码信息。
     */
    @Override
    public PermissionVO updatePermission(String id, PermissionRequest request) {
        PermissionDO permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new ClientException("权限码不存在");
        }
        permission.setPermissionCode(clean(request.permissionCode()));
        permission.setPermissionName(clean(request.permissionName()));
        permission.setDescription(clean(request.description()));
        permissionMapper.updateById(permission);
        return toPermissionVO(permission);
    }

    /**
     * 删除权限码。
     * <p>
     * 删除后自动清理关联的角色-权限关系，防止孤立数据。
     */
    @Override
    @Transactional
    public void deletePermission(String id) {
        log.info("Deleting permission: id={}", id);
        PermissionDO permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new ClientException("权限码不存在");
        }
        permissionMapper.deleteById(id);
        directoryService.removePermissionRelations(id);
        log.info("Permission deleted successfully: id={}, permissionCode={}", id, permission.getPermissionCode());
    }

    /**
     * 查询所有资源访问规则列表，按路径模式升序排列。
     */
    @Override
    public List<ResourceVO> resources() {
        return resourceMapper.selectList(Wrappers.lambdaQuery(ResourceDO.class).orderByAsc(ResourceDO::getPathPattern))
                .stream().map(this::toResourceVO).toList();
    }

    /**
     * 创建资源访问规则。
     * <p>
     * 创建后自动刷新访问控制缓存，使新规则立即生效。
     */
    @Override
    public ResourceVO createResource(ResourceRequest request) {
        log.info("Creating resource: resourceName={}, httpMethod={}, pathPattern={}", request.resourceName(), request.httpMethod(), request.pathPattern());
        ResourceDO resource = new ResourceDO();
        apply(request, resource);
        resourceMapper.insert(resource);
        accessControlService.clearResourceCache();
        log.info("Resource created successfully: id={}", resource.getId());
        return toResourceVO(resource);
    }

    /**
     * 更新资源访问规则。
     * <p>
     * 更新后自动刷新访问控制缓存。
     */
    @Override
    public ResourceVO updateResource(String id, ResourceRequest request) {
        ResourceDO resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new ClientException("资源规则不存在");
        }
        apply(request, resource);
        resourceMapper.updateById(resource);
        accessControlService.clearResourceCache();
        return toResourceVO(resource);
    }

    /**
     * 删除资源访问规则。
     * <p>
     * 删除后自动刷新访问控制缓存。
     */
    @Override
    public void deleteResource(String id) {
        log.info("Deleting resource: id={}", id);
        resourceMapper.deleteById(id);
        accessControlService.clearResourceCache();
        log.info("Resource deleted successfully: id={}", id);
    }

    /**
     * 根据ID加载角色，不存在则抛出异常。
     */
    private RoleDO loadRole(String id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new ClientException("角色不存在");
        }
        return role;
    }

    /**
     * 将请求参数应用到资源实体。
     * <p>
     * HTTP 方法统一转大写，公开访问标志默认为 0（需认证）。
     */
    private void apply(ResourceRequest request, ResourceDO resource) {
        resource.setResourceName(clean(request.resourceName()));
        resource.setHttpMethod(clean(request.httpMethod()).toUpperCase());
        resource.setPathPattern(clean(request.pathPattern()));
        resource.setPermissionCode(clean(request.permissionCode()));
        resource.setPublicAccess(request.publicAccess() == null ? 0 : request.publicAccess());
    }

    /**
     * 将角色实体转换为视图对象，包含该角色的权限编码集合。
     */
    private RoleVO toRoleVO(RoleDO role) {
        return new RoleVO(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDescription(), directoryService.permissionCodesByRoles(java.util.Set.of(role.getRoleCode())));
    }

    /**
     * 将权限码实体转换为视图对象。
     */
    private PermissionVO toPermissionVO(PermissionDO permission) {
        return new PermissionVO(permission.getId(), permission.getPermissionCode(), permission.getPermissionName(), permission.getDescription());
    }

    /**
     * 将资源访问规则实体转换为视图对象。
     */
    private ResourceVO toResourceVO(ResourceDO resource) {
        return new ResourceVO(resource.getId(), resource.getResourceName(), resource.getHttpMethod(), resource.getPathPattern(), resource.getPermissionCode(), resource.getPublicAccess());
    }

    /**
     * 清理字符串：去除首尾空白。null 值保持不变。
     */
    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
