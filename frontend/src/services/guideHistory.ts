import type { GuideMessage, GuideSession } from '../types';

/** AI 导购本地会话摘要缓存键；后续可替换为服务端历史列表接口。 */
export const guideSessionStoreKey = 'devbrain.guide.sessions.v1';

const maxGuideSessions = 80;
const maxGuideMessages = 120;

export function readGuideSessions(): GuideSession[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(guideSessionStoreKey);
    const parsed = raw ? JSON.parse(raw) as GuideSession[] : [];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item) => item?.sessionId)
      .sort((left, right) => new Date(right.lastTime || 0).getTime() - new Date(left.lastTime || 0).getTime());
  } catch {
    return [];
  }
}

export function writeGuideSessions(items: GuideSession[]) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(guideSessionStoreKey, JSON.stringify(items.slice(0, maxGuideSessions)));
  } catch {
    // Local guide history is a convenience cache; chat messages remain usable even if cache persistence fails.
  }
}

export function readGuideMessages(sessionId: string): GuideMessage[] {
  if (typeof window === 'undefined' || !sessionId) return [];
  try {
    const raw = window.localStorage.getItem(guideMessageKey(sessionId));
    const parsed = raw ? JSON.parse(raw) as GuideMessage[] : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function writeGuideMessages(sessionId: string, messages: GuideMessage[]) {
  if (typeof window === 'undefined' || !sessionId) return;
  try {
    window.localStorage.setItem(guideMessageKey(sessionId), JSON.stringify(messages.slice(-maxGuideMessages)));
  } catch {
    // Best-effort local cache only.
  }
}

export function upsertGuideSession(sessionId: string, title: string, lastMessage: string, runId?: string | null) {
  const sessions = readGuideSessions();
  const existing = sessions.find((item) => item.sessionId === sessionId);
  const messages = readGuideMessages(sessionId);
  const next: GuideSession = {
    ...existing,
    sessionId,
    title: normalizeGuideTitle(title || existing?.title || '导购会话'),
    lastMessage,
    lastTime: new Date().toISOString(),
    runId: runId ?? existing?.runId,
    archived: false,
    archivedTime: null,
    summary: existing?.summary || null,
    messageCount: messages.length,
  };
  const merged = [
    next,
    ...sessions.filter((item) => item.sessionId !== sessionId),
  ].sort((left, right) => new Date(right.lastTime || 0).getTime() - new Date(left.lastTime || 0).getTime());
  writeGuideSessions(merged);
  return merged;
}

export function archiveGuideSession(sessionId: string) {
  const messages = readGuideMessages(sessionId);
  return setGuideSessionArchived(sessionId, true, summarizeGuideSession(messages));
}

export function restoreGuideSession(sessionId: string) {
  return setGuideSessionArchived(sessionId, false);
}

export function deleteGuideSession(sessionId: string) {
  if (typeof window === 'undefined' || !sessionId) return readGuideSessions();
  const next = readGuideSessions().filter((session) => session.sessionId !== sessionId);
  writeGuideSessions(next);
  window.localStorage.removeItem(guideMessageKey(sessionId));
  return next;
}

export function deleteArchivedGuideSessions() {
  if (typeof window === 'undefined') return [];
  const sessions = readGuideSessions();
  const archivedIds = sessions.filter((session) => session.archived).map((session) => session.sessionId);
  const next = sessions.filter((session) => !session.archived);
  writeGuideSessions(next);
  archivedIds.forEach((sessionId) => window.localStorage.removeItem(guideMessageKey(sessionId)));
  return next;
}

export function getGuideSessionTitle(item?: GuideSession | null) {
  return item?.title || item?.lastMessage || '未命名导购会话';
}

export function summarizeGuideSession(messages: GuideMessage[]) {
  const userMessages = messages
    .filter((message) => message.role === 'user' && message.content.trim())
    .map((message) => message.content.trim());
  const assistantMessages = messages
    .filter((message) => message.role === 'assistant' && message.content.trim())
    .map((message) => message.content.trim());
  const topic = userMessages[0] || '导购会话';
  const answer = assistantMessages[assistantMessages.length - 1];
  const summary = answer ? `${topic}；${answer}` : topic;
  return summary.length > 88 ? `${summary.slice(0, 88)}...` : summary;
}

function setGuideSessionArchived(sessionId: string, archived: boolean, summary?: string | null) {
  const next = readGuideSessions().map((session) => (
    session.sessionId === sessionId
      ? {
        ...session,
        archived,
        archivedTime: archived ? new Date().toISOString() : null,
        summary: archived ? summary || session.summary || session.lastMessage || null : session.summary || null,
        messageCount: readGuideMessages(sessionId).length,
      }
      : session
  ));
  writeGuideSessions(next);
  return next;
}

function normalizeGuideTitle(value: string) {
  const normalized = value.trim() || '导购会话';
  return normalized.length > 24 ? `${normalized.slice(0, 24)}...` : normalized;
}

function guideMessageKey(sessionId: string) {
  return `${guideSessionStoreKey}.${sessionId}.messages`;
}
