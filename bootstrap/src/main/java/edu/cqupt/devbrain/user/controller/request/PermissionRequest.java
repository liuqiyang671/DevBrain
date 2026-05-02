package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限码请求 —— 创建或更新权限码时提交的数据。
 *
 * @param permissionCode 权限编码，全局唯一，如 user:create、role:delete
 * @param permissionName 权限显示名称
 * @param description    权限描述，选填
 */
public record PermissionRequest(
        @NotBlank(message = "权限码不能为空") String permissionCode,
        @NotBlank(message = "权限名称不能为空") String permissionName,
        String description
) {
}
