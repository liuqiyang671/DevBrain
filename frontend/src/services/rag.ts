import { api } from './api';
import type {
  RagChatRequest,
  RagSseCompletionPayload,
  RagSseErrorPayload,
  RagSseMessageDelta,
  RagSseMetaPayload,
} from '../types';

/**
 * RAG 问答链路 API 契约
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090/api/devbrain';

export interface RagStreamHandlers {
  onMeta?: (payload: RagSseMetaPayload) => void;
  onMessage?: (payload: RagSseMessageDelta) => void;
  onFinish?: (payload: RagSseCompletionPayload) => void;
  onDone?: () => void;
  onCancel?: (payload: RagSseErrorPayload) => void;
  onError?: (error: Error) => void;
}

/** 打开前台 RAG 流式问答连接 */
export function streamChat(payload: RagChatRequest, handlers: RagStreamHandlers) {
  const search = new URLSearchParams();
  search.set('question', payload.question);
  if (payload.conversationId) {
    search.set('conversationId', payload.conversationId);
  }
  search.set('deepThinking', String(Boolean(payload.deepThinking)));

  const source = new EventSource(`${API_BASE_URL}/rag/v3/chat?${search.toString()}`, { withCredentials: true });
  source.addEventListener('meta', (event) => handlers.onMeta?.(parseEventData<RagSseMetaPayload>(event)));
  source.addEventListener('message', (event) => handlers.onMessage?.(parseEventData<RagSseMessageDelta>(event)));
  source.addEventListener('finish', (event) => handlers.onFinish?.(parseEventData<RagSseCompletionPayload>(event)));
  source.addEventListener('done', () => {
    source.close();
    handlers.onDone?.();
  });
  source.addEventListener('cancel', (event) => {
    source.close();
    handlers.onCancel?.(parseEventData<RagSseErrorPayload>(event));
  });
  source.addEventListener('error', (event) => {
    source.close();
    if (event instanceof MessageEvent && event.data) {
      const payload = parseEventData<RagSseErrorPayload>(event);
      handlers.onError?.(new Error(payload.message || 'RAG 问答请求失败'));
      return;
    }
    handlers.onError?.(new Error('RAG 问答连接异常'));
  });
  source.onerror = () => {
    if (source.readyState === EventSource.CLOSED) return;
    source.close();
    handlers.onError?.(new Error('RAG 问答连接中断'));
  };
  return source;
}

/** 停止当前 RAG 流式生成任务 */
export async function stopChat(taskId: string) {
  return api.post<void, void>('/rag/v3/stop', null, { params: { taskId } });
}

function parseEventData<T>(event: Event) {
  const data = event instanceof MessageEvent ? event.data : '';
  if (!data || data === '[DONE]') return {} as T;
  try {
    return JSON.parse(data) as T;
  } catch {
    return { message: data } as T;
  }
}
