package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 个人资料更新请求 —— 已登录用户更新自身资料。
 * <p>
 * 所有字段均为可选，仅更新非空字段。
 *
 * @param email       新邮箱地址，需符合邮箱格式
 * @param displayName 新显示名称，最长 64 字符
 * @param avatar      新头像地址，最长 256 字符
 */
public record ProfileUpdateRequest(
        @Email(message = "邮箱格式不正确") String email,
        @Size(max = 64, message = "显示名称不能超过 64 个字符") String displayName,
        @Size(max = 256, message = "头像地址不能超过 256 个字符") String avatar
) {
}
