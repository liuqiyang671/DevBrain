package edu.cqupt.devbrain.knowledge.controller.request;

/**
 * 文档启停请求。
 *
 * @param enabled 启用状态：0 禁用，1 启用
 */
public record DocumentEnabledRequest(Integer enabled) {
}
