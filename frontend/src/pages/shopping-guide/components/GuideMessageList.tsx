/**
 * 导购对话消息列表组件。
 * 渲染用户和AI的消息气泡，支持图片展示和流式生成状态。
 */
import type { GuideMessage } from '../../../types';

interface GuideMessageListProps {
  messages: GuideMessage[];
  streaming: boolean;
  statusText?: string | null;
}

export function GuideMessageList({ messages, streaming, statusText }: GuideMessageListProps) {
  if (messages.length === 0) {
    return (
      <section className="guide-empty-state">
        <span className="guide-empty-mark">AI</span>
        <h2>今天想买什么？</h2>
        <p>说出预算、用途、偏好或纠结点，我会按证据和商品属性帮你筛选。</p>
        <div className="guide-prompt-row">
          <span>5000 以内剪视频笔记本</span>
          <span>通勤降噪耳机怎么选</span>
          <span>两款手机拍照对比</span>
        </div>
      </section>
    );
  }
  return (
    <div className="guide-message-list">
      {messages.map((message) => (
        <article className={`guide-message ${message.role}`} key={message.id}>
          <div className="guide-message-meta">
            <strong>{message.role === 'user' ? '你' : 'AI 导购'}</strong>
            {message.streaming && <span>生成中</span>}
          </div>
          {message.images && message.images.length > 0 && (
            <div className="guide-message-images">
              {message.images.map((image) => (
                <figure key={image.imageId}>
                  {image.previewUrl && <img src={image.previewUrl} alt={image.fileName} />}
                  <figcaption>{image.fileName}</figcaption>
                </figure>
              ))}
            </div>
          )}
          <p>{message.content || (message.streaming ? '正在组织推荐理由...' : '')}</p>
          {message.errorMessage && <small>{message.errorMessage}</small>}
        </article>
      ))}
      {streaming && statusText && <div className="guide-stream-status">{statusText}</div>}
    </div>
  );
}
