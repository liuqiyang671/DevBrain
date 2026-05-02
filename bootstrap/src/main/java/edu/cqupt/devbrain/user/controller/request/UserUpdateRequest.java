package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 更新用户请求 —— 管理员更新用户信息时提交的数据。
 * <p>
 * 所有字段均为可选，仅更新非空字段。角色分配为全量替换模式。
 *
 * @param email       新邮箱地址
 * @param displayName 新显示名称
 * @param avatar      新头像地址
 * @param status      账号状态（enabled/disabled）
 * @param password    新密码，设置后直接覆盖旧密码
 * @param roleCodes   角色编码集合，非 null 时全量替换
 */
public record UserUpdateRequest(
        @Email(message = "邮箱格式不正确") String email,
        @Size(max = 64, message = "显示名称不能超过 64 个字符") String displayName,
        @Size(max = 256, message = "头像地址不能超过 256 个字符") String avatar,
        String status,
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 个字符") String password,
        Set<String> roleCodes
) {
}
