package edu.cqupt.devbrain.user.service;

import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;

/**
 * 登录结果 —— 封装登录成功后返回的 JWT 令牌和用户信息。
 *
 * @param token JWT 令牌字符串，用于后续请求认证
 * @param user  当前用户信息
 */
public record LoginOutcome(String token, CurrentUserVO user) {
}
