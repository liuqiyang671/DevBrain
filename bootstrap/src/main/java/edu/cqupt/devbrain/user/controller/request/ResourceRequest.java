package edu.cqupt.devbrain.user.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 资源访问规则请求 —— 创建或更新资源访问规则时提交的数据。
 *
 * @param resourceName   资源名称，如 "用户列表接口"
 * @param httpMethod     HTTP 方法，仅允许 GET/POST/PUT/PATCH/DELETE
 * @param pathPattern    路径模式，支持 Ant 风格通配符，如 /api/users/**
 * @param permissionCode 要求的权限码，为空表示仅需登录即可访问
 * @param publicAccess   是否公开访问，1 表示无需登录，null 或 0 表示需认证
 */
public record ResourceRequest(
        @NotBlank(message = "资源名称不能为空") String resourceName,
        @NotBlank(message = "HTTP 方法不能为空") @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", flags = Pattern.Flag.CASE_INSENSITIVE, message = "HTTP 方法必须是 GET/POST/PUT/PATCH/DELETE") String httpMethod,
        @NotBlank(message = "路径规则不能为空") String pathPattern,
        String permissionCode,
        Integer publicAccess
) {
}
