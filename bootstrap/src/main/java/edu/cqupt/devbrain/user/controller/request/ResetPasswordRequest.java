package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求 —— 使用重置令牌设置新密码。
 *
 * @param token      密码重置令牌（由"忘记密码"流程生成）
 * @param newPassword 新密码，长度 8-64 字符
 */
public record ResetPasswordRequest(
        @NotBlank(message = "重置令牌不能为空") String token,
        @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 64, message = "新密码长度需为 8-64 个字符") String newPassword
) {
}
