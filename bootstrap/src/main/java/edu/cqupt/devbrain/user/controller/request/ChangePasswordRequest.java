package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求 —— 已登录用户修改自身密码。
 *
 * @param currentPassword 当前密码，用于身份验证
 * @param newPassword      新密码，长度 8-64 字符
 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空") String currentPassword,
        @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 64, message = "新密码长度需为 8-64 个字符") String newPassword
) {
}
