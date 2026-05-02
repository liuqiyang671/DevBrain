package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 —— 新用户注册时提交的信息。
 *
 * @param username    用户名，必填，最长 64 字符
 * @param email       邮箱地址，必填，需符合邮箱格式
 * @param password    密码，必填，长度 8-64 字符
 * @param displayName 显示名称，选填，最长 64 字符，为空时默认使用用户名
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名不能超过 64 个字符") String username,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "密码不能为空") @Size(min = 8, max = 64, message = "密码长度需为 8-64 个字符") String password,
        @Size(max = 64, message = "显示名称不能超过 64 个字符") String displayName
) {
}
