/**
 * 导购流式对话Hook。
 * 管理对话状态、SSE事件处理、消息历史和会话持久化（localStorage）。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as guideApi from '../../../services/guide';
import * as guideHistory from '../../../services/guideHistory';
import type {
  GuideAgentTimelineItem,
  GuideBusinessSignalCoverage,
  GuideCitation,
  GuideIntentPayload,
  GuideMessage,
  GuideProductCard,
  GuideSession,
  GuideTraceStep,
  GuideImageRef,
} from '../../../types';
import {
  analyzeBusinessSignalCoverage,
  applyAgentCancel,
  applyAgentFinish,
  applyAgentPlan,
  applyToolCall,
  applyToolObservation,
  buildReasonableFallbackReply,
  createGuideOutboundText,
  hydrateGuideWorkbenchFromSession,
  stepsToTimeline,
} from './guideWorkbenchModel';

const restoreSessionKey = 'devbrain.guide.restoreSessionId';
const recentSessionKey = 'devbrain.guide.recentSessionId';

export function useGuideStream() {
  const [messages, setMessages] = useState<GuideMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [currentRunId, setCurrentRunId] = useState<string | null>(null);
  const [currentIntent, setCurrentIntent] = useState<GuideIntentPayload | null>(null);
  const [currentProducts, setCurrentProducts] = useState<GuideProductCard[]>([]);
  const [citations, setCitations] = useState<GuideCitation[]>([]);
  const [traces, setTraces] = useState<GuideTraceStep[]>([]);
  const [agentTimeline, setAgentTimeline] = useState<GuideAgentTimelineItem[]>([]);
  const [activeStepNo, setActiveStepNo] = useState<number | null>(null);
  const [serverBackedSessions, setServerBackedSessions] = useState(false);
  const [offlineCache, setOfflineCache] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);
  const [sessions, setSessions] = useState<GuideSession[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [statusText, setStatusText] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const messagesRef = useRef<GuideMessage[]>([]);
  const assistantIdRef = useRef<string | null>(null);
  const latestSessionIdRef = useRef<string | null>(null);
  const latestConversationIdRef = useRef<string | null>(null);
  const latestRunIdRef = useRef<string | null>(null);

  const syncMessages = useCallback((next: GuideMessage[]) => {
    messagesRef.current = next;
    setMessages(next);
  }, []);

  const refreshSessions = useCallback(async () => {
    const cachedSessions = guideHistory.readGuideSessions();
    setSessions(cachedSessions);
    try {
      const page = await guideApi.getGuideSessions();
      const remoteSessions = page.records || [];
      setSessions(remoteSessions);
      guideHistory.writeGuideSessions(remoteSessions);
      setServerBackedSessions(true);
      setOfflineCache(false);
      return remoteSessions;
    } catch {
      setSessions(cachedSessions);
      setServerBackedSessions(false);
      setOfflineCache(cachedSessions.length > 0);
      return cachedSessions;
    }
  }, []);

  useEffect(() => {
    const cachedSessions = guideHistory.readGuideSessions();
    setSessions(cachedSessions);
    refreshSessions();
    const restoredSessionId = sessionStorage.getItem(restoreSessionKey) || localStorage.getItem(recentSessionKey);
    if (restoredSessionId && cachedSessions.some((session) => session.sessionId === restoredSessionId && !session.archived)) {
      sessionStorage.removeItem(restoreSessionKey);
      setCurrentSessionId(restoredSessionId);
      latestSessionIdRef.current = restoredSessionId;
      syncMessages(guideHistory.readGuideMessages(restoredSessionId));
    }
    return () => abortRef.current?.abort();
  }, [refreshSessions, syncMessages]);

  const startNewSession = useCallback(() => {
    if (streaming) return;
    setCurrentSessionId(null);
    setCurrentConversationId(null);
    setCurrentRunId(null);
    setCurrentIntent(null);
    latestSessionIdRef.current = null;
    latestConversationIdRef.current = null;
    latestRunIdRef.current = null;
    setCurrentProducts([]);
    setCitations([]);
    setTraces([]);
    setAgentTimeline([]);
    setActiveStepNo(null);
    setError(null);
    setStatusText(null);
    setFeedbackMessage(null);
    syncMessages([]);
  }, [streaming, syncMessages]);

  const resetCurrentSession = useCallback(() => {
    setCurrentSessionId(null);
    setCurrentConversationId(null);
    setCurrentRunId(null);
    setCurrentIntent(null);
    latestSessionIdRef.current = null;
    latestConversationIdRef.current = null;
    latestRunIdRef.current = null;
    setCurrentProducts([]);
    setCitations([]);
    setTraces([]);
    setAgentTimeline([]);
    setActiveStepNo(null);
    setError(null);
    setStatusText(null);
    setFeedbackMessage(null);
    syncMessages([]);
  }, [syncMessages]);

  const openSession = useCallback((sessionId: string) => {
    if (streaming) return;
    const cached = guideHistory.readGuideMessages(sessionId);
    setCurrentSessionId(sessionId);
    setCurrentConversationId(sessionId);
    latestSessionIdRef.current = sessionId;
    latestConversationIdRef.current = sessionId;
    localStorage.setItem(recentSessionKey, sessionId);
    setCurrentRunId(null);
    setCurrentIntent(null);
    setCurrentProducts([]);
    setCitations([]);
    setTraces([]);
    setAgentTimeline([]);
    setActiveStepNo(null);
    setError(null);
    setStatusText(null);
    setFeedbackMessage(null);
    syncMessages(cached);
    guideApi.getGuideSessionDetail(sessionId)
      .then((detail) => {
        const remoteMessages = detail.messages || [];
        const hydrated = hydrateGuideWorkbenchFromSession(detail);
        setCurrentConversationId(detail.conversationId || detail.sessionId);
        latestConversationIdRef.current = detail.conversationId || detail.sessionId;
        if (remoteMessages.length > 0) {
          syncMessages(remoteMessages);
          guideHistory.writeGuideMessages(sessionId, remoteMessages);
        }
        setCurrentIntent(hydrated.intent);
        setCurrentRunId(hydrated.currentRunId);
        latestRunIdRef.current = hydrated.currentRunId;
        setCurrentProducts(hydrated.products);
        setCitations(hydrated.citations);
        if (hydrated.currentRunId) {
          guideApi.listGuideRunSteps(hydrated.currentRunId)
            .then((steps) => setAgentTimeline(stepsToTimeline(steps)))
            .catch(() => undefined);
        }
      })
      .catch(() => {
        syncMessages(cached);
        setOfflineCache(cached.length > 0);
      });
  }, [streaming, syncMessages]);

  const archiveSession = useCallback(async (sessionId: string) => {
    if (streaming) return;
    setError(null);
    const cachedSession = guideHistory.readGuideSessions().find((session) => session.sessionId === sessionId);
    try {
      await guideApi.archiveGuideSessionRemote(sessionId);
      const next = guideHistory.archiveGuideSession(sessionId);
      setSessions(next);
      await refreshSessions();
      if (currentSessionId === sessionId) {
        resetCurrentSession();
      }
    } catch (archiveError) {
      if (!serverBackedSessions && cachedSession) {
        const next = guideHistory.archiveGuideSession(sessionId);
        setSessions(next);
        if (currentSessionId === sessionId) {
          resetCurrentSession();
        }
        setOfflineCache(true);
        setError('服务端归档失败，已仅归档当前浏览器缓存。');
        return;
      }
      setError(archiveError instanceof Error ? archiveError.message : '导购会话归档失败');
    }
  }, [currentSessionId, refreshSessions, resetCurrentSession, serverBackedSessions, streaming]);

  const restoreSession = useCallback(async (sessionId: string) => {
    if (streaming) return;
    setError(null);
    try {
      await guideApi.restoreGuideSessionRemote(sessionId);
      const next = guideHistory.restoreGuideSession(sessionId);
      setSessions(next);
      await refreshSessions();
    } catch (restoreError) {
      const cachedSession = guideHistory.readGuideSessions().find((session) => session.sessionId === sessionId);
      if (!serverBackedSessions && cachedSession) {
        const next = guideHistory.restoreGuideSession(sessionId);
        setSessions(next);
        setOfflineCache(true);
        setError('服务端恢复失败，已仅恢复当前浏览器缓存。');
        return;
      }
      setError(restoreError instanceof Error ? restoreError.message : '导购会话恢复失败');
    }
  }, [refreshSessions, serverBackedSessions, streaming]);

  const deleteSession = useCallback(async (sessionId: string) => {
    if (streaming) return;
    setError(null);
    const beforeDelete = sessions;
    const next = guideHistory.deleteGuideSession(sessionId);
    setSessions(next);
    if (currentSessionId === sessionId) {
      resetCurrentSession();
    }
    try {
      await guideApi.deleteGuideSessionRemote(sessionId);
      await refreshSessions();
    } catch (deleteError) {
      guideHistory.writeGuideSessions(beforeDelete);
      setSessions(beforeDelete);
      setError(deleteError instanceof Error ? deleteError.message : '导购会话删除失败');
    }
  }, [currentSessionId, refreshSessions, resetCurrentSession, sessions, streaming]);

  const sendMessage = useCallback(async (text: string, images: GuideImageRef[] = []) => {
    const message = createGuideOutboundText(text, images.length);
    if ((!message && images.length === 0) || streaming) return;
    const userId = `guide-user-${Date.now()}`;
    const assistantId = `guide-assistant-${Date.now()}`;
    assistantIdRef.current = assistantId;
    setStreaming(true);
    setError(null);
    setStatusText('正在连接导购服务');
    setFeedbackMessage(null);
    setCurrentIntent(null);
    setCurrentRunId(null);
    latestRunIdRef.current = null;
    setCurrentProducts([]);
    setCitations([]);
    setTraces([]);
    setAgentTimeline([]);
    setActiveStepNo(null);
    const nextMessages: GuideMessage[] = [
      ...messagesRef.current,
      { id: userId, role: 'user', content: message, images, createTime: new Date().toISOString() },
      { id: assistantId, role: 'assistant', content: '', createTime: new Date().toISOString(), streaming: true },
    ];
    syncMessages(nextMessages);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await guideApi.streamGuideChat({
        sessionId: currentSessionId,
        conversationId: currentConversationId || currentSessionId,
        message,
        imageIds: images.map((image) => image.imageId),
        clientMessageId: userId,
        scene: 'shopping_guide',
      }, {
        onSession: (payload) => {
          setCurrentSessionId(payload.sessionId);
          setCurrentConversationId(payload.conversationId);
          setCurrentRunId(payload.runId || null);
          latestSessionIdRef.current = payload.sessionId;
          latestConversationIdRef.current = payload.conversationId;
          latestRunIdRef.current = payload.runId || null;
          localStorage.setItem(recentSessionKey, payload.sessionId);
          guideHistory.upsertGuideSession(payload.sessionId, message, message, payload.runId);
        },
        onIntent: (payload) => {
          setCurrentIntent(payload);
        },
        onSearching: (payload) => setStatusText(payload),
        onClarification: (payload) => setStatusText(payload.question),
        onAgentPlan: (payload) => {
          setStatusText(`第 ${payload.stepNo} 步：${payload.action || '规划中'}`);
          setCurrentRunId(payload.runId);
          latestRunIdRef.current = payload.runId;
          setActiveStepNo(payload.stepNo);
          setAgentTimeline((prev) => applyAgentPlan(prev, payload));
        },
        onToolCall: (payload) => {
          setStatusText(`正在调用工具：${payload.toolName}`);
          setActiveStepNo(payload.stepNo);
          setAgentTimeline((prev) => applyToolCall(prev, payload));
        },
        onToolObservation: (payload) => {
          setAgentTimeline((prev) => applyToolObservation(prev, payload));
          setTraces((prev) => [...prev, {
            node: `agent:${payload.toolName || 'tool'}`,
            inputSummary: `step=${payload.stepNo}`,
            outputSummary: payload.observation || '',
            durationMs: payload.durationMs,
            error: payload.error || null,
          }]);
        },
        onAgentFinish: (payload) => {
          setStatusText(payload.status === 'completed' ? null : `Agent 状态：${payload.status}`);
          setAgentTimeline((prev) => applyAgentFinish(prev, payload));
          setActiveStepNo(null);
        },
        onCancel: (payload) => {
          setStatusText(payload.message || '已停止生成');
          setAgentTimeline((prev) => applyAgentCancel(prev, payload.runId || latestRunIdRef.current));
          setActiveStepNo(null);
        },
        onProductCard: (product) => {
          setCurrentProducts((prev) => upsertById(prev, product.productId, product));
        },
        onCitation: (citation) => setCitations((prev) => [...prev, citation]),
        onTrace: (trace) => setTraces((prev) => [...prev, trace]),
        onAnswerDelta: (delta) => {
          const target = assistantIdRef.current;
          if (!target) return;
          const updated = messagesRef.current.map((item) => (
            item.id === target ? { ...item, content: `${item.content}${delta}` } : item
          ));
          syncMessages(updated);
        },
        onError: (streamError) => {
          setError(streamError.message);
          const target = assistantIdRef.current;
          if (!target) return;
          const fallback = buildReasonableFallbackReply(message);
          const updated = messagesRef.current.map((item) => (
            item.id === target
              ? { ...item, content: item.content || fallback, streaming: false, errorMessage: streamError.message }
              : item
          ));
          syncMessages(updated);
        },
        onDone: () => {
          const target = assistantIdRef.current;
          const updated = messagesRef.current.map((item) => (
            item.id === target ? { ...item, streaming: false } : item
          ));
          syncMessages(updated);
          setStreaming(false);
          setStatusText(null);
          const sessionId = latestSessionIdRef.current || currentSessionId || guideHistory.readGuideSessions()[0]?.sessionId;
          if (sessionId) {
            guideHistory.writeGuideMessages(sessionId, updated);
          }
          if (sessionId) {
            guideApi.getGuideSessionDetail(sessionId)
              .then((detail) => {
                const hydrated = hydrateGuideWorkbenchFromSession(detail);
                if (hydrated.products.length > 0) setCurrentProducts(hydrated.products);
                if (hydrated.citations.length > 0) setCitations(hydrated.citations);
                if (hydrated.intent) setCurrentIntent(hydrated.intent);
                if (hydrated.currentRunId) {
                  setCurrentRunId(hydrated.currentRunId);
                  latestRunIdRef.current = hydrated.currentRunId;
                }
              })
              .catch(() => undefined);
          }
        },
      }, controller.signal);
    } catch (streamError) {
      const messageText = streamError instanceof Error ? streamError.message : '导购连接中断';
      setError(messageText);
      setStreaming(false);
      setStatusText(null);
    } finally {
      abortRef.current = null;
      assistantIdRef.current = null;
      setStreaming(false);
      setStatusText(null);
      setSessions(guideHistory.readGuideSessions());
      refreshSessions();
    }
  }, [currentConversationId, currentSessionId, refreshSessions, streaming, syncMessages]);

  const stop = useCallback(async () => {
    if (!currentSessionId) {
      abortRef.current?.abort();
      setStreaming(false);
      return;
    }
    await guideApi.stopGuideChat(currentSessionId);
    abortRef.current?.abort();
    setAgentTimeline((prev) => applyAgentCancel(prev, latestRunIdRef.current));
    setActiveStepNo(null);
    setStreaming(false);
  }, [currentSessionId]);

  const submitFeedback = useCallback(async (payload: {
    feedbackType: string;
    comment?: string | null;
    productId?: string | null;
    messageId?: string | null;
  }) => {
    const conversationId = currentConversationId || latestConversationIdRef.current || currentSessionId || latestSessionIdRef.current;
    if (!conversationId) {
      setFeedbackMessage('请先打开或完成一个导购会话。');
      return;
    }
    try {
      await guideApi.submitGuideFeedback({
        conversationId,
        messageId: payload.messageId || latestAssistantMessageId(messagesRef.current),
        productId: payload.productId || null,
        feedbackType: payload.feedbackType,
        comment: payload.comment || null,
      });
      setFeedbackMessage('反馈已提交，会进入评测闭环。');
    } catch (feedbackError) {
      setFeedbackMessage(feedbackError instanceof Error ? feedbackError.message : '反馈提交失败');
    }
  }, [currentConversationId, currentSessionId]);

  const businessSignals: GuideBusinessSignalCoverage = useMemo(() => (
    analyzeBusinessSignalCoverage(currentProducts, citations, currentIntent, agentTimeline)
  ), [agentTimeline, citations, currentIntent, currentProducts]);

  return useMemo(() => ({
    messages,
    streaming,
    currentSessionId,
    currentConversationId,
    currentRunId,
    currentIntent,
    currentProducts,
    citations,
    traces,
    agentTimeline,
    activeStepNo,
    serverBackedSessions,
    offlineCache,
    businessSignals,
    feedbackMessage,
    sessions,
    error,
    statusText,
    sendMessage,
    stop,
    startNewSession,
    openSession,
    archiveSession,
    restoreSession,
    deleteSession,
    submitFeedback,
  }), [
    messages,
    streaming,
    currentSessionId,
    currentConversationId,
    currentRunId,
    currentIntent,
    currentProducts,
    citations,
    traces,
    agentTimeline,
    activeStepNo,
    serverBackedSessions,
    offlineCache,
    businessSignals,
    feedbackMessage,
    sessions,
    error,
    statusText,
    sendMessage,
    stop,
    startNewSession,
    openSession,
    archiveSession,
    restoreSession,
    deleteSession,
    submitFeedback,
  ]);
}

function upsertById(items: GuideProductCard[], id: string, next: GuideProductCard) {
  const index = items.findIndex((item) => item.productId === id);
  if (index < 0) return [...items, next];
  return items.map((item) => (item.productId === id ? next : item));
}

function latestAssistantMessageId(messages: GuideMessage[]) {
  return [...messages].reverse().find((message) => message.role === 'assistant')?.id || null;
}
