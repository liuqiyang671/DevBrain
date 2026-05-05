import { api } from './api';
import type { PageResult, ScheduleConfigPayload, SyncHistoryItem, SyncTaskOverviewItem, KnowledgeDocumentItem } from '../types';

/**
 * 文档同步 API 模块
 * 提供定时同步配置、手动触发同步、同步历史查询和任务概览功能
 */

/**
 * 配置文档定时同步计划
 * @param kbId - 知识库 ID
 * @param docId - 文档 ID
 * @param payload - 定时配置参数（来源类型、来源地址、是否启用定时、Cron 表达式）
 * @returns 更新后的文档信息
 */
export async function configureSchedule(kbId: string, docId: string, payload: ScheduleConfigPayload): Promise<KnowledgeDocumentItem> {
  const res = await api.put(`/knowledge-base/${kbId}/docs/${docId}/schedule`, payload);
  return res.data;
}

/**
 * 手动触发文档同步
 * @param docId - 文档 ID
 * @returns 同步结果（内容是否变化、提示消息）
 */
export async function triggerSync(docId: string): Promise<{ contentChanged: boolean; message: string }> {
  const res = await api.post(`/sync-tasks/${docId}/trigger`);
  return res.data;
}

/**
 * 获取文档的同步历史记录
 * @param docId - 文档 ID
 * @param pageNo - 页码
 * @param pageSize - 每页大小
 * @returns 分页同步历史列表
 */
export async function getSyncHistory(docId: string, pageNo: number, pageSize: number): Promise<PageResult<SyncHistoryItem>> {
  const res = await api.get(`/sync-tasks/${docId}/history`, { params: { pageNo, pageSize } });
  return res.data;
}

/**
 * 获取所有同步任务概览
 * @returns 同步任务概览数组，包含文档信息、定时配置和最后同步时间
 */
export async function getSyncTaskOverview(): Promise<SyncTaskOverviewItem[]> {
  const res = await api.get('/sync-tasks/overview');
  return res.data;
}
