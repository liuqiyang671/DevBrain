/**
 * 导购对话API服务层。
 * 提供基于SSE的流式导购对话、图片上传和会话管理能力。
 */
import { api, ensureCsrfToken } from './api';
import type {
  GuideChatRequest,
  AgentRunItem,
  AgentStepItem,
  AgentToolCallItem,
  GuideAgentFinishPayload,
  GuideAgentPlanPayload,
  GuideClarificationPayload,
  GuideCitation,
  GuideIntentPayload,
  GuideToolCallPayload,
  GuideToolObservationPayload,
  GuideProductCard,
  GuideSessionPayload,
  GuideSseEvent,
  GuideTraceStep,
  GuideImageRef,
  GuideSession,
  GuideSessionDetail,
  GuideSessionPage,
  GuidePersistedRecommendation,
  LlmCallLogItem,
} from '../types';
import type { GuideFeedbackItem } from './evaluation';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090/api/devbrain';

export interface GuideStreamHandlers {
  onSession?: (payload: GuideSessionPayload) => void;
  onIntent?: (payload: GuideIntentPayload) => void;
  onClarification?: (payload: GuideClarificationPayload) => void;
  onSearching?: (message: string) => void;
  onProductCard?: (payload: GuideProductCard) => void;
  onCitation?: (payload: GuideCitation) => void;
  onAnswerDelta?: (delta: string) => void;
  onAnswerDone?: () => void;
  onTrace?: (payload: GuideTraceStep) => void;
  onAgentPlan?: (payload: GuideAgentPlanPayload) => void;
  onToolCall?: (payload: GuideToolCallPayload) => void;
  onToolObservation?: (payload: GuideToolObservationPayload) => void;
  onAgentFinish?: (payload: GuideAgentFinishPayload) => void;
  onCancel?: (payload: { runId?: string; message?: string }) => void;
  onDone?: () => void;
  onError?: (error: Error) => void;
}

export async function streamGuideChat(payload: GuideChatRequest, handlers: GuideStreamHandlers, signal?: AbortSignal) {
  const csrf = await ensureCsrfToken();
  const response = await fetch(`${API_BASE_URL}/commerce/guide/chat/stream`, {
    method: 'POST',
    credentials: 'include',
    signal,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'X-XSRF-TOKEN': csrf,
    },
    body: JSON.stringify(payload),
  });
  if (!response.ok || !response.body) {
    throw new Error(`导购连接失败：${response.status}`);
  }
  await readSseStream(response.body, handlers);
}

export async function stopGuideChat(sessionId: string) {
  return api.post<void, void>('/commerce/guide/chat/stop', null, { params: { sessionId } });
}

export function getAgentRun(runId: string) {
  return api.get<AgentRunItem, AgentRunItem>(`/commerce/guide/runs/${runId}`);
}

export function getAgentRunSteps(runId: string) {
  return api.get<AgentStepItem[], AgentStepItem[]>(`/commerce/guide/runs/${runId}/steps`);
}

export function getAgentRunToolCalls(runId: string) {
  return api.get<AgentToolCallItem[], AgentToolCallItem[]>(`/commerce/guide/runs/${runId}/tool-calls`);
}

export function getAgentRunLlmCalls(runId: string) {
  return api.get<LlmCallLogItem[], LlmCallLogItem[]>(`/commerce/guide/runs/${runId}/llm-calls`);
}

export async function uploadGuideImage(file: File, sessionId?: string | null) {
  const formData = new FormData();
  formData.append('file', file);
  if (sessionId) {
    formData.append('sessionId', sessionId);
  }
  const image = await api.post<GuideImageRef, GuideImageRef>('/commerce/guide/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return normalizeGuideImage(image);
}

export async function getGuideSessions(pageNo = 1, pageSize = 50) {
  const page = await api.get<GuideSessionPage, GuideSessionPage>('/commerce/guide/sessions', {
    params: { pageNo, pageSize },
  });
  return {
    ...page,
    records: (page.records || []).map(normalizeGuideSession),
  };
}

export async function getGuideSessionDetail(sessionId: string) {
  const detail = await api.get<GuideSessionDetail, GuideSessionDetail>(`/commerce/guide/sessions/${sessionId}`);
  return normalizeGuideSessionDetail(detail);
}

export async function getGuideSessionMessages(sessionId: string) {
  const messages = await api.get<GuideSessionDetail['messages'], GuideSessionDetail['messages']>(
    `/commerce/guide/sessions/${sessionId}/messages`,
  );
  return (messages || []).map(normalizeGuideMessage);
}

export async function listGuideRecommendations(sessionId: string) {
  const recommendations = await api.get<GuidePersistedRecommendation[], GuidePersistedRecommendation[]>(
    `/commerce/guide/sessions/${sessionId}/recommendations`,
  );
  return recommendations || [];
}

export async function listGuideRunSteps(runId: string) {
  return getAgentRunSteps(runId);
}

export async function submitGuideFeedback(payload: {
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
}) {
  return api.post<GuideFeedbackItem, GuideFeedbackItem>('/commerce/guide/feedback', payload);
}

export async function archiveGuideSessionRemote(sessionId: string) {
  return api.post<void, void>(`/commerce/guide/sessions/${sessionId}/archive`);
}

export async function restoreGuideSessionRemote(sessionId: string) {
  return api.post<void, void>(`/commerce/guide/sessions/${sessionId}/restore`);
}

export async function deleteGuideSessionRemote(sessionId: string) {
  return api.delete<void, void>(`/commerce/guide/sessions/${sessionId}`);
}

async function readSseStream(body: ReadableStream<Uint8Array>, handlers: GuideStreamHandlers) {
  const reader = body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || '';
    for (const eventText of events) {
      dispatchEventBlock(eventText, handlers);
    }
  }
  if (buffer.trim()) {
    dispatchEventBlock(buffer, handlers);
  }
}

function dispatchEventBlock(block: string, handlers: GuideStreamHandlers) {
  const lines = block.split(/\r?\n/);
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim();
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  if (!eventName || !data) return;
  const event = parseGuideEvent(data);
  if (!event) return;
  switch (eventName) {
    case 'session':
      handlers.onSession?.(event.payload as GuideSessionPayload);
      break;
    case 'intent':
      handlers.onIntent?.(event.payload as GuideIntentPayload);
      break;
    case 'clarification':
      handlers.onClarification?.(event.payload as GuideClarificationPayload);
      break;
    case 'searching':
      handlers.onSearching?.(String(event.payload || ''));
      break;
    case 'product_card':
      handlers.onProductCard?.(event.payload as GuideProductCard);
      break;
    case 'citation':
      handlers.onCitation?.(event.payload as GuideCitation);
      break;
    case 'answer_delta':
      handlers.onAnswerDelta?.(String(event.payload || ''));
      break;
    case 'answer_done':
      handlers.onAnswerDone?.();
      break;
    case 'trace':
      handlers.onTrace?.(event.payload as GuideTraceStep);
      break;
    case 'agent_plan':
      handlers.onAgentPlan?.(event.payload as GuideAgentPlanPayload);
      break;
    case 'tool_call':
      handlers.onToolCall?.(event.payload as GuideToolCallPayload);
      break;
    case 'tool_observation':
      handlers.onToolObservation?.(event.payload as GuideToolObservationPayload);
      break;
    case 'agent_finish':
      handlers.onAgentFinish?.(event.payload as GuideAgentFinishPayload);
      break;
    case 'cancel':
      handlers.onCancel?.(event.payload as { runId?: string; message?: string });
      break;
    case 'error': {
      const message = typeof event.payload === 'object' && event.payload && 'message' in event.payload
        ? String((event.payload as { message?: string }).message || '导购请求失败')
        : '导购请求失败';
      handlers.onError?.(new Error(message));
      break;
    }
    case 'done':
      handlers.onDone?.();
      break;
    default:
      break;
  }
}

function parseGuideEvent(data: string) {
  try {
    return JSON.parse(data) as GuideSseEvent;
  } catch {
    return null;
  }
}

function normalizeGuideImage(image: GuideImageRef): GuideImageRef {
  if (!image.previewUrl || image.previewUrl.startsWith('http') || image.previewUrl.startsWith('blob:')) {
    return image;
  }
  const base = API_BASE_URL.replace(/\/+$/, '');
  const path = image.previewUrl.startsWith('/') ? image.previewUrl : `/${image.previewUrl}`;
  return { ...image, previewUrl: `${base}${path}` };
}

function normalizeGuideSession(session: GuideSession): GuideSession {
  return {
    ...session,
    sessionId: session.sessionId || session.conversationId || '',
    lastTime: session.lastTime || session.updateTime || session.createTime || new Date().toISOString(),
    title: session.title || session.lastMessage || '导购会话',
    runId: session.runId || latestMessageRunId((session as GuideSessionDetail).messages) || null,
    archived: Boolean(session.archived),
    archivedTime: session.archivedTime || null,
    summary: session.summary || null,
  };
}

function normalizeGuideSessionDetail(detail: GuideSessionDetail): GuideSessionDetail {
  const session = normalizeGuideSession(detail);
  return {
    ...detail,
    ...session,
    messages: (detail.messages || []).map(normalizeGuideMessage),
    recommendations: detail.recommendations || [],
    state: detail.state || null,
  };
}

function normalizeGuideMessage(message: NonNullable<GuideSessionDetail['messages']>[number]) {
  return {
    ...message,
    createTime: message.createTime || new Date().toISOString(),
  };
}

function latestMessageRunId(messages?: GuideSessionDetail['messages']) {
  return [...(messages || [])].reverse().find((message) => Boolean(message.agentRunId))?.agentRunId || null;
}
