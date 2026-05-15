/**
 * 评测管理API服务层。
 * 提供评测数据集、评测运行、导购反馈的CRUD和审核接口。
 */
import { api } from './api';
import type { PageResult } from '../types';

export interface EvaluationDatasetItem {
  id: string;
  name: string;
  description?: string | null;
  status: string;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface EvaluationRunItem {
  id: string;
  datasetId: string;
  promptVersion?: string | null;
  status: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  progress?: Record<string, unknown> | null;
  caseCount?: number | null;
  completedCaseCount?: number | null;
  failedCaseCount?: number | null;
  summaryMetrics?: Record<string, unknown> | null;
}

export interface EvaluationCaseResultItem {
  id: string;
  caseId: string;
  answer?: string | null;
  score?: Record<string, unknown> | null;
  agentRunId?: string | null;
  failureType?: string | null;
  latencyMs?: number | null;
  expected?: Record<string, unknown> | null;
  actual?: Record<string, unknown> | null;
  debugHints?: string[] | null;
  errorMessage?: string | null;
}

export interface EvaluationReport {
  runId: string;
  datasetId: string;
  status: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  summaryMetrics?: Record<string, unknown> | null;
  caseResults?: EvaluationCaseResultItem[] | null;
  failedCases?: EvaluationCaseResultItem[] | null;
  improvementHints?: string[] | null;
}

export interface GuideFeedbackItem {
  id: string;
  conversationId: string;
  messageId?: string | null;
  productId?: string | null;
  feedbackType: string;
  comment?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  agentRunId?: string | null;
  stepId?: string | null;
  evidenceId?: string | null;
  reasonIndex?: number | null;
  reviewStatus: string;
  reviewResult?: string | null;
  improvementSuggestion?: string | null;
  createTime?: string | null;
}

export interface DatasetPayload {
  name: string;
  description?: string;
  status?: string;
}

export function listEvaluationDatasets(params: { pageNo: number; pageSize: number; keyword?: string }) {
  return api.get<PageResult<EvaluationDatasetItem>, PageResult<EvaluationDatasetItem>>('/commerce/evaluations/datasets', { params });
}

export function createEvaluationDataset(payload: DatasetPayload) {
  return api.post<EvaluationDatasetItem, EvaluationDatasetItem>('/commerce/evaluations/datasets', payload);
}

export function listEvaluationRuns(params: { pageNo: number; pageSize: number; datasetId?: string }) {
  return api.get<PageResult<EvaluationRunItem>, PageResult<EvaluationRunItem>>('/commerce/evaluations/runs', { params });
}

export function runEvaluation(payload: { datasetId: string; promptVersion?: string }) {
  return api.post<EvaluationRunItem, EvaluationRunItem>('/commerce/evaluations/runs', payload);
}

export function getEvaluationReport(runId: string) {
  return api.get<EvaluationReport, EvaluationReport>(`/commerce/evaluations/runs/${runId}/report`);
}

export function cancelEvaluationRun(runId: string) {
  return api.post<EvaluationRunItem, EvaluationRunItem>(`/commerce/evaluations/runs/${runId}/cancel`);
}

export function listGuideFeedback(params: { pageNo: number; pageSize: number; reviewStatus?: string }) {
  return api.get<PageResult<GuideFeedbackItem>, PageResult<GuideFeedbackItem>>('/commerce/guide/feedback', { params });
}

export function reviewGuideFeedback(feedbackId: string, payload: { reviewStatus: string; reviewResult?: string }) {
  return api.put<GuideFeedbackItem, GuideFeedbackItem>(`/commerce/guide/feedback/${feedbackId}/review`, payload);
}
