package edu.cqupt.devbrain.sync.adapter;

/**
 * 从远程来源拉取到的文档内容。
 *
 * @param text        文本内容
 * @param contentType 内容类型（text/plain、text/html 等）
 * @param title       文档标题（可为 null）
 */
public record FetchedContent(String text, String contentType, String title) {
}
