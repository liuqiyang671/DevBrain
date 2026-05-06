import { api } from './api';
import type {
  RagChatRequest,
  RagChatResponse,
  RagConversationDetail,
  RagConversationSummary,
  RagDebugRunRequest,
  RagDebugRunResult,
} from '../types';

/**
 * RAG 问答链路 API 契约
 * 后端 Controller 尚未接入时，页面会通过错误态展示接口不可用。
 */

/** 获取当前用户的 RAG 会话列表 */
export async function getConversations() {
  return api.get<RagConversationSummary[], RagConversationSummary[]>('/rag/conversations');
}

/** 获取指定 RAG 会话详情 */
export async function getConversation(conversationId: string) {
  return api.get<RagConversationDetail, RagConversationDetail>(`/rag/conversations/${conversationId}`);
}

/** 发送前台 RAG 问答请求 */
export async function sendChat(payload: RagChatRequest) {
  return api.post<RagChatResponse, RagChatResponse>('/rag/chat', payload);
}

/** 执行后台 RAG 链路调试请求 */
export async function runDebug(payload: RagDebugRunRequest) {
  return api.post<RagDebugRunResult, RagDebugRunResult>('/rag/debug/runs', payload);
}
