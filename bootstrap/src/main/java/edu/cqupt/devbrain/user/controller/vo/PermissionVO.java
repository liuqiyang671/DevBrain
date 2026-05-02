package edu.cqupt.devbrain.user.controller.vo;

/**
 * 权限码视图 —— 权限管理接口返回的权限码信息。
 *
 * @param id              权限码ID
 * @param permissionCode  权限编码
 * @param permissionName  权限显示名称
 * @param description     权限描述
 */
public record PermissionVO(
        String id,
        String permissionCode,
        String permissionName,
        String description
) {
}
