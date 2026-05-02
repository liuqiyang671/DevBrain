package edu.cqupt.devbrain.user.controller.vo;

import java.util.Set;

/**
 * 角色视图 —— 角色管理接口返回的角色信息，包含该角色关联的权限编码集合。
 *
 * @param id            角色ID
 * @param roleCode      角色编码
 * @param roleName      角色显示名称
 * @param description   角色描述
 * @param permissions   该角色关联的权限编码集合
 */
public record RoleVO(
        String id,
        String roleCode,
        String roleName,
        String description,
        Set<String> permissions
) {
}
