package edu.cqupt.devbrain.user.controller.vo;

import java.util.Set;

/**
 * 登录响应视图 —— 登录成功后返回给前端的用户信息。
 *
 * @param user 当前用户信息
 */
public record LoginVO(CurrentUserVO user) {
}
