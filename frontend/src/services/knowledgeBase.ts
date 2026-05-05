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

/**
 * 知识库管理 API 模块
 * 提供知识库 CRUD、文档上传/导入、文档分块管理等功能
 */

/**
 * 分页查询知识库列表
 * @param params - 分页及筛选参数（页码、每页大小、关键词、状态）
 * @returns 分页知识库列表
 */
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

/**
 * 获取单个知识库详情
 * @param id - 知识库 ID
 * @returns 知识库详细信息
 */
export async function getKnowledgeBase(id: string) {
  return api.get<KnowledgeBaseItem, KnowledgeBaseItem>(`/knowledge-base/${id}`);
}

/**
 * 创建知识库
 * @param payload - 知识库创建参数（名称、描述、向量集合名、Embedding 模型等）
 * @returns 创建成功的知识库信息
 */
export async function createKnowledgeBase(payload: KnowledgeBaseCreatePayload) {
  return api.post<KnowledgeBaseItem, KnowledgeBaseItem>('/knowledge-base', payload);
}

/**
 * 更新知识库信息
 * @param id - 知识库 ID
 * @param payload - 更新参数
 * @returns 更新后的知识库信息
 */
export async function updateKnowledgeBase(id: string, payload: KnowledgeBaseUpdatePayload) {
  return api.put<KnowledgeBaseItem, KnowledgeBaseItem>(`/knowledge-base/${id}`, payload);
}

/**
 * 删除知识库
 * @param id - 知识库 ID
 */
export async function deleteKnowledgeBase(id: string) {
  return api.delete<void, void>(`/knowledge-base/${id}`);
}

/**
 * 上传文档到知识库
 * 支持文件上传及上传进度回调，使用 multipart/form-data 格式
 * @param kbId - 知识库 ID
 * @param payload - 上传参数（文件、处理模式、分块策略、分块配置、流水线 ID）
 * @param onUploadProgress - 上传进度回调函数，可选
 * @returns 上传成功的文档信息
 */
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

/**
 * 导入在线文档（飞书、URL 等）
 * @param kbId - 知识库 ID
 * @param payload - 在线文档导入参数（来源类型、来源地址、文档名称等）
 * @returns 导入成功的文档信息
 */
export async function importOnlineDocument(kbId: string, payload: OnlineDocumentImportPayload) {
  return api.post<KnowledgeDocumentItem, KnowledgeDocumentItem>(
    `/knowledge-base/${kbId}/docs/upload`,
    payload,
    { timeout: 0 },
  );
}

/**
 * 全局分页查询知识文档（跨知识库）
 * @param params - 分页及筛选参数（页码、每页大小、知识库 ID、关键词、状态、启用状态）
 * @returns 分页文档列表
 */
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

/**
 * 获取知识库下的文档列表
 * @param kbId - 知识库 ID
 * @returns 文档数组
 */
export async function getKnowledgeDocuments(kbId: string) {
  return api.get<KnowledgeDocumentItem[], KnowledgeDocumentItem[]>(`/knowledge-base/${kbId}/docs`);
}

/**
 * 删除知识库中的文档
 * @param kbId - 知识库 ID
 * @param docId - 文档 ID
 */
export async function deleteKnowledgeDocument(kbId: string, docId: string) {
  return api.delete<void, void>(`/knowledge-base/${kbId}/docs/${docId}`);
}

/**
 * 切换文档启用/禁用状态
 * @param kbId - 知识库 ID
 * @param docId - 文档 ID
 * @param enabled - 启用状态（1=启用，0=禁用）
 */
export async function toggleDocumentEnabled(kbId: string, docId: string, enabled: number) {
  return api.put<void, void>(`/knowledge-base/${kbId}/docs/${docId}/enabled`, { enabled });
}

/**
 * 触发文档分块解析
 * @param docId - 文档 ID
 * @param payload - 可选的分块策略和分块配置
 */
export async function triggerDocumentChunk(docId: string, payload?: { chunkStrategy?: string; chunkConfig?: string }) {
  return api.post<void, void>(`/documents/parse/${docId}`, payload);
}

/**
 * 获取文档的分块列表（通用接口）
 * @param docId - 文档 ID
 * @param pageNo - 页码
 * @param pageSize - 每页大小
 * @returns 分页分块列表
 */
export async function getDocumentChunks(docId: string, pageNo: number, pageSize: number) {
  return api.get<PageResult<DocumentChunkItem>, PageResult<DocumentChunkItem>>(`/documents/${docId}/chunks`, {
    params: { page: pageNo, size: pageSize },
  });
}

/**
 * 获取知识库文档的分块列表（知识库专用接口）
 * @param docId - 文档 ID
 * @param pageNo - 页码
 * @param pageSize - 每页大小
 * @returns 分页知识分块列表
 */
export async function getKnowledgeDocumentChunks(docId: string, pageNo: number, pageSize: number) {
  return api.get<PageResult<KnowledgeChunkItem>, PageResult<KnowledgeChunkItem>>(
    `/knowledge-base/docs/${docId}/chunks`,
    { params: { pageNo, pageSize } },
  );
}

/**
 * 更新知识库文档分块内容
 * @param docId - 文档 ID
 * @param chunkId - 分块 ID
 * @param content - 新的分块内容
 * @returns 更新后的分块信息
 */
export async function updateKnowledgeDocumentChunk(docId: string, chunkId: string, content: string) {
  return api.put<KnowledgeChunkItem, KnowledgeChunkItem>(
    `/knowledge-base/docs/${docId}/chunks/${chunkId}`,
    { content },
  );
}
