# RAG 问答前端实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 RAG 问答链路的前台聊天页面和后台链路调试页面，并通过前端服务层预留后端接口契约。

**架构：** 前端新增 `rag.ts` 服务层封装约定接口，页面只依赖服务层和类型定义。`/assistant` 负责普通聊天体验，`/admin/qa` 负责检索、Prompt、Chat 三段调试视图。后端 Controller 不在本计划范围内，接口未接通时前端展示错误态。

**技术栈：** React 18、TypeScript、Vite、Axios、现有 AppShell / PageContainer / CSS。

---

## 文件结构

- 修改：`frontend/src/types.ts`
  - 增加 RAG 会话、消息、引用、检索命中、Prompt 预览、聊天请求和调试请求类型。
- 创建：`frontend/src/services/rag.ts`
  - 封装 `GET /rag/conversations`、`GET /rag/conversations/{conversationId}`、`POST /rag/chat`、`POST /rag/debug/runs`。
- 修改：`frontend/src/App.tsx`
  - 引入 `ragApi` 和 RAG 类型。
  - 将 `/admin/qa` 路由改为 `AdminQaPage`。
  - 替换 `AssistantPage` 静态内容。
  - 新增 `AdminQaPage` 和若干小型渲染辅助组件。
- 修改：`frontend/src/styles.css`
  - 增加前台聊天主导布局和后台调试台样式。
  - 复用现有按钮、卡片、空态和状态徽章风格。
- 验证：`frontend` 构建和 `git diff --check`。

## 任务 1：定义 RAG 类型与服务契约

**文件：**

- 修改：`frontend/src/types.ts`
- 创建：`frontend/src/services/rag.ts`

- [ ] **步骤 1：在 `types.ts` 追加 RAG 类型**

在文件末尾追加以下类型：

```ts
export type RagMessageRole = 'user' | 'assistant' | 'system';

export interface RagConversationSummary {
  conversationId: string;
  title?: string | null;
  lastQuestion?: string | null;
  lastAnswer?: string | null;
  lastTime?: string | null;
  messageCount?: number | null;
}

export interface RagCitation {
  id?: string;
  kbId?: string | null;
  kbName?: string | null;
  docId?: string | null;
  docName?: string | null;
  chunkId?: string | null;
  chunkIndex?: number | null;
  score?: number | null;
  content?: string | null;
}

export interface RagRetrievedChunk extends RagCitation {
  collectionName?: string | null;
  metadata?: Record<string, unknown> | null;
}

export interface RagPromptPreview {
  scene?: string | null;
  baseTemplate?: string | null;
  kbContext?: string | null;
  mcpContext?: string | null;
  question?: string | null;
  finalPrompt?: string | null;
}

export interface RagTraceStep {
  name: 'retrieve' | 'prompt' | 'chat' | string;
  status: 'idle' | 'running' | 'success' | 'error' | string;
  durationMs?: number | null;
  message?: string | null;
}

export interface RagMessage {
  id: string;
  conversationId?: string | null;
  role: RagMessageRole;
  content: string;
  thinkingContent?: string | null;
  thinkingDuration?: number | null;
  citations?: RagCitation[];
  retrievedChunks?: RagRetrievedChunk[];
  promptPreview?: RagPromptPreview | null;
  createTime?: string | null;
}

export interface RagConversationDetail {
  conversationId: string;
  title?: string | null;
  messages: RagMessage[];
}

export interface RagChatRequest {
  conversationId?: string | null;
  question: string;
  kbIds?: string[];
  topK?: number;
  returnDebug?: boolean;
}

export interface RagChatResponse {
  conversationId: string;
  messageId?: string | null;
  answer: string;
  citations: RagCitation[];
  retrievedChunks?: RagRetrievedChunk[];
  promptPreview?: RagPromptPreview | null;
  traceSteps?: RagTraceStep[];
}

export interface RagDebugRunRequest {
  question: string;
  kbIds?: string[];
  topK: number;
  returnPrompt: boolean;
}

export interface RagDebugRunResult {
  runId?: string | null;
  answer?: string | null;
  citations: RagCitation[];
  retrievedChunks: RagRetrievedChunk[];
  promptPreview?: RagPromptPreview | null;
  traceSteps: RagTraceStep[];
  errorMessage?: string | null;
  createTime?: string | null;
}
```

- [ ] **步骤 2：创建 `frontend/src/services/rag.ts`**

文件内容：

```ts
import { api } from './api';
import type {
  RagChatRequest,
  RagChatResponse,
  RagConversationDetail,
  RagConversationSummary,
  RagDebugRunRequest,
  RagDebugRunResult,
} from '../types';

export async function getConversations() {
  return api.get<RagConversationSummary[], RagConversationSummary[]>('/rag/conversations');
}

export async function getConversation(conversationId: string) {
  return api.get<RagConversationDetail, RagConversationDetail>(`/rag/conversations/${conversationId}`);
}

export async function sendChat(payload: RagChatRequest) {
  return api.post<RagChatResponse, RagChatResponse>('/rag/chat', payload);
}

export async function runDebug(payload: RagDebugRunRequest) {
  return api.post<RagDebugRunResult, RagDebugRunResult>('/rag/debug/runs', payload);
}
```

- [ ] **步骤 3：运行类型检查**

运行：

```powershell
cd frontend
npm run build
```

预期：如果没有页面引用新类型，构建应继续通过；若现有仓库已有无关错误，记录完整报错。

- [ ] **步骤 4：Commit**

```powershell
git add frontend/src/types.ts frontend/src/services/rag.ts
git commit -m "feat(RAG问答): 添加前端服务契约"
```

## 任务 2：实现前台 `/assistant` 聊天主导页面

**文件：**

- 修改：`frontend/src/App.tsx`
- 修改：`frontend/src/styles.css`

- [ ] **步骤 1：更新 `App.tsx` import**

在现有服务 import 附近加入：

```ts
import * as ragApi from './services/rag';
```

在类型 import 中加入：

```ts
  RagChatResponse,
  RagCitation,
  RagConversationSummary,
  RagMessage,
  RagPromptPreview,
  RagRetrievedChunk,
```

- [ ] **步骤 2：替换 `AssistantPage` 状态与加载逻辑**

将现有 `AssistantPage` 函数整体替换为：

```tsx
function AssistantPage() {
  const user = useAuthStore((state) => state.user);
  const [conversations, setConversations] = useState<RagConversationSummary[]>([]);
  const [messages, setMessages] = useState<RagMessage[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<string[]>([]);
  const [topK, setTopK] = useState(5);
  const [question, setQuestion] = useState('');
  const [loadingConversations, setLoadingConversations] = useState(false);
  const [loadingConversation, setLoadingConversation] = useState(false);
  const [sending, setSending] = useState(false);
  const [conversationError, setConversationError] = useState<string | null>(null);
  const [knowledgeError, setKnowledgeError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [lastRun, setLastRun] = useState<RagChatResponse | null>(null);
  const lastQuestionRef = useRef('');

  const loadConversations = useCallback(async () => {
    setLoadingConversations(true);
    setConversationError(null);
    try {
      const items = await ragApi.getConversations();
      setConversations(items);
    } catch (error) {
      setConversationError(getErrorMessage(error));
    } finally {
      setLoadingConversations(false);
    }
  }, []);

  useEffect(() => {
    loadConversations();
    knowledgeBaseApi.getKnowledgeBases({ pageNo: 1, pageSize: 50, status: 'enabled' })
      .then((page) => {
        setKnowledgeBases(page.records);
        setSelectedKbIds(page.records.slice(0, 2).map((item) => item.id));
      })
      .catch((error) => setKnowledgeError(getErrorMessage(error)));
  }, [loadConversations]);

  const openConversation = useCallback(async (conversationId: string) => {
    setSelectedConversationId(conversationId);
    setLoadingConversation(true);
    setSendError(null);
    try {
      const detail = await ragApi.getConversation(conversationId);
      setMessages(detail.messages);
      setLastRun(null);
    } catch (error) {
      setSendError(getErrorMessage(error));
    } finally {
      setLoadingConversation(false);
    }
  }, []);

  const startNewConversation = useCallback(() => {
    setSelectedConversationId(null);
    setMessages([]);
    setQuestion('');
    setSendError(null);
    setLastRun(null);
  }, []);

  const submitQuestion = useCallback(async (event?: FormEvent) => {
    event?.preventDefault();
    const normalized = question.trim();
    if (!normalized || sending) return;
    const clientMessageId = `local-user-${Date.now()}`;
    const pendingConversationId = selectedConversationId;
    lastQuestionRef.current = normalized;
    setSending(true);
    setSendError(null);
    setQuestion('');
    setMessages((current) => [
      ...current,
      {
        id: clientMessageId,
        conversationId: pendingConversationId,
        role: 'user',
        content: normalized,
        createTime: new Date().toISOString(),
      },
    ]);
    try {
      const response = await ragApi.sendChat({
        conversationId: pendingConversationId,
        question: normalized,
        kbIds: selectedKbIds,
        topK,
        returnDebug: true,
      });
      setSelectedConversationId(response.conversationId);
      setLastRun(response);
      setMessages((current) => [
        ...current,
        {
          id: response.messageId || `local-assistant-${Date.now()}`,
          conversationId: response.conversationId,
          role: 'assistant',
          content: response.answer,
          citations: response.citations,
          retrievedChunks: response.retrievedChunks,
          promptPreview: response.promptPreview,
          createTime: new Date().toISOString(),
        },
      ]);
      loadConversations();
    } catch (error) {
      setQuestion(normalized);
      setSendError(getErrorMessage(error));
    } finally {
      setSending(false);
    }
  }, [loadConversations, question, selectedConversationId, selectedKbIds, sending, topK]);

  const retryLastQuestion = useCallback(() => {
    if (lastQuestionRef.current) setQuestion(lastQuestionRef.current);
  }, []);

  const promptSuggestions = ['如何排查 Redis 连接失败？', '接口 500 错误如何定位？', '文档分块策略怎么选？'];
  const activeConversationTitle = conversations.find((item) => item.conversationId === selectedConversationId)?.title || '新会话';

  return (
    <AppShell>
      <section className="rag-chat-shell">
        <aside className="rag-session-sidebar card">
          <button className="btn btn-primary" type="button" onClick={startNewConversation}>新建会话</button>
          <div className="rag-sidebar-title">
            <h3>历史会话</h3>
            <button className="btn-text" type="button" onClick={loadConversations} disabled={loadingConversations}>刷新</button>
          </div>
          {conversationError && <p className="rag-error-text">{conversationError}</p>}
          <div className="rag-session-list">
            {loadingConversations ? (
              <div className="empty-state compact">正在加载会话...</div>
            ) : conversations.length === 0 ? (
              <div className="empty-state compact">暂无会话</div>
            ) : conversations.map((item) => (
              <button
                className={item.conversationId === selectedConversationId ? 'rag-session active' : 'rag-session'}
                type="button"
                key={item.conversationId}
                onClick={() => openConversation(item.conversationId)}
              >
                <strong>{item.title || item.lastQuestion || '未命名会话'}</strong>
                <span>{formatShortDate(item.lastTime)}</span>
              </button>
            ))}
          </div>
        </aside>

        <article className="rag-chat-panel">
          <header className="rag-chat-header">
            <div>
              <p className="eyebrow">RAG Chat</p>
              <h2>{activeConversationTitle}</h2>
              <p>{selectedKbIds.length ? `已选择 ${selectedKbIds.length} 个知识库` : '未选择知识库，将按后端默认范围检索'}</p>
            </div>
            <label className="rag-topk-control">
              Top-K
              <input type="number" min={1} max={20} value={topK} onChange={(event) => setTopK(Number(event.target.value) || 5)} />
            </label>
          </header>

          <div className="rag-thread">
            {loadingConversation ? (
              <div className="empty-state">正在同步会话消息...</div>
            ) : messages.length === 0 ? (
              <div className="rag-empty-thread">
                <div className="empty-icon">问</div>
                <h3>开始一次知识库问答</h3>
                <p>选择知识库后输入问题，页面会展示回答、引用来源和本轮检索摘要。</p>
                <div className="rag-suggestion-row">
                  {promptSuggestions.map((item) => (
                    <button type="button" key={item} onClick={() => setQuestion(item)}>{item}</button>
                  ))}
                </div>
              </div>
            ) : messages.map((message) => (
              <RagMessageBubble message={message} displayName={user?.displayName || user?.username || '我'} key={message.id} />
            ))}
            {sending && <div className="rag-pending-answer">正在检索知识库并生成回答...</div>}
            {sendError && (
              <div className="rag-error-card">
                <strong>请求失败</strong>
                <p>{sendError}</p>
                <button className="btn btn-light" type="button" onClick={retryLastQuestion}>恢复问题</button>
              </div>
            )}
          </div>

          <form className="rag-composer" onSubmit={submitQuestion}>
            <textarea
              aria-label="输入问题"
              placeholder="继续追问，或输入新的研发问题..."
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              disabled={sending}
            />
            <button className="btn btn-primary" type="submit" disabled={sending || !question.trim()}>{sending ? '发送中...' : '发送'}</button>
          </form>
        </article>

        <aside className="rag-context-sidebar">
          <RagKnowledgeSelector
            knowledgeBases={knowledgeBases}
            selectedKbIds={selectedKbIds}
            onChange={setSelectedKbIds}
            error={knowledgeError}
          />
          <RagCitationPanel citations={lastRun?.citations || []} />
          <RagRetrievalPanel chunks={lastRun?.retrievedChunks || []} />
          <RagPromptPanel prompt={lastRun?.promptPreview || null} compact />
        </aside>
      </section>
    </AppShell>
  );
}
```

- [ ] **步骤 3：添加前台辅助组件**

在 `AssistantPage` 后方添加：

```tsx
function RagMessageBubble({ message, displayName }: { message: RagMessage; displayName: string }) {
  const isUser = message.role === 'user';
  return (
    <article className={isUser ? 'rag-message user' : 'rag-message assistant'}>
      <header>
        <strong>{isUser ? displayName : 'DevBrain Assistant'}</strong>
        <span>{formatShortDate(message.createTime)}</span>
      </header>
      <p>{message.content}</p>
      {!isUser && message.citations?.length ? (
        <div className="rag-inline-citations">
          {message.citations.slice(0, 4).map((item, index) => (
            <span key={`${item.chunkId || item.docName || index}`}>{item.docName || item.kbName || `引用 ${index + 1}`}</span>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function RagKnowledgeSelector({
  knowledgeBases,
  selectedKbIds,
  onChange,
  error,
}: {
  knowledgeBases: KnowledgeBaseItem[];
  selectedKbIds: string[];
  onChange: (ids: string[]) => void;
  error?: string | null;
}) {
  function toggle(id: string) {
    onChange(selectedKbIds.includes(id) ? selectedKbIds.filter((item) => item !== id) : [...selectedKbIds, id]);
  }

  return (
    <article className="rag-side-card">
      <div className="card-title">
        <h3>知识库范围</h3>
      </div>
      {error && <p className="rag-error-text">{error}</p>}
      <div className="rag-kb-list">
        {knowledgeBases.length === 0 ? (
          <p className="muted-empty">暂无可选知识库</p>
        ) : knowledgeBases.map((item) => (
          <label key={item.id} className="rag-kb-option">
            <input type="checkbox" checked={selectedKbIds.includes(item.id)} onChange={() => toggle(item.id)} />
            <span>
              <strong>{item.name}</strong>
              <small>{item.documentCount || 0} 篇文档</small>
            </span>
          </label>
        ))}
      </div>
    </article>
  );
}

function RagCitationPanel({ citations }: { citations: RagCitation[] }) {
  return (
    <article className="rag-side-card">
      <div className="card-title"><h3>引用来源</h3></div>
      <div className="rag-source-list">
        {citations.length === 0 ? <p className="muted-empty">回答生成后显示引用来源。</p> : citations.map((item, index) => (
          <div className="rag-source-row" key={`${item.chunkId || item.docId || index}`}>
            <span>{index + 1}</span>
            <strong>{item.docName || item.kbName || '未命名来源'}</strong>
            <small>{formatScore(item.score)}</small>
          </div>
        ))}
      </div>
    </article>
  );
}
```

- [ ] **步骤 4：添加前台样式**

在 `styles.css` 的问答样式附近追加：

```css
.rag-chat-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: stretch;
}

.rag-session-sidebar,
.rag-chat-panel,
.rag-side-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 28px rgba(21, 36, 52, 0.06);
}

.rag-session-sidebar {
  display: flex;
  min-height: calc(100vh - 150px);
  flex-direction: column;
  gap: 14px;
}

.rag-sidebar-title,
.rag-chat-header,
.rag-message header,
.rag-source-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.btn-text {
  border: 0;
  background: transparent;
  color: var(--primary);
  font-weight: 900;
}

.rag-session-list,
.rag-context-sidebar,
.rag-source-list,
.rag-kb-list {
  display: grid;
  gap: 10px;
}

.rag-session {
  display: grid;
  gap: 4px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f8fafc;
  padding: 11px 12px;
  color: var(--text);
  text-align: left;
}

.rag-session.active {
  border-color: #b8d9dd;
  background: var(--primary-soft);
  color: var(--primary-strong);
}

.rag-session strong,
.rag-session span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rag-session span,
.rag-message header span,
.rag-source-row small {
  color: var(--muted);
  font-size: 0.82rem;
  font-weight: 800;
}

.rag-chat-panel {
  display: grid;
  min-height: calc(100vh - 150px);
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.rag-chat-header {
  border-bottom: 1px solid var(--border);
  padding: 18px 20px;
}

.rag-chat-header h2,
.rag-chat-header p {
  margin: 0;
}

.rag-topk-control {
  display: grid;
  gap: 6px;
  width: 92px;
  color: var(--muted);
  font-weight: 900;
}

.rag-thread {
  display: grid;
  align-content: start;
  gap: 14px;
  min-height: 0;
  overflow: auto;
  padding: 20px;
}

.rag-message {
  display: grid;
  gap: 10px;
  max-width: min(720px, 90%);
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  padding: 14px;
}

.rag-message.user {
  justify-self: end;
  border-color: #b8d9dd;
  background: #e8f3ff;
}

.rag-message p {
  margin: 0;
  color: var(--text);
  white-space: pre-wrap;
}

.rag-inline-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rag-inline-citations span {
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary-strong);
  padding: 6px 10px;
  font-size: 0.8rem;
  font-weight: 900;
}

.rag-empty-thread {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 80px 20px;
  text-align: center;
}

.rag-suggestion-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.rag-suggestion-row button {
  min-height: 34px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: #fff;
  color: var(--primary-strong);
  padding: 0 12px;
  font-weight: 900;
}

.rag-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  border-top: 1px solid var(--border);
  background: #fbfdff;
  padding: 14px;
}

.rag-composer textarea {
  min-height: 54px;
  resize: vertical;
}

.rag-side-card {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.rag-kb-option {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fbfdff;
  padding: 10px;
}

.rag-kb-option span,
.rag-kb-option strong,
.rag-kb-option small {
  display: block;
  min-width: 0;
}

.rag-kb-option small {
  color: var(--muted);
}

.rag-source-row {
  border-bottom: 1px solid var(--border);
  padding-bottom: 10px;
}

.rag-source-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.rag-source-row span {
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 8px;
  background: var(--primary-soft);
  color: var(--primary-strong);
  font-weight: 900;
}

.rag-source-row strong {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rag-error-card {
  border: 1px solid #f3b4ab;
  border-radius: 8px;
  background: var(--danger-soft);
  padding: 14px;
}

.rag-error-card p,
.rag-error-text {
  color: var(--danger);
}

.rag-pending-answer {
  border: 1px dashed var(--border-strong);
  border-radius: 8px;
  background: #fbfdff;
  padding: 14px;
  color: var(--muted);
  font-weight: 900;
}
```

- [ ] **步骤 5：运行构建并修复类型错误**

运行：

```powershell
cd frontend
npm run build
```

预期：PASS。若出现 `formatScore`、`RagRetrievalPanel`、`RagPromptPanel` 未定义，则在任务 3 中实现后再复跑。

- [ ] **步骤 6：Commit**

```powershell
git add frontend/src/App.tsx frontend/src/styles.css
git commit -m "feat(RAG问答): 实现前台聊天页面"
```

## 任务 3：补充共享 RAG 面板组件

**文件：**

- 修改：`frontend/src/App.tsx`
- 修改：`frontend/src/styles.css`

- [ ] **步骤 1：在 `App.tsx` 添加格式化与面板组件**

在 `RagCitationPanel` 后追加：

```tsx
function formatScore(score?: number | null) {
  if (score == null || Number.isNaN(score)) return '--';
  return score <= 1 ? `${Math.round(score * 100)}%` : score.toFixed(2);
}

function RagRetrievalPanel({ chunks }: { chunks: RagRetrievedChunk[] }) {
  return (
    <article className="rag-side-card">
      <div className="card-title"><h3>检索命中</h3></div>
      <div className="rag-chunk-list">
        {chunks.length === 0 ? <p className="muted-empty">暂无检索命中。</p> : chunks.map((chunk, index) => (
          <article className="rag-chunk-card" key={`${chunk.chunkId || chunk.id || index}`}>
            <header>
              <strong>{chunk.docName || chunk.kbName || `Chunk ${index + 1}`}</strong>
              <span>{formatScore(chunk.score)}</span>
            </header>
            <p>{chunk.content || '暂无内容片段'}</p>
            <small>{chunk.kbName || chunk.collectionName || '--'} · #{chunk.chunkIndex ?? index + 1}</small>
          </article>
        ))}
      </div>
    </article>
  );
}

function RagPromptPanel({ prompt, compact = false }: { prompt?: RagPromptPreview | null; compact?: boolean }) {
  return (
    <article className={compact ? 'rag-side-card rag-prompt-card compact' : 'card rag-prompt-card'}>
      <div className="card-title"><h3>Prompt 摘要</h3></div>
      {!prompt ? (
        <p className="muted-empty">暂无 Prompt 信息。</p>
      ) : (
        <div className="rag-prompt-grid">
          <div><span>Scene</span><strong>{prompt.scene || '--'}</strong></div>
          <div><span>Template</span><strong>{prompt.baseTemplate || '--'}</strong></div>
          <div><span>Question</span><pre>{prompt.question || '--'}</pre></div>
          <div><span>KB Context</span><pre>{prompt.kbContext || '--'}</pre></div>
          {!compact && <div><span>MCP Context</span><pre>{prompt.mcpContext || '--'}</pre></div>}
          {!compact && <div><span>Final Prompt</span><pre>{prompt.finalPrompt || '--'}</pre></div>}
        </div>
      )}
    </article>
  );
}
```

- [ ] **步骤 2：补充共享面板样式**

在 `styles.css` 的 RAG 样式后追加：

```css
.rag-chunk-list,
.rag-prompt-grid {
  display: grid;
  gap: 10px;
}

.rag-chunk-card {
  display: grid;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fbfdff;
  padding: 12px;
}

.rag-chunk-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rag-chunk-card header span {
  color: var(--primary-strong);
  font-weight: 900;
}

.rag-chunk-card p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--text);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.rag-chunk-card small,
.rag-prompt-grid span {
  color: var(--muted);
  font-weight: 900;
}

.rag-prompt-card {
  display: grid;
  gap: 12px;
}

.rag-prompt-grid > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.rag-prompt-grid pre {
  max-height: 180px;
  overflow: auto;
  margin: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px;
  color: var(--text);
  font-family: "Cascadia Code", "Consolas", "Microsoft YaHei UI", monospace;
  font-size: 0.84rem;
  line-height: 1.6;
  white-space: pre-wrap;
}

.rag-prompt-card.compact .rag-prompt-grid pre {
  max-height: 120px;
}
```

- [ ] **步骤 3：运行构建**

运行：

```powershell
cd frontend
npm run build
```

预期：PASS。

- [ ] **步骤 4：Commit**

```powershell
git add frontend/src/App.tsx frontend/src/styles.css
git commit -m "feat(RAG问答): 添加链路信息面板"
```

## 任务 4：实现后台 `/admin/qa` 链路调试台

**文件：**

- 修改：`frontend/src/App.tsx`
- 修改：`frontend/src/styles.css`

- [ ] **步骤 1：替换 `/admin/qa` 路由**

将路由：

```tsx
<Route path="/admin/qa" element={<RequireAuth><RequireAdmin><AdminModulePage title="问答管理" description="管理问答记录、反馈记录和 FAQ 内容。" /></RequireAdmin></RequireAuth>} />
```

替换为：

```tsx
<Route path="/admin/qa" element={<RequireAuth><RequireAdmin><AdminQaPage /></RequireAdmin></RequireAuth>} />
```

在类型 import 中加入：

```ts
  RagDebugRunResult,
  RagTraceStep,
```

- [ ] **步骤 2：新增 `AdminQaPage`**

在 `AdminModulePage` 前添加：

```tsx
function AdminQaPage() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<string[]>([]);
  const [question, setQuestion] = useState('生产环境 Redis 连接失败如何排查？');
  const [topK, setTopK] = useState(5);
  const [returnPrompt, setReturnPrompt] = useState(true);
  const [loadingKb, setLoadingKb] = useState(false);
  const [running, setRunning] = useState(false);
  const [kbError, setKbError] = useState<string | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [result, setResult] = useState<RagDebugRunResult | null>(null);

  useEffect(() => {
    setLoadingKb(true);
    knowledgeBaseApi.getKnowledgeBases({ pageNo: 1, pageSize: 100, status: 'enabled' })
      .then((page) => {
        setKnowledgeBases(page.records);
        setSelectedKbIds(page.records.slice(0, 3).map((item) => item.id));
      })
      .catch((error) => setKbError(getErrorMessage(error)))
      .finally(() => setLoadingKb(false));
  }, []);

  async function run(event: FormEvent) {
    event.preventDefault();
    const normalized = question.trim();
    if (!normalized || running) return;
    setRunning(true);
    setRunError(null);
    try {
      const next = await ragApi.runDebug({
        question: normalized,
        kbIds: selectedKbIds,
        topK,
        returnPrompt,
      });
      setResult(next);
    } catch (error) {
      setRunError(getErrorMessage(error));
    } finally {
      setRunning(false);
    }
  }

  return (
    <AppShell mode="admin">
      <PageContainer
        title="问答管理"
        description="调试 RAG 问答链路，查看检索命中、Prompt 注入和最终回答。"
      >
        <section className="rag-debug-shell">
          <form className="card rag-debug-form" onSubmit={run}>
            <label>
              调试问题
              <textarea value={question} onChange={(event) => setQuestion(event.target.value)} />
            </label>
            <div className="rag-debug-controls">
              <label>
                Top-K
                <input type="number" min={1} max={20} value={topK} onChange={(event) => setTopK(Number(event.target.value) || 5)} />
              </label>
              <label className="inline-checkbox">
                <input type="checkbox" checked={returnPrompt} onChange={(event) => setReturnPrompt(event.target.checked)} />
                返回 Prompt
              </label>
              <button className="btn btn-primary" type="submit" disabled={running || !question.trim()}>{running ? '运行中...' : '运行调试'}</button>
            </div>
            {kbError && <p className="rag-error-text">{kbError}</p>}
            {runError && <div className="rag-error-card"><strong>调试请求失败</strong><p>{runError}</p></div>}
            <RagKnowledgeSelector
              knowledgeBases={knowledgeBases}
              selectedKbIds={selectedKbIds}
              onChange={setSelectedKbIds}
              error={loadingKb ? '正在加载知识库...' : null}
            />
          </form>

          <RagTraceOverview steps={result?.traceSteps || []} running={running} />

          <section className="rag-debug-grid">
            <article className="card rag-debug-main">
              <div className="card-title"><h3>检索结果</h3></div>
              <RagDebugRetrievalTable chunks={result?.retrievedChunks || []} />
            </article>
            <RagPromptPanel prompt={result?.promptPreview || null} />
            <article className="card rag-answer-card">
              <div className="card-title"><h3>最终回答</h3></div>
              {result?.answer ? <p>{result.answer}</p> : <div className="empty-state compact">运行调试后显示回答。</div>}
              <RagCitationPanel citations={result?.citations || []} />
            </article>
          </section>
        </section>
      </PageContainer>
    </AppShell>
  );
}
```

- [ ] **步骤 3：新增后台调试辅助组件**

在 `AdminQaPage` 后添加：

```tsx
function RagTraceOverview({ steps, running }: { steps: RagTraceStep[]; running: boolean }) {
  const names = ['retrieve', 'prompt', 'chat'];
  const normalized = names.map((name) => steps.find((step) => step.name === name) || {
    name,
    status: running ? 'running' : 'idle',
    durationMs: null,
    message: running ? '执行中' : '等待执行',
  });

  return (
    <section className="rag-trace-overview">
      {normalized.map((step) => (
        <article className={`rag-trace-card ${step.status}`} key={step.name}>
          <span>{step.name}</span>
          <strong>{traceStatusLabel(step.status)}</strong>
          <small>{step.durationMs != null ? `${step.durationMs} ms` : step.message || '--'}</small>
        </article>
      ))}
    </section>
  );
}

function traceStatusLabel(status: string) {
  if (status === 'success') return '成功';
  if (status === 'error') return '失败';
  if (status === 'running') return '运行中';
  return '待运行';
}

function RagDebugRetrievalTable({ chunks }: { chunks: RagRetrievedChunk[] }) {
  if (chunks.length === 0) return <div className="empty-state compact">暂无检索结果。</div>;
  return (
    <div className="rag-debug-table-wrap">
      <table className="data-table rag-debug-table">
        <thead>
          <tr>
            <th>来源</th>
            <th>分数</th>
            <th>Chunk</th>
            <th>内容片段</th>
          </tr>
        </thead>
        <tbody>
          {chunks.map((chunk, index) => (
            <tr key={`${chunk.chunkId || chunk.id || index}`}>
              <td>
                <strong>{chunk.docName || '--'}</strong>
                <small>{chunk.kbName || chunk.collectionName || '--'}</small>
              </td>
              <td>{formatScore(chunk.score)}</td>
              <td>#{chunk.chunkIndex ?? index + 1}</td>
              <td>{chunk.content || '--'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **步骤 4：添加后台样式**

在 `styles.css` 追加：

```css
.rag-debug-shell {
  display: grid;
  gap: 18px;
}

.rag-debug-form {
  display: grid;
  gap: 14px;
}

.rag-debug-form textarea {
  min-height: 96px;
  resize: vertical;
}

.rag-debug-controls {
  display: grid;
  grid-template-columns: minmax(120px, 160px) minmax(160px, auto) auto;
  gap: 12px;
  align-items: end;
}

.rag-trace-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.rag-trace-card {
  display: grid;
  gap: 6px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  padding: 16px;
  box-shadow: 0 8px 26px rgba(21, 36, 52, 0.05);
}

.rag-trace-card span,
.rag-trace-card small {
  color: var(--muted);
  font-weight: 900;
}

.rag-trace-card strong {
  font-size: 1.25rem;
}

.rag-trace-card.success strong {
  color: #16a34a;
}

.rag-trace-card.error strong {
  color: var(--danger);
}

.rag-trace-card.running strong {
  color: var(--warning);
}

.rag-debug-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.95fr);
  gap: 18px;
  align-items: start;
}

.rag-debug-main {
  grid-row: span 2;
}

.rag-debug-table-wrap {
  overflow-x: auto;
}

.rag-debug-table {
  min-width: 820px;
}

.rag-debug-table td strong,
.rag-debug-table td small {
  display: block;
}

.rag-debug-table td small {
  color: var(--muted);
}

.rag-answer-card {
  display: grid;
  gap: 12px;
}

.rag-answer-card > p {
  margin: 0;
  color: var(--text);
  white-space: pre-wrap;
}
```

- [ ] **步骤 5：运行构建**

运行：

```powershell
cd frontend
npm run build
```

预期：PASS。

- [ ] **步骤 6：Commit**

```powershell
git add frontend/src/App.tsx frontend/src/styles.css
git commit -m "feat(RAG问答): 实现后台链路调试台"
```

## 任务 5：响应式与最终验证

**文件：**

- 修改：`frontend/src/styles.css`

- [ ] **步骤 1：补充响应式样式**

在现有 `@media (max-width: 1080px)` 中加入：

```css
  .rag-chat-shell,
  .rag-debug-grid {
    grid-template-columns: 1fr;
  }

  .rag-session-sidebar {
    min-height: auto;
  }

  .rag-context-sidebar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
```

在现有 `@media (max-width: 860px)` 中加入：

```css
  .rag-context-sidebar,
  .rag-trace-overview,
  .rag-debug-controls {
    grid-template-columns: 1fr;
  }

  .rag-composer {
    grid-template-columns: 1fr;
  }

  .rag-composer .btn {
    width: 100%;
  }
```

在现有 `@media (max-width: 560px)` 中加入：

```css
  .rag-chat-header,
  .rag-message header,
  .rag-source-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .rag-message {
    max-width: 100%;
  }
```

- [ ] **步骤 2：运行前端构建**

运行：

```powershell
cd frontend
npm run build
```

预期：TypeScript 和 Vite 构建全部通过。

- [ ] **步骤 3：运行空白字符检查**

运行：

```powershell
git diff --check
```

预期：无 trailing whitespace 或冲突标记。

- [ ] **步骤 4：检查最终 diff**

运行：

```powershell
git diff --stat
git diff -- frontend/src/App.tsx frontend/src/styles.css frontend/src/types.ts frontend/src/services/rag.ts
```

预期：只包含 RAG 前端相关变更；不要包含后端 Controller 或无关重构。

- [ ] **步骤 5：Commit**

```powershell
git add frontend/src/App.tsx frontend/src/styles.css
git commit -m "style(RAG问答): 优化页面响应式布局"
```

如果步骤 1 没有产生额外 diff，则跳过本次 commit，并在最终说明中写明。
