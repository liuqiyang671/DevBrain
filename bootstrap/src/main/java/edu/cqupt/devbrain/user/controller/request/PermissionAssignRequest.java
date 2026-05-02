package edu.cqupt.devbrain.user.controller.request;

import java.util.Set;

/**
 * 权限分配请求 —— 为角色分配权限码时提交的数据。
 * <p>
 * 采用全量替换模式：传入的权限码列表将完全替换角色当前的权限关联。
 * 传入空列表则清除该角色的所有权限关联。
 *
 * @param permissionCodes 权限编码集合
 */
public record PermissionAssignRequest(Set<String> permissionCodes) {
}
