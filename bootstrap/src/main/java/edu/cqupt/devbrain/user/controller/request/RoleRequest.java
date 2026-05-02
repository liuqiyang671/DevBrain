package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 角色请求 —— 创建或更新角色时提交的数据。
 *
 * @param roleCode    角色编码，全局唯一，如 admin、user
 * @param roleName    角色显示名称，如 管理员、普通用户
 * @param description 角色描述，选填
 */
public record RoleRequest(
        @NotBlank(message = "角色编码不能为空") String roleCode,
        @NotBlank(message = "角色名称不能为空") String roleName,
        String description
) {
}
