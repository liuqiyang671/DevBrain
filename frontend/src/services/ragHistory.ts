import type { RagConversationSummary, RagMessage } from '../types';

/** RAG 本地会话摘要缓存键；后端会话列表接口补齐后可替换为服务端数据。 */
const ragConversationStoreKey = 'ai-shopping-agent.rag.conversations.v1';
/** RAG 本地会话消息缓存键；用于当前浏览器展示历史会话。 */
const ragMessageStoreKey = 'ai-shopping-agent.rag.messages.v1';

export function readRagConversations(): RagConversationSummary[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(ragConversationStoreKey);
    const parsed = raw ? JSON.parse(raw) as RagConversationSummary[] : [];
    if (!Array.isArray(parsed)) return [];
    return parsed.sort((left, right) => new Date(right.lastTime || 0).getTime() - new Date(left.lastTime || 0).getTime());
  } catch {
    return [];
  }
}

export function writeRagConversations(items: RagConversationSummary[]) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(ragConversationStoreKey, JSON.stringify(items.slice(0, 80)));
  } catch {
    // Local RAG history is a convenience cache; the streaming answer remains the source of truth.
  }
}

export function readRagMessages(conversationId: string): RagMessage[] {
  if (typeof window === 'undefined' || !conversationId) return [];
  try {
    const raw = window.localStorage.getItem(ragMessageStoreKey);
    const parsed = raw ? JSON.parse(raw) as Record<string, RagMessage[]> : {};
    const messages = parsed?.[conversationId];
    return Array.isArray(messages) ? messages : [];
  } catch {
    return [];
  }
}

export function writeRagMessages(conversationId: string, messages: RagMessage[]) {
  if (typeof window === 'undefined' || !conversationId) return;
  try {
    const raw = window.localStorage.getItem(ragMessageStoreKey);
    const parsed = raw ? JSON.parse(raw) as Record<string, RagMessage[]> : {};
    const next = { ...(parsed || {}), [conversationId]: messages.slice(-80) };
    window.localStorage.setItem(ragMessageStoreKey, JSON.stringify(next));
  } catch {
    // Local RAG history is best-effort only.
  }
}

export function upsertRagConversation(summary: RagConversationSummary) {
  const current = readRagConversations();
  const next = [
    summary,
    ...current.filter((item) => item.conversationId !== summary.conversationId),
  ].sort((left, right) => new Date(right.lastTime || 0).getTime() - new Date(left.lastTime || 0).getTime());
  writeRagConversations(next);
  return next;
}

export function deleteRagConversation(conversationId: string) {
  if (typeof window === 'undefined' || !conversationId) return [];
  const next = readRagConversations().filter((item) => item.conversationId !== conversationId);
  writeRagConversations(next);
  try {
    const raw = window.localStorage.getItem(ragMessageStoreKey);
    const parsed = raw ? JSON.parse(raw) as Record<string, RagMessage[]> : {};
    delete parsed[conversationId];
    window.localStorage.setItem(ragMessageStoreKey, JSON.stringify(parsed));
  } catch {
    // Ignore malformed local cache and keep the summary deletion effective.
  }
  return next;
}

export function clearRagHistory() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ragConversationStoreKey);
  window.localStorage.removeItem(ragMessageStoreKey);
}

export function getRagConversationTitle(item?: RagConversationSummary | null) {
  return item?.title || item?.lastQuestion || '未命名会话';
}
