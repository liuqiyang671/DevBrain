import { api } from './api';
import type {
  ExecuteIngestionTaskPayload,
  IngestionPipelineItem,
  IngestionPipelinePayload,
  IngestionResultItem,
  IngestionTaskItem,
  IngestionTaskNodeItem,
  PageResult,
} from '../types';

/**
 * 摄入 Pipeline API 模块
 * 封装流水线定义、任务执行和节点日志查询接口。
 */

/**
 * 分页查询流水线定义。
 */
export async function getPipelines(params: { pageNo: number; pageSize: number; keyword?: string }) {
  return api.get<PageResult<IngestionPipelineItem>, PageResult<IngestionPipelineItem>>('/ingestion/pipelines', {
    params: {
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
    },
  });
}

/**
 * 获取单个流水线定义详情。
 */
export async function getPipeline(id: string) {
  return api.get<IngestionPipelineItem, IngestionPipelineItem>(`/ingestion/pipelines/${id}`);
}

/**
 * 创建流水线定义。
 */
export async function createPipeline(payload: IngestionPipelinePayload) {
  return api.post<IngestionPipelineItem, IngestionPipelineItem>('/ingestion/pipelines', payload);
}

/**
 * 更新流水线定义。
 */
export async function updatePipeline(id: string, payload: IngestionPipelinePayload) {
  return api.put<IngestionPipelineItem, IngestionPipelineItem>(`/ingestion/pipelines/${id}`, payload);
}

/**
 * 删除流水线定义。
 */
export async function deletePipeline(id: string) {
  return api.delete<void, void>(`/ingestion/pipelines/${id}`);
}

/**
 * 创建并执行摄入任务。
 */
export async function executeTask(payload: ExecuteIngestionTaskPayload) {
  return api.post<IngestionResultItem, IngestionResultItem>('/ingestion/tasks', payload, { timeout: 0 });
}

/**
 * 分页查询摄入任务。
 */
export async function getTasks(params: { pageNo: number; pageSize: number; pipelineId?: string; status?: string }) {
  return api.get<PageResult<IngestionTaskItem>, PageResult<IngestionTaskItem>>('/ingestion/tasks', {
    params: {
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      pipelineId: params.pipelineId || undefined,
      status: params.status || undefined,
    },
  });
}

/**
 * 查询任务节点执行日志。
 */
export async function getTaskNodes(taskId: string) {
  return api.get<IngestionTaskNodeItem[], IngestionTaskNodeItem[]>(`/ingestion/tasks/${taskId}/nodes`);
}
