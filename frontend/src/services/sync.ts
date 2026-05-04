import { api } from './api';
import type { PageResult, ScheduleConfigPayload, SyncHistoryItem, SyncTaskOverviewItem, KnowledgeDocumentItem } from '../types';

export async function configureSchedule(kbId: string, docId: string, payload: ScheduleConfigPayload): Promise<KnowledgeDocumentItem> {
  const res = await api.put(`/knowledge-base/${kbId}/docs/${docId}/schedule`, payload);
  return res.data;
}

export async function triggerSync(docId: string): Promise<{ contentChanged: boolean; message: string }> {
  const res = await api.post(`/sync-tasks/${docId}/trigger`);
  return res.data;
}

export async function getSyncHistory(docId: string, pageNo: number, pageSize: number): Promise<PageResult<SyncHistoryItem>> {
  const res = await api.get(`/sync-tasks/${docId}/history`, { params: { pageNo, pageSize } });
  return res.data;
}

export async function getSyncTaskOverview(): Promise<SyncTaskOverviewItem[]> {
  const res = await api.get('/sync-tasks/overview');
  return res.data;
}
