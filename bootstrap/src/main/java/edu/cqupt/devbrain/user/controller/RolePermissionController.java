package edu.cqupt.devbrain.user.controller;

import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.user.controller.request.PermissionAssignRequest;
import edu.cqupt.devbrain.user.controller.request.PermissionRequest;
import edu.cqupt.devbrain.user.controller.request.ResourceRequest;
import edu.cqupt.devbrain.user.controller.request.RoleRequest;
import edu.cqupt.devbrain.user.controller.vo.PermissionVO;
import edu.cqupt.devbrain.user.controller.vo.ResourceVO;
import edu.cqupt.devbrain.user.controller.vo.RoleVO;
import edu.cqupt.devbrain.user.service.RolePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限管理控制器 —— 提供角色、权限码和资源访问规则的 CRUD 管理接口。
 * <p>
 * 基于 RBAC（基于角色的访问控制）模型，管理三要素：
 * <ul>
 *   <li><b>角色（Role）</b>：用户的身份标识，如 admin、user</li>
 *   <li><b>权限码（Permission）</b>：细粒度的操作权限标识</li>
 *   <li><b>资源（Resource）</b>：API 接口访问规则，绑定 HTTP 方法 + 路径模式 + 权限码</li>
 * </ul>
 * <p>
 * <b>业务规则</b>：
 * <ul>
 *   <li>admin 和 user 为内置角色，不允许删除</li>
 *   <li>角色删除时自动清理关联的用户-角色关系和角色-权限关系</li>
 *   <li>权限码删除时自动清理关联的角色-权限关系</li>
 *   <li>资源变更时自动刷新访问控制缓存</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    /**
     * 查询所有角色列表。
     *
     * @return 角色列表，按角色编码升序排列
     */
    @GetMapping("/roles")
    public Result<List<RoleVO>> roles() {
        return Results.success(rolePermissionService.roles());
    }

    /**
     * 创建新角色。
     *
     * @param request 角色创建请求，包含角色编码、角色名称和描述
     * @return 创建成功的角色信息
     */
    @PostMapping("/roles")
    public Result<RoleVO> createRole(@RequestBody @Valid RoleRequest request) {
        return Results.success(rolePermissionService.createRole(request));
    }

    /**
     * 更新角色信息。
     *
     * @param id      角色ID
     * @param request 角色更新请求
     * @return 更新后的角色信息
     */
    @PutMapping("/roles/{id}")
    public Result<RoleVO> updateRole(@PathVariable String id, @RequestBody @Valid RoleRequest request) {
        return Results.success(rolePermissionService.updateRole(id, request));
    }

    /**
     * 删除角色。
     * <p>
     * 内置角色（admin、user）不允许删除。删除时自动清理关联关系。
     *
     * @param id 角色ID
     * @return 空结果
     */
    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable String id) {
        rolePermissionService.deleteRole(id);
        return Results.success();
    }

    /**
     * 为角色分配权限码。
     * <p>
     * 全量替换模式：传入的权限码列表将完全替换角色当前的权限关联。
     * 传入空列表则清除该角色的所有权限关联。
     *
     * @param id      角色ID
     * @param request 权限分配请求，包含权限码集合
     * @return 空结果
     */
    @PutMapping("/roles/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable String id, @RequestBody PermissionAssignRequest request) {
        rolePermissionService.assignPermissions(id, request);
        return Results.success();
    }

    /**
     * 查询所有权限码列表。
     *
     * @return 权限码列表，按权限编码升序排列
     */
    @GetMapping("/permissions")
    public Result<List<PermissionVO>> permissions() {
        return Results.success(rolePermissionService.permissions());
    }

    /**
     * 创建新权限码。
     *
     * @param request 权限码创建请求，包含权限编码、权限名称和描述
     * @return 创建成功的权限码信息
     */
    @PostMapping("/permissions")
    public Result<PermissionVO> createPermission(@RequestBody @Valid PermissionRequest request) {
        return Results.success(rolePermissionService.createPermission(request));
    }

    /**
     * 更新权限码信息。
     *
     * @param id      权限码ID
     * @param request 权限码更新请求
     * @return 更新后的权限码信息
     */
    @PutMapping("/permissions/{id}")
    public Result<PermissionVO> updatePermission(@PathVariable String id, @RequestBody @Valid PermissionRequest request) {
        return Results.success(rolePermissionService.updatePermission(id, request));
    }

    /**
     * 删除权限码。
     * <p>
     * 删除时自动清理关联的角色-权限关系。
     *
     * @param id 权限码ID
     * @return 空结果
     */
    @DeleteMapping("/permissions/{id}")
    public Result<Void> deletePermission(@PathVariable String id) {
        rolePermissionService.deletePermission(id);
        return Results.success();
    }

    /**
     * 查询所有资源访问规则列表。
     *
     * @return 资源列表，按路径模式升序排列
     */
    @GetMapping("/resources")
    public Result<List<ResourceVO>> resources() {
        return Results.success(rolePermissionService.resources());
    }

    /**
     * 创建资源访问规则。
     * <p>
     * 创建后自动刷新访问控制缓存，使新规则立即生效。
     *
     * @param request 资源创建请求
     * @return 创建成功的资源信息
     */
    @PostMapping("/resources")
    public Result<ResourceVO> createResource(@RequestBody @Valid ResourceRequest request) {
        return Results.success(rolePermissionService.createResource(request));
    }

    /**
     * 更新资源访问规则。
     * <p>
     * 更新后自动刷新访问控制缓存。
     *
     * @param id      资源ID
     * @param request 资源更新请求
     * @return 更新后的资源信息
     */
    @PutMapping("/resources/{id}")
    public Result<ResourceVO> updateResource(@PathVariable String id, @RequestBody @Valid ResourceRequest request) {
        return Results.success(rolePermissionService.updateResource(id, request));
    }

    /**
     * 删除资源访问规则。
     * <p>
     * 删除后自动刷新访问控制缓存。
     *
     * @param id 资源ID
     * @return 空结果
     */
    @DeleteMapping("/resources/{id}")
    public Result<Void> deleteResource(@PathVariable String id) {
        rolePermissionService.deleteResource(id);
        return Results.success();
    }
}
