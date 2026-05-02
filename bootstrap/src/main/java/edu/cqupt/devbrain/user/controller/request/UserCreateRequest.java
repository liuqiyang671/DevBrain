package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 创建用户请求 —— 管理员创建新用户时提交的信息。
 *
 * @param username    用户名，必填
 * @param email       邮箱地址，必填
 * @param password    密码，必填，长度 8-64 字符
 * @param displayName 显示名称，选填，为空时默认使用用户名
 * @param avatar      头像地址，选填
 * @param roleCodes   角色编码集合，选填，为空时分配默认角色 "user"
 */
public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "密码不能为空") @Size(min = 8, max = 64, message = "密码长度需为 8-64 个字符") String password,
        String displayName,
        String avatar,
        Set<String> roleCodes
) {
}
