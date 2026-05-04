import { api } from './api';
import type {
  DocumentChunkItem,
  DocumentUploadPayload,
  KnowledgeBaseCreatePayload,
  KnowledgeBaseItem,
  KnowledgeBasePageParams,
  KnowledgeBaseUpdatePayload,
  KnowledgeChunkItem,
  KnowledgeDocumentItem,
  KnowledgeDocumentPageParams,
  OnlineDocumentImportPayload,
  PageResult,
} from '../types';
import type { AxiosProgressEvent } from 'axios';

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

export async function uploadDocument(
  kbId: string,
  payload: DocumentUploadPayload,
  onUploadProgress?: (event: AxiosProgressEvent) => void,
) {
  const formData = new FormData();
  formData.append('file', payload.file);
  if (payload.processMode) formData.append('processMode', payload.processMode);
  if (payload.chunkStrategy) formData.append('chunkStrategy', payload.chunkStrategy);
  if (payload.chunkConfig) formData.append('chunkConfig', payload.chunkConfig);
  if (payload.pipelineId) formData.append('pipelineId', payload.pipelineId);

  return api.post<KnowledgeDocumentItem, KnowledgeDocumentItem>(
    `/knowledge-base/${kbId}/docs/upload`,
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 0,
      onUploadProgress,
    },
  );
}

export async function importOnlineDocument(kbId: string, payload: OnlineDocumentImportPayload) {
  return api.post<KnowledgeDocumentItem, KnowledgeDocumentItem>(
    `/knowledge-base/${kbId}/docs/upload`,
    payload,
    { timeout: 0 },
  );
}

export async function getAllKnowledgeDocuments(params: KnowledgeDocumentPageParams) {
  return api.get<PageResult<KnowledgeDocumentItem>, PageResult<KnowledgeDocumentItem>>('/knowledge-documents', {
    params: {
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      kbId: params.kbId || undefined,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
      enabled: params.enabled === '' ? undefined : params.enabled,
    },
  });
}

export async function getKnowledgeDocuments(kbId: string) {
  return api.get<KnowledgeDocumentItem[], KnowledgeDocumentItem[]>(`/knowledge-base/${kbId}/docs`);
}

export async function deleteKnowledgeDocument(kbId: string, docId: string) {
  return api.delete<void, void>(`/knowledge-base/${kbId}/docs/${docId}`);
}

export async function toggleDocumentEnabled(kbId: string, docId: string, enabled: number) {
  return api.put<void, void>(`/knowledge-base/${kbId}/docs/${docId}/enabled`, { enabled });
}

export async function triggerDocumentChunk(docId: string, payload?: { chunkStrategy?: string; chunkConfig?: string }) {
  return api.post<void, void>(`/documents/parse/${docId}`, payload);
}

export async function getDocumentChunks(docId: string, pageNo: number, pageSize: number) {
  return api.get<PageResult<DocumentChunkItem>, PageResult<DocumentChunkItem>>(`/documents/${docId}/chunks`, {
    params: { page: pageNo, size: pageSize },
  });
}

export async function getKnowledgeDocumentChunks(docId: string, pageNo: number, pageSize: number) {
  return api.get<PageResult<KnowledgeChunkItem>, PageResult<KnowledgeChunkItem>>(
    `/knowledge-base/docs/${docId}/chunks`,
    { params: { pageNo, pageSize } },
  );
}

export async function updateKnowledgeDocumentChunk(docId: string, chunkId: string, content: string) {
  return api.put<KnowledgeChunkItem, KnowledgeChunkItem>(
    `/knowledge-base/docs/${docId}/chunks/${chunkId}`,
    { content },
  );
}
