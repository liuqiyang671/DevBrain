package edu.cqupt.devbrain.user.controller.vo;

/**
 * 资源访问规则视图 —— 资源管理接口返回的访问规则信息。
 *
 * @param id              资源ID
 * @param resourceName    资源名称
 * @param httpMethod      HTTP 方法
 * @param pathPattern     路径模式
 * @param permissionCode  要求的权限码，为空表示仅需登录
 * @param publicAccess    是否公开访问，1 表示无需登录
 */
public record ResourceVO(
        String id,
        String resourceName,
        String httpMethod,
        String pathPattern,
        String permissionCode,
        Integer publicAccess
) {
}
