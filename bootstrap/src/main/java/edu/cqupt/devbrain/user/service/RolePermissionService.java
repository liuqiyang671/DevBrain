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

    List<RoleVO> roles();

    RoleVO createRole(RoleRequest request);

    RoleVO updateRole(String id, RoleRequest request);

    void deleteRole(String id);

    void assignPermissions(String id, PermissionAssignRequest request);

    List<PermissionVO> permissions();

    PermissionVO createPermission(PermissionRequest request);

    PermissionVO updatePermission(String id, PermissionRequest request);

    void deletePermission(String id);

    List<ResourceVO> resources();

    ResourceVO createResource(ResourceRequest request);

    ResourceVO updateResource(String id, ResourceRequest request);

    void deleteResource(String id);
}
