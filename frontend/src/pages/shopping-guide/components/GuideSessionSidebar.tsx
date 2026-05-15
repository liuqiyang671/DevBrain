/**
 * Guide session sidebar with current guide session management.
 */
import type { MouseEvent, ReactNode } from 'react';
import type { GuideSession } from '../../../types';

interface GuideSessionSidebarProps {
  sessions: GuideSession[];
  activeSessionId: string | null;
  streaming: boolean;
  serverBacked: boolean;
  offlineCache: boolean;
  onNew: () => void;
  onOpen: (sessionId: string) => void;
  onArchive: (sessionId: string) => void;
  onDelete: (sessionId: string) => void;
}

export function GuideSessionSidebar({
  sessions,
  activeSessionId,
  streaming,
  serverBacked,
  offlineCache,
  onNew,
  onOpen,
  onArchive,
  onDelete,
}: GuideSessionSidebarProps) {
  const activeSessions = sessions.filter((session) => !session.archived);

  return (
    <aside className="guide-session-sidebar">
      <button className="btn btn-primary" type="button" onClick={onNew} disabled={streaming}>新导购</button>

      <div className="guide-sidebar-title">
        <h3>当前会话</h3>
        <span>{serverBacked ? '服务端' : offlineCache ? '离线缓存' : activeSessions.length}</span>
      </div>
      <SessionList
        sessions={activeSessions}
        activeSessionId={activeSessionId}
        streaming={streaming}
        emptyText="暂无当前会话"
        onOpen={onOpen}
        actions={(session) => (
          <>
            <button type="button" onClick={(event) => handleActionClick(event, () => onArchive(session.sessionId))} disabled={streaming}>归档</button>
            <button type="button" className="danger" onClick={(event) => handleActionClick(event, () => onDelete(session.sessionId))} disabled={streaming}>删除</button>
          </>
        )}
      />
    </aside>
  );
}

function SessionList({
  sessions,
  activeSessionId,
  streaming,
  emptyText,
  onOpen,
  actions,
}: {
  sessions: GuideSession[];
  activeSessionId: string | null;
  streaming: boolean;
  emptyText: string;
  onOpen: (sessionId: string) => void;
  actions: (session: GuideSession) => ReactNode;
}) {
  if (sessions.length === 0) {
    return <div className="guide-panel-empty">{emptyText}</div>;
  }

  return (
    <div className="guide-session-list">
      {sessions.map((session) => (
        <article
          className={`guide-session-item${session.sessionId === activeSessionId ? ' active' : ''}`}
          key={session.sessionId}
        >
          <button
            type="button"
            className="guide-session-open"
            onClick={() => onOpen(session.sessionId)}
            disabled={streaming}
          >
            <strong>{session.title}</strong>
            <span>{session.lastMessage || '导购会话'}</span>
            <small>{session.messageCount ? `${session.messageCount} 条消息` : formatSessionTime(session.lastTime)}</small>
          </button>
          <div className="guide-session-actions">
            {actions(session)}
          </div>
        </article>
      ))}
    </div>
  );
}

function formatSessionTime(value?: string | null) {
  if (!value) return '刚刚';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚';
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}

function handleActionClick(event: MouseEvent<HTMLButtonElement>, action: () => void) {
  event.preventDefault();
  event.stopPropagation();
  action();
}
