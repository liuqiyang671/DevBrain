import { api } from './api';
import type {
  KnowledgeBaseCreatePayload,
  KnowledgeBaseItem,
  KnowledgeBasePageParams,
  KnowledgeBaseUpdatePayload,
  PageResult,
} from '../types';

export async function getKnowledgeBases(params: KnowledgeBasePageParams) {
  return api.get<PageResult<KnowledgeBaseItem>, PageResult<KnowledgeBaseItem>>('/knowledge-base', {
    params: {
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
    },
  });
}

export async function getKnowledgeBase(id: string) {
  return api.get<KnowledgeBaseItem, KnowledgeBaseItem>(`/knowledge-base/${id}`);
}

export async function createKnowledgeBase(payload: KnowledgeBaseCreatePayload) {
  return api.post<KnowledgeBaseItem, KnowledgeBaseItem>('/knowledge-base', payload);
}

export async function updateKnowledgeBase(id: string, payload: KnowledgeBaseUpdatePayload) {
  return api.put<KnowledgeBaseItem, KnowledgeBaseItem>(`/knowledge-base/${id}`, payload);
}

export async function deleteKnowledgeBase(id: string) {
  return api.delete<void, void>(`/knowledge-base/${id}`);
}
