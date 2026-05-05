package edu.cqupt.devbrain.user.service;

import edu.cqupt.devbrain.user.controller.request.PermissionAssignRequest;
import edu.cqupt.devbrain.user.controller.request.PermissionRequest;
import edu.cqupt.devbrain.user.controller.request.ResourceRequest;
import edu.cqupt.devbrain.user.controller.request.RoleRequest;
import edu.cqupt.devbrain.user.controller.vo.PermissionVO;
import edu.cqupt.devbrain.user.controller.vo.ResourceVO;
import edu.cqupt.devbrain.user.controller.vo.RoleVO;

import java.util.List;

/**
 * 角色权限服务接口 —— 定义角色、权限码和资源访问规则的 CRUD 操作。
 */
public interface RolePermissionService {

    /**
     * 查询所有角色列表。
     *
     * @return 角色列表，按角色编码升序排列
     */
    List<RoleVO> roles();

    /**
     * 创建新角色。
     *
     * @param request 角色创建请求，包含角色编码、角色名称和描述
     * @return 创建成功的角色信息
     */
    RoleVO createRole(RoleRequest request);

    /**
     * 更新角色信息。
     *
     * @param id      角色ID
     * @param request 角色更新请求
     * @return 更新后的角色信息
     */
    RoleVO updateRole(String id, RoleRequest request);

    /**
     * 删除角色。
     * <p>
     * 内置角色（admin、user）不允许删除，删除时自动清理关联关系。
     *
     * @param id 角色ID
     */
    void deleteRole(String id);

    /**
     * 为角色分配权限码（全量替换模式）。
     * <p>
     * 传入的权限码列表将完全替换角色当前的权限关联，传入空列表则清除所有权限。
     *
     * @param id      角色ID
     * @param request 权限分配请求，包含权限码集合
     */
    void assignPermissions(String id, PermissionAssignRequest request);

    /**
     * 查询所有权限码列表。
     *
     * @return 权限码列表，按权限编码升序排列
     */
    List<PermissionVO> permissions();

    /**
     * 创建新权限码。
     *
     * @param request 权限码创建请求，包含权限编码、权限名称和描述
     * @return 创建成功的权限码信息
     */
    PermissionVO createPermission(PermissionRequest request);

    /**
     * 更新权限码信息。
     *
     * @param id      权限码ID
     * @param request 权限码更新请求
     * @return 更新后的权限码信息
     */
    PermissionVO updatePermission(String id, PermissionRequest request);

    /**
     * 删除权限码。
     * <p>
     * 删除时自动清理关联的角色-权限关系。
     *
     * @param id 权限码ID
     */
    void deletePermission(String id);

    /**
     * 查询所有资源访问规则列表。
     *
     * @return 资源列表，按路径模式升序排列
     */
    List<ResourceVO> resources();

    /**
     * 创建资源访问规则。
     * <p>
     * 创建后自动刷新访问控制缓存，使新规则立即生效。
     *
     * @param request 资源创建请求
     * @return 创建成功的资源信息
     */
    ResourceVO createResource(ResourceRequest request);

    /**
     * 更新资源访问规则。
     * <p>
     * 更新后自动刷新访问控制缓存。
     *
     * @param id      资源ID
     * @param request 资源更新请求
     * @return 更新后的资源信息
     */
    ResourceVO updateResource(String id, ResourceRequest request);

    /**
     * 删除资源访问规则。
     * <p>
     * 删除后自动刷新访问控制缓存。
     *
     * @param id 资源ID
     */
    void deleteResource(String id);
}
