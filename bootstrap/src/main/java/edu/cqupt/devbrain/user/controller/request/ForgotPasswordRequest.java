package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 忘记密码请求 —— 用户通过邮箱申请密码重置。
 *
 * @param email 注册时使用的邮箱地址
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email
) {
}
