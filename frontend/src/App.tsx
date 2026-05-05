/**
 * DevBrain-CQUPT 前端主应用模块
 * 包含所有页面组件、路由配置和公共 UI 组件
 *
 * 架构说明：
 * - 使用 React Router 进行路由管理
 * - 使用 Zustand (authStore) 管理全局认证状态
 * - 前台（front）和管理后台（admin）共用同一套代码，通过 mode 区分
 * - 所有 API 请求通过 services 模块统一处理
 */
import { FormEvent, ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BrowserRouter, Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from './stores/authStore';
import * as authApi from './services/auth';
import * as knowledgeBaseApi from './services/knowledgeBase';
import * as syncApi from './services/sync';
import type { DocumentChunkItem, KnowledgeBaseItem, KnowledgeBaseStatus, KnowledgeChunkItem, KnowledgeDocumentItem, PermissionItem, ResourceItem, RoleItem, SyncHistoryItem, SyncTaskOverviewItem, UserItem } from './types';
import type { AxiosProgressEvent } from 'axios';

/** 认证页面模式：登录、注册、找回密码 */
type AuthMode = 'login' | 'register' | 'forgot';
/** 管理后台 Tab 页类型 */
type AdminTab = 'users' | 'roles' | 'permissions' | 'departments';
/** 知识库弹窗模式：创建或编辑 */
type KnowledgeBaseModalMode = 'create' | 'edit';
/** 应用 Shell 模式：前台或管理后台 */
type ShellMode = 'front' | 'admin';
/** 文档来源模式：本地文件、飞书文档、URL */
type DocumentSourceMode = 'file' | 'feishu' | 'url';
/** 同步频率选项 */
type SyncFrequency = 'none' | 'daily' | 'weekly' | 'monthly';
/** 加入知识库方式 */
type JoinKnowledgeBaseMode = 'search' | 'invite' | 'organization';
/** 文档操作模式：空白文档或上传文件 */
type DocumentActionMode = 'blank' | 'upload';
/** 分块策略模式 */
type ChunkStrategyMode = 'fixed_size' | 'recursive_character' | 'structure_aware' | 'qa_pair' | 'table_aware';
type IconName =
  | 'home'
  | 'message'
  | 'book'
  | 'history'
  | 'star'
  | 'user'
  | 'users'
  | 'bell'
  | 'help'
  | 'headphones'
  | 'question'
  | 'bookmark'
  | 'filePlus'
  | 'target'
  | 'code'
  | 'cloudUpload'
  | 'database'
  | 'fileSearch'
  | 'fileText'
  | 'settings'
  | 'tag'
  | 'box'
  | 'shield'
  | 'chart';

/** 侧边栏菜单项接口 */
interface ShellMenuItem {
  label: string;
  path: string;
  icon: IconName;
}

/** 知识库表单状态（创建/编辑弹窗使用） */
interface KnowledgeBaseFormState {
  name: string;
  description: string;
  collectionName: string;
  embeddingModel: string;
  status: KnowledgeBaseStatus;
  /** 访问级别：私有、团队、组织 */
  accessLevel: 'private' | 'team' | 'organization';
}

/** 分块配置表单状态 */
interface ChunkFormState {
  strategy: ChunkStrategyMode;
  /** 分块大小（字符数） */
  chunkSize: number;
  /** 重叠大小（字符数） */
  overlapSize: number;
  /** 最小字符数 */
  minChars: number;
  /** 最大字符数 */
  maxChars: number;
}

/** 本地文档版本记录（存储在 localStorage 中） */
interface LocalDocumentVersion {
  id: string;
  docId: string;
  docName: string;
  version: string;
  note: string;
  createdAt: string;
}

/** 分页大小选项 */
const pageSizeOptions = [10, 20, 50];
interface EmbeddingModelOption {
  value: string;
  label: string;
  hint: string;
}

interface EmbeddingModelGroup {
  label: string;
  options: EmbeddingModelOption[];
}

const defaultEmbeddingModel = 'text-embedding-3-small';
const embeddingModelGroups: EmbeddingModelGroup[] = [
  {
    label: '云服务模型',
    options: [
      {
        value: 'text-embedding-3-small',
        label: 'text-embedding-3-small（首选）',
        hint: '云服务 Embedding 模型，适合优先走稳定托管服务的知识库。',
      },
    ],
  },
  {
    label: '本地模型',
    options: [
      {
        value: 'bge-m3',
        label: 'bge-m3',
        hint: '本地通用多语种 Embedding 模型，使用前需确认本地服务已加载该模型。',
      },
      {
        value: 'qwen-emb-local',
        label: 'qwen3-embedding:8b-fp16',
        hint: '本地 Qwen3 Embedding 模型，适合走 Ollama 本地向量化。',
      },
    ],
  },
];
const embeddingModelOptions = embeddingModelGroups.flatMap((group) => group.options);
/** 知识库表单初始值 */
const emptyKnowledgeBaseForm: KnowledgeBaseFormState = {
  name: '',
  description: '',
  collectionName: '',
  embeddingModel: defaultEmbeddingModel,
  status: 'enabled',
  accessLevel: 'team',
};
/** 分块配置表单默认值 */
const defaultChunkForm: ChunkFormState = {
  strategy: 'fixed_size',
  chunkSize: 512,
  overlapSize: 128,
  minChars: 240,
  maxChars: 900,
};
/** 分块策略选项列表，用于下拉选择 */
const chunkStrategyOptions: Array<{ value: ChunkStrategyMode; label: string; hint: string }> = [
  { value: 'fixed_size', label: '固定长度', hint: '通用文档快速处理' },
  { value: 'recursive_character', label: '递归字符', hint: '优先保留段落边界' },
  { value: 'structure_aware', label: '结构感知', hint: '适合 Markdown 和标题层级' },
  { value: 'qa_pair', label: '问答对', hint: '适合 FAQ 和问答材料' },
  { value: 'table_aware', label: '表格感知', hint: '尽量保持表格完整' },
];
/** 文档版本记录的 localStorage 存储键 */
const documentVersionStoreKey = 'devbrain.documentVersions.v1';

/** 前台侧边栏菜单配置 */
const frontMenuItems: ShellMenuItem[] = [
  { label: '首页', path: '/workspace', icon: 'home' },
  { label: '智能问答', path: '/assistant', icon: 'message' },
  { label: '知识库', path: '/knowledge-bases', icon: 'book' },
  { label: '历史记录', path: '/history', icon: 'history' },
  { label: '我的收藏', path: '/favorites', icon: 'star' },
  { label: '个人中心', path: '/profile', icon: 'user' },
];

/** 管理后台侧边栏菜单配置 */
const adminMenuItems: ShellMenuItem[] = [
  { label: '工作台', path: '/admin', icon: 'home' },
  { label: '知识库管理', path: '/admin/knowledge-bases', icon: 'database' },
  { label: '问答管理', path: '/admin/qa', icon: 'message' },
  { label: '用户权限', path: '/admin/users', icon: 'shield' },
  { label: '标签分类', path: '/admin/tags', icon: 'tag' },
  { label: '入库任务', path: '/admin/ingestion', icon: 'box' },
  { label: '模型配置', path: '/admin/models', icon: 'target' },
  { label: '系统配置', path: '/admin/system', icon: 'settings' },
  { label: '日志审计', path: '/admin/audit', icon: 'fileSearch' },
  { label: '数据统计', path: '/admin/stats', icon: 'chart' },
];

/** 工作台快捷提问提示卡片配置 */
const workspacePrompts: Array<{ title: string; text: string; icon: IconName }> = [
  { title: '接口报错排查', text: '定位接口异常原因', icon: 'code' },
  { title: '部署失败处理', text: '解决部署过程问题', icon: 'cloudUpload' },
  { title: '数据库连接问题', text: '排查连接异常', icon: 'database' },
  { title: '日志定位指南', text: '快速定位问题日志', icon: 'fileSearch' },
];

/**
 * 应用根组件
 * 配置全局路由结构，包含前台页面、管理后台页面和公共页面
 */
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/workspace" element={<RequireAuth><WorkspacePage /></RequireAuth>} />
        <Route path="/knowledge-bases" element={<RequireAuth><FrontKnowledgePage /></RequireAuth>} />
        <Route path="/knowledge-bases/:id/documents" element={<RequireAuth><KnowledgeBaseDocumentsPage /></RequireAuth>} />
        <Route path="/knowledge-bases/:id/documents/:documentId" element={<RequireAuth><DocumentDetailPage /></RequireAuth>} />
        <Route path="/documents" element={<RequireAuth><FrontKnowledgePage /></RequireAuth>} />
        <Route path="/assistant" element={<RequireAuth><AssistantPage /></RequireAuth>} />
        <Route path="/history" element={<RequireAuth><FrontModulePage title="历史记录" description="查看历史问答、检索和知识库访问记录。" /></RequireAuth>} />
        <Route path="/favorites" element={<RequireAuth><FrontModulePage title="我的收藏" description="集中管理收藏的答案、文档片段和引用来源。" /></RequireAuth>} />
        <Route path="/profile" element={<RequireAuth><SettingsPage /></RequireAuth>} />
        <Route path="/settings" element={<RequireAuth><SettingsPage /></RequireAuth>} />
        <Route path="/admin" element={<RequireAuth><RequireAdmin><AdminDashboardPage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/knowledge-bases" element={<RequireAuth><RequireAdmin><KnowledgeBasePage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/knowledge-bases/:id/documents" element={<RequireAuth><RequireAdmin><KnowledgeBaseDocumentsPage mode="admin" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/knowledge-bases/:id/documents/:documentId/chunks" element={<RequireAuth><RequireAdmin><AdminDocumentChunksPage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/documents" element={<RequireAuth><RequireAdmin><AdminDocumentsPage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/qa" element={<RequireAuth><RequireAdmin><AdminModulePage title="问答管理" description="管理问答记录、反馈记录和 FAQ 内容。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/users" element={<RequireAuth><RequireAdmin><AdminPage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/tags" element={<RequireAuth><RequireAdmin><AdminModulePage title="标签分类" description="维护知识库、文档和问答内容的标签体系。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/ingestion" element={<RequireAuth><RequireAdmin><AdminIngestionPage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/models" element={<RequireAuth><RequireAdmin><AdminModulePage title="模型配置" description="配置问答模型、Embedding 模型、重排模型和调用策略。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/system" element={<RequireAuth><RequireAdmin><AdminModulePage title="系统配置" description="配置平台参数、检索策略和安全策略。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/audit" element={<RequireAuth><RequireAdmin><AdminModulePage title="日志审计" description="审计登录、权限、配置变更和关键操作日志。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/stats" element={<RequireAuth><RequireAdmin><AdminModulePage title="数据统计" description="查看知识库使用、问答效果和系统资源统计。" /></RequireAdmin></RequireAuth>} />
        <Route path="/403" element={<StatusPage code="403" title="权限不足" text="当前账号没有访问该页面的权限。" />} />
        <Route path="*" element={<StatusPage code="404" title="页面不存在" text="没有找到对应的 DevBrain 工作区页面。" />} />
      </Routes>
    </BrowserRouter>
  );
}

/**
 * 首页重定向组件
 * 根据登录状态自动跳转到工作台或认证页面
 */
function HomeRedirect() {
  const user = useAuthStore((state) => state.user);
  return <Navigate to={user ? '/workspace' : '/auth'} replace />;
}

/**
 * 路由守卫组件 - 认证保护
 * 访问受保护页面前先刷新用户信息，未登录则跳转到认证页面
 */
function RequireAuth({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const refresh = useAuthStore((state) => state.refresh);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    refresh().finally(() => setReady(true));
  }, [refresh]);

  if (!ready) return <BootScreen />;
  if (!user) return <Navigate to="/auth" replace />;
  return children;
}

/**
 * 路由守卫组件 - 管理员权限保护
 * 非管理员用户访问管理页面时跳转到 403 页面
 */
function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  if (!user?.roles.includes('admin')) return <Navigate to="/403" replace />;
  return children;
}

/**
 * SVG 图标组件
 * 根据图标名称渲染对应的 SVG 矢量图标
 * @param name - 图标名称
 * @param className - CSS 类名，默认为 'ui-icon'
 */
function UiIcon({ name, className = 'ui-icon' }: { name: IconName; className?: string }) {
  const iconProps = {
    className,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
  } as const;

  switch (name) {
    case 'home':
      return <svg {...iconProps} aria-hidden="true"><path d="m3 10.5 9-7 9 7" /><path d="M5 9.5V20h14V9.5" /><path d="M9.5 20v-6h5v6" /></svg>;
    case 'message':
      return <svg {...iconProps} aria-hidden="true"><path d="M5 5h14v10H8l-4 4V5Z" /><path d="M8 9h8" /><path d="M8 12h5" /></svg>;
    case 'book':
      return <svg {...iconProps} aria-hidden="true"><path d="M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3V4Z" /><path d="M8 4v13a3 3 0 0 0 3 3" /><path d="M9 8h6" /></svg>;
    case 'history':
      return <svg {...iconProps} aria-hidden="true"><path d="M4 12a8 8 0 1 0 2.35-5.65L4 8.7" /><path d="M4 4v4.7h4.7" /><path d="M12 8v5l3 2" /></svg>;
    case 'star':
      return <svg {...iconProps} aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" /></svg>;
    case 'user':
      return <svg {...iconProps} aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4.5 21a7.5 7.5 0 0 1 15 0" /></svg>;
    case 'users':
      return <svg {...iconProps} aria-hidden="true"><path d="M16 19a4 4 0 0 0-8 0" /><circle cx="12" cy="8" r="3.2" /><path d="M22 19a3.5 3.5 0 0 0-4-3.5" /><path d="M2 19a3.5 3.5 0 0 1 4-3.5" /><path d="M18 6.2a2.4 2.4 0 0 1 0 4.6" /><path d="M6 6.2a2.4 2.4 0 0 0 0 4.6" /></svg>;
    case 'bell':
      return <svg {...iconProps} aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z" /><path d="M10 21h4" /></svg>;
    case 'help':
      return <svg {...iconProps} aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M9.6 9a2.6 2.6 0 1 1 4.2 2c-1 .7-1.8 1.3-1.8 2.7" /><path d="M12 17.3h.01" /></svg>;
    case 'headphones':
      return <svg {...iconProps} aria-hidden="true"><path d="M4 13a8 8 0 0 1 16 0" /><path d="M4 13v4a2 2 0 0 0 2 2h1v-6H6a2 2 0 0 0-2 2" /><path d="M20 13v4a2 2 0 0 1-2 2h-1v-6h1a2 2 0 0 1 2 2" /></svg>;
    case 'question':
      return <svg {...iconProps} aria-hidden="true"><path d="M5 6.5A4.5 4.5 0 0 1 9.5 2h5A4.5 4.5 0 0 1 19 6.5v4A4.5 4.5 0 0 1 14.5 15H12l-4 4v-4.2A4.5 4.5 0 0 1 5 10.5v-4Z" /><path d="M10 7.5a2 2 0 0 1 3.6 1.2c0 1.7-1.8 1.8-1.8 3.3" /><path d="M12 15h.01" /></svg>;
    case 'bookmark':
      return <svg {...iconProps} aria-hidden="true"><path d="M6 4h12v17l-6-3.8L6 21V4Z" /><path d="m9 11 2 2 4-4" /></svg>;
    case 'filePlus':
      return <svg {...iconProps} aria-hidden="true"><path d="M6 3h8l4 4v14H6V3Z" /><path d="M14 3v5h5" /><path d="M12 11v6" /><path d="M9 14h6" /></svg>;
    case 'target':
      return <svg {...iconProps} aria-hidden="true"><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="4" /><path d="M12 2v3" /><path d="M22 12h-3" /><path d="M12 22v-3" /><path d="M2 12h3" /></svg>;
    case 'code':
      return <svg {...iconProps} aria-hidden="true"><path d="m8 9-4 3 4 3" /><path d="m16 9 4 3-4 3" /><path d="m14 5-4 14" /></svg>;
    case 'cloudUpload':
      return <svg {...iconProps} aria-hidden="true"><path d="M16 16h2.5a3.5 3.5 0 0 0 .5-7 6 6 0 0 0-11.5-2A4.5 4.5 0 0 0 7 16h1" /><path d="M12 12v8" /><path d="m9 15 3-3 3 3" /></svg>;
    case 'database':
      return <svg {...iconProps} aria-hidden="true"><ellipse cx="12" cy="5" rx="7" ry="3" /><path d="M5 5v6c0 1.7 3.1 3 7 3s7-1.3 7-3V5" /><path d="M5 11v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6" /></svg>;
    case 'fileSearch':
      return <svg {...iconProps} aria-hidden="true"><path d="M6 3h8l4 4v6.5" /><path d="M14 3v5h5" /><path d="M6 3v18h7" /><circle cx="17" cy="17" r="3" /><path d="m19.2 19.2 2 2" /></svg>;
    case 'fileText':
      return <svg {...iconProps} aria-hidden="true"><path d="M6 3h8l4 4v14H6V3Z" /><path d="M14 3v5h5" /><path d="M9 12h6" /><path d="M9 16h6" /><path d="M9 8h2" /></svg>;
    case 'settings':
      return <svg {...iconProps} aria-hidden="true"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.8 1.8 0 0 0 .36 2l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.8 1.8 0 0 0-2-.36 1.8 1.8 0 0 0-1.1 1.66V21a2 2 0 1 1-4 0v-.09a1.8 1.8 0 0 0-1.1-1.66 1.8 1.8 0 0 0-2 .36l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.8 1.8 0 0 0 .36-2 1.8 1.8 0 0 0-1.66-1.1H3a2 2 0 1 1 0-4h.09a1.8 1.8 0 0 0 1.66-1.1 1.8 1.8 0 0 0-.36-2l-.06-.06A2 2 0 1 1 7.16 3.7l.06.06a1.8 1.8 0 0 0 2 .36A1.8 1.8 0 0 0 10.33 2.5V2a2 2 0 1 1 4 0v.09a1.8 1.8 0 0 0 1.1 1.66 1.8 1.8 0 0 0 2-.36l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.8 1.8 0 0 0-.36 2 1.8 1.8 0 0 0 1.66 1.1H21a2 2 0 1 1 0 4h-.09A1.8 1.8 0 0 0 19.4 15Z" /></svg>;
    case 'tag':
      return <svg {...iconProps} aria-hidden="true"><path d="M20 13 13 20 4 11V4h7l9 9Z" /><path d="M8.5 8.5h.01" /></svg>;
    case 'box':
      return <svg {...iconProps} aria-hidden="true"><path d="m21 8-9-5-9 5 9 5 9-5Z" /><path d="M3 8v8l9 5 9-5V8" /><path d="M12 13v8" /></svg>;
    case 'shield':
      return <svg {...iconProps} aria-hidden="true"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" /><path d="m9.5 12 1.8 1.8 3.8-4.1" /></svg>;
    case 'chart':
      return <svg {...iconProps} aria-hidden="true"><path d="M4 19V5" /><path d="M4 19h16" /><path d="M8 16v-5" /><path d="M12 16V8" /><path d="M16 16v-7" /></svg>;
    default:
      return null;
  }
}

/**
 * 认证页面组件
 * 包含登录、注册、找回密码三种模式的表单
 * 已登录用户会自动跳转到工作台
 */
function AuthPage() {
  const [mode, setMode] = useState<AuthMode>('login');
  const [form, setForm] = useState({ username: '', email: '', password: '', displayName: '' });
  const [localMessage, setLocalMessage] = useState<string | null>(null);
  const { login, register, loading, message, setMessage, user } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (user) navigate('/workspace');
  }, [navigate, user]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLocalMessage(null);
    try {
      if (mode === 'login') {
        await login(form.username, form.password);
        navigate('/workspace');
      } else if (mode === 'register') {
        await register(form);
        setMode('login');
      } else {
        await authApi.forgotPassword(form.email);
        setLocalMessage('如果邮箱存在，重置链接已经发送。');
      }
    } catch (error) {
      setLocalMessage((error as Error).message);
    }
  }

  return (
    <main className="auth-stage">
      <section className="auth-intro" aria-label="DevBrain-CQUPT">
        <div className="brand-lockup">
          <span className="brand-logo">DB</span>
          <div>
            <strong>DevBrain-CQUPT</strong>
            <span>研发知识中枢</span>
          </div>
        </div>
        <h1>面向研发团队的知识管理与问答工作台</h1>
        <p>
          统一组织知识库、文档、权限与智能问答入口，让团队在清晰稳定的界面中长期沉淀研发资产。
        </p>
        <div className="auth-highlights">
          <span>身份认证</span>
          <span>权限控制</span>
          <span>知识检索</span>
        </div>
      </section>

      <section className="auth-panel" aria-labelledby="auth-title">
        <div className="mode-switch" role="tablist" aria-label="认证模式">
          {(['login', 'register', 'forgot'] as AuthMode[]).map((item) => (
            <button
              key={item}
              type="button"
              className={mode === item ? 'active' : ''}
              onClick={() => {
                setMode(item);
                setMessage(null);
                setLocalMessage(null);
              }}
            >
              {item === 'login' ? '登录' : item === 'register' ? '注册' : '找回'}
            </button>
          ))}
        </div>
        <h2 id="auth-title">{mode === 'login' ? '登录系统' : mode === 'register' ? '创建账号' : '重置密码'}</h2>
        <form onSubmit={submit} className="stack-form">
          {mode !== 'forgot' && (
            <label>
              用户名
              <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} autoComplete="username" required />
            </label>
          )}
          {mode !== 'login' && (
            <label>
              邮箱
              <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} type="email" autoComplete="email" required />
            </label>
          )}
          {mode === 'register' && (
            <label>
              显示名称
              <input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
            </label>
          )}
          {mode !== 'forgot' && (
            <label>
              密码
              <input
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                required
                minLength={8}
              />
            </label>
          )}
          <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
            {loading ? '处理中...' : mode === 'login' ? '进入工作台' : mode === 'register' ? '创建账号' : '发送重置链接'}
          </button>
        </form>
        {(localMessage || message) && <p className="notice">{localMessage || message}</p>}
      </section>
    </main>
  );
}

/**
 * 密码重置页面
 * 通过邮件中的重置链接访问，用户输入新密码完成重置
 */
function ResetPasswordPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const token = new URLSearchParams(location.search).get('token') || '';
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      await authApi.resetPassword(token, password);
      setMessage('密码已重置，请重新登录。');
      setTimeout(() => navigate('/auth'), 900);
    } catch (error) {
      setMessage((error as Error).message);
    }
  }

  return (
    <main className="auth-stage compact">
      <section className="auth-panel">
        <p className="eyebrow">Reset password</p>
        <h2>重置访问密码</h2>
        <form className="stack-form" onSubmit={submit}>
          <label>
            新密码
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} required />
          </label>
          <button className="btn btn-primary">确认重置</button>
        </form>
        {message && <p className="notice">{message}</p>}
      </section>
    </main>
  );
}

/**
 * 应用外壳布局组件
 * 提供侧边栏导航、顶部搜索栏、用户信息等公共布局
 * @param children - 页面内容
 * @param mode - 布局模式：'front' 前台模式，'admin' 管理后台模式
 */
function AppShell({ children, mode = 'front' }: { children: ReactNode; mode?: ShellMode }) {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const initials = useMemo(() => (user?.displayName || user?.username || 'DB').slice(0, 2).toUpperCase(), [user]);
  const isAdminMode = mode === 'admin';
  const menu = isAdminMode ? adminMenuItems : frontMenuItems;

  async function handleLogout() {
    await logout();
    navigate('/auth');
  }

  return (
    <div className={isAdminMode ? 'app-shell admin-shell' : 'app-shell front-shell'}>
      <aside className="sidebar">
        <Link to={isAdminMode ? '/admin' : '/workspace'} className="sidebar-brand" aria-label="DevBrain-CQUPT">
          <span className="brand-logo">DB</span>
          <span>
            <strong className="brand-wordmark"><span>Dev</span><b>Brain</b></strong>
            <small>{isAdminMode ? '管理后台' : '研发知识中枢'}</small>
          </span>
        </Link>
        <nav className="sidebar-menu" aria-label="主菜单">
          {menu.map((item) => (
            <NavLink key={item.path} to={item.path} className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="menu-badge"><UiIcon name={item.icon} /></span>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="menu-badge"><UiIcon name="headphones" /></span>
          <span>{isAdminMode ? '帮助中心' : '帮助与反馈'}</span>
        </div>
      </aside>

      <div className="main-layout">
        <header className="topbar">
          <label className="global-search">
            <span>⌕</span>
            <input placeholder={isAdminMode ? '搜索用户、知识库、文档、日志...' : '全局搜索知识库、文档、问题...'} />
            <kbd>⌘ K</kbd>
          </label>
          <div className="topbar-user">
            <button className="top-icon-button" type="button" aria-label="消息通知"><UiIcon name="bell" /></button>
            <button className="top-icon-button" type="button" aria-label="帮助"><UiIcon name="help" /></button>
            <span className="avatar">{initials}</span>
            <div>
              <strong>{isAdminMode ? '系统管理员' : (user?.displayName || user?.username)}</strong>
              <small>{isAdminMode ? '平台运维' : user?.email}</small>
            </div>
            {user?.roles.includes('admin') && (
              <Link className="btn btn-light shell-switch" to={isAdminMode ? '/workspace' : '/admin'}>
                {isAdminMode ? '前台' : '后台'}
              </Link>
            )}
            <button className="btn btn-light" type="button" onClick={handleLogout}>退出登录</button>
          </div>
        </header>
        <main className="content-area">{children}</main>
      </div>
    </div>
  );
}

/**
 * 页面容器组件
 * 提供统一的页面标题、描述和操作按钮布局
 * @param title - 页面标题
 * @param description - 页面描述，可选
 * @param actions - 页面操作按钮区域，可选
 * @param children - 页面主体内容
 */
function PageContainer({
  title,
  description,
  actions,
  children,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="page-container">
      <div className="page-heading">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        {actions && <div className="page-actions">{actions}</div>}
      </div>
      {children}
    </section>
  );
}

/**
 * 工作台首页组件
 * 展示用户统计指标、快捷提问入口、问答预览和侧边推荐内容
 */
function WorkspacePage() {
  const user = useAuthStore((state) => state.user);
  if (!user) return null;

  return (
    <AppShell>
      <section className="front-dashboard">
        <div className="front-main">
          <section className="hero-panel">
            <div>
              <h1>你好，今天想解决什么研发问题？</h1>
              <span className="hero-underline" />
            </div>
            <div className="brain-visual" aria-hidden="true">
              <span>AI</span>
            </div>
          </section>

          <section className="front-metrics">
            <FrontMetric label="我的提问" value="128" delta="较上周 ↑ 18" tone="blue" icon="question" />
            <FrontMetric label="收藏文档" value="24" delta="较上周 ↑ 6" tone="green" icon="bookmark" />
            <FrontMetric label="本周新增资料" value="16" delta="较上周 ↑ 4" tone="purple" icon="filePlus" />
            <FrontMetric label="命中率" value="92%" delta="较上周 ↑ 7%" tone="cyan" icon="target" />
          </section>

          <section className="ask-box">
            <textarea placeholder="请输入今天的问题，例如：生产环境 Redis 连接失败如何排查？" />
            <div className="ask-actions">
              <button type="button">添加附件</button>
              <button type="button">选择知识库</button>
              <Link className="btn btn-primary" to="/assistant">发送 <span>Enter</span></Link>
            </div>
          </section>

          <section className="prompt-grid">
            {workspacePrompts.map(({ title, text, icon }) => (
              <Link className="prompt-card" to="/assistant" key={title}>
                <span><UiIcon name={icon} /></span>
                <strong>{title}</strong>
                <small>{text}</small>
              </Link>
            ))}
          </section>

          <section className="answer-preview">
            <div className="user-question">
              <span>生产环境 Redis 连接失败如何排查？</span>
              <small>今天 10:32</small>
            </div>
            <article className="assistant-answer">
              <span className="bot-avatar">AI</span>
              <div>
                <h3>问题分析</h3>
                <p>生产环境 Redis 连接失败通常由网络、配置、实例状态或资源限制导致，需要从多个维度进行排查。</p>
                <h4>推荐处理步骤</h4>
                <ol>
                  <li>检查网络连通性，确认应用服务器与 Redis 实例之间可达。</li>
                  <li>验证连接地址、端口、密码、超时时间等配置是否正确。</li>
                  <li>确认 Redis 控制台或 redis-cli 检查实例是否运行正常。</li>
                  <li>查看应用日志和 Redis 日志，获取具体错误信息。</li>
                </ol>
                <div className="answer-actions">
                  <button type="button">复制</button>
                  <button type="button">重新生成</button>
                  <button type="button">收藏</button>
                  <button type="button">点赞</button>
                </div>
              </div>
            </article>
          </section>
        </div>

        <aside className="front-side">
          <InsightCard title="引用来源" action="查看全部来源（8）">
            {['Redis 部署手册.md', '运维故障 SOP', '历史问题：连接超时排查记录'].map((item, index) => (
              <div className="source-row" key={item}>
                <span>{index + 1}</span>
                <strong>{item}</strong>
                <small>{index === 0 ? 'README' : index === 1 ? 'SOP' : '运维记录'}</small>
              </div>
            ))}
          </InsightCard>

          <InsightCard title="相关文档" action="查看更多">
            {['Redis 高可用部署方案', '生产环境故障排查指南', 'Redis 连接超时问题分析'].map((item, index) => (
              <div className="doc-row" key={item}>
                <span><UiIcon name="fileText" /></span>
                <div>
                  <strong>{item}</strong>
                  <small>{index === 0 ? '2024-05-12' : index === 1 ? '2024-05-08' : '2024-05-05'}</small>
                </div>
              </div>
            ))}
          </InsightCard>

          <InsightCard title="推荐问题">
            {['Redis 频繁断连如何解决？', '如何排查数据库慢查询？', '服务 CPU 使用率过高怎么办？', '如何定位接口超时问题？'].map((item) => (
              <Link className="question-row" to="/assistant" key={item}>{item}<span>›</span></Link>
            ))}
          </InsightCard>
        </aside>
      </section>
    </AppShell>
  );
}

/**
 * 前台知识库页面
 * 展示可用知识库列表和最近文档，供普通用户浏览
 */
function FrontKnowledgePage() {
  const knowledgeBases = [
    ['DevBrain 项目知识库', '项目架构、接口规范、部署手册与常见问题', '128 篇文档', '12,842 次引用'],
    ['运维故障知识库', '故障复盘、排查 SOP、告警处理和恢复方案', '86 篇文档', '8,731 次引用'],
    ['接口文档中心', '后端接口、联调记录、变更说明和错误码', '64 篇文档', '6,245 次引用'],
  ];
  const documents = [
    ['Redis部署手册.md', '运维文档', '2024-05-12'],
    ['生产环境故障排查指南', 'SOP', '2024-05-08'],
    ['接口文档规范.xlsx', '接口文档', '2024-05-05'],
  ];

  return (
    <AppShell>
      <PageContainer title="知识库" description="浏览可用知识库、查看文档列表，并进入文档详情。">
        <section className="content-grid three-columns">
          {knowledgeBases.map(([name, desc, docs, hits], index) => (
            <article className="card kb-front-card" key={name}>
              <div className="empty-icon">{index + 1}</div>
              <h3>{name}</h3>
              <p>{desc}</p>
              <div className="kb-front-meta">
                <span>{docs}</span>
                <span>{hits}</span>
              </div>
              <Link className="btn btn-light" to={`/knowledge-bases/kb-${index + 1}/documents`}>查看文档</Link>
            </article>
          ))}
        </section>

        <article className="card table-card">
          <div className="card-title">
            <div>
              <h3>最近文档</h3>
              <p>来自已授权知识库的最新资料</p>
            </div>
          </div>
          <table className="data-table">
            <thead>
              <tr>
                <th>文档名称</th>
                <th>类型</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {documents.map(([name, type, date], index) => (
                <tr key={name}>
                  <td>{name}</td>
                  <td><span className="status-pill muted">{type}</span></td>
                  <td>{date}</td>
                  <td><Link className="btn btn-light" to={`/knowledge-bases/kb-${index + 1}/documents/doc-${index + 1}`}>查看详情</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>
      </PageContainer>
    </AppShell>
  );
}

/**
 * 管理后台 - 知识库管理页面
 * 提供知识库的 CRUD 操作、搜索筛选、统计指标和详情查看
 */
function KnowledgeBasePage() {
  const navigate = useNavigate();
  const [records, setRecords] = useState<KnowledgeBaseItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<KnowledgeBaseStatus | ''>('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [appliedStatus, setAppliedStatus] = useState<KnowledgeBaseStatus | ''>('');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [modalMode, setModalMode] = useState<KnowledgeBaseModalMode | null>(null);
  const [editingItem, setEditingItem] = useState<KnowledgeBaseItem | null>(null);
  const [form, setForm] = useState<KnowledgeBaseFormState>(emptyKnowledgeBaseForm);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<KnowledgeBaseItem | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [joinOpen, setJoinOpen] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    knowledgeBaseApi.getKnowledgeBases({
      pageNo,
      pageSize,
      keyword: appliedKeyword,
      status: appliedStatus,
    }).then((page) => {
      if (!active) return;
      setRecords(page.records || []);
      setTotal(page.total || 0);
      setPages(page.pages || 0);
    }).catch((nextError) => {
      if (!active) return;
      setRecords([]);
      setError(getErrorMessage(nextError));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [appliedKeyword, appliedStatus, pageNo, pageSize, reloadKey]);

  function refreshList() {
    setReloadKey((value) => value + 1);
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setAppliedKeyword(keyword.trim());
    setAppliedStatus(status);
    setPageNo(1);
  }

  function resetSearch() {
    setKeyword('');
    setStatus('');
    setAppliedKeyword('');
    setAppliedStatus('');
    setPageNo(1);
  }

  function openCreateModal() {
    setMessage(null);
    setFormError(null);
    setEditingItem(null);
    setForm(emptyKnowledgeBaseForm);
    setModalMode('create');
  }

  function openEditModal(item: KnowledgeBaseItem) {
    setMessage(null);
    setFormError(null);
    setEditingItem(item);
    setForm({
      name: item.name,
      description: item.description || '',
      collectionName: item.collectionName,
      embeddingModel: item.embeddingModel,
      status: item.status === 'disabled' ? 'disabled' : 'enabled',
      accessLevel: 'team',
    });
    setModalMode('edit');
  }

  async function submitKnowledgeBase(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    setError(null);
    setFormError(null);
    try {
      if (modalMode === 'create') {
        await knowledgeBaseApi.createKnowledgeBase({
          name: form.name.trim(),
          description: form.description.trim(),
          collectionName: form.collectionName.trim(),
          embeddingModel: form.embeddingModel.trim(),
          status: form.status,
        });
        setPageNo(1);
        setMessage('知识库已创建');
      } else if (modalMode === 'edit' && editingItem) {
        await knowledgeBaseApi.updateKnowledgeBase(editingItem.id, {
          name: form.name.trim(),
          description: form.description.trim(),
          embeddingModel: form.embeddingModel.trim(),
          status: form.status,
        });
        setMessage('知识库已更新');
      }
      setModalMode(null);
      setEditingItem(null);
      refreshList();
    } catch (nextError) {
      setFormError(getErrorMessage(nextError));
    } finally {
      setSaving(false);
    }
  }

  async function deleteKnowledgeBase(item: KnowledgeBaseItem) {
    if (!window.confirm(`确认删除知识库「${item.name}」吗？删除前请确保该知识库下没有文档。`)) {
      return;
    }
    setMessage(null);
    setError(null);
    try {
      await knowledgeBaseApi.deleteKnowledgeBase(item.id);
      setMessage('知识库已删除');
      if (records.length === 1 && pageNo > 1) {
        setPageNo((value) => Math.max(1, value - 1));
      } else {
        refreshList();
      }
    } catch (nextError) {
      setError(getErrorMessage(nextError));
    }
  }

  async function openDetail(item: KnowledgeBaseItem) {
    setDetailOpen(true);
    setDetail(null);
    setDetailError(null);
    setDetailLoading(true);
    try {
      setDetail(await knowledgeBaseApi.getKnowledgeBase(item.id));
    } catch (nextError) {
      setDetailError(getErrorMessage(nextError));
    } finally {
      setDetailLoading(false);
    }
  }

  const enabledCount = records.filter((item) => item.status !== 'disabled').length;
  const totalDocuments = records.reduce((sum, item) => sum + (item.documentCount || 0), 0);
  const totalChunks = records.reduce((sum, item) => sum + (item.chunkCount || 0), 0);
  const recentRecords = [...records]
    .sort((a, b) => new Date(b.updateTime || b.createTime || '').getTime() - new Date(a.updateTime || a.createTime || '').getTime())
    .slice(0, 5);
  const progressMetrics = [
    { label: '知识库', value: total, color: 'blue' },
    { label: '文档', value: totalDocuments, color: 'green' },
    { label: '分块', value: totalChunks, color: 'teal' },
    { label: '停用', value: records.filter((item) => item.status === 'disabled').length, color: 'red' },
  ];
  const selectedEmbeddingModel = embeddingModelOptions.find((option) => option.value === form.embeddingModel);
  const embeddingModelHint = selectedEmbeddingModel?.hint
    ?? (form.embeddingModel ? '当前知识库使用历史模型配置，保存前建议切换为当前可选模型。' : '请选择 Embedding 模型。');

  return (
    <AppShell mode="admin">
      <PageContainer
        title="知识库管理"
        description="统一管理研发文档、SOP、接口文档与运维知识。"
        actions={
          <div className="page-actions">
            <button className="btn btn-light" type="button" onClick={() => setJoinOpen(true)}>加入知识库</button>
            <button className="btn btn-primary" type="button" onClick={openCreateModal}>新建知识库</button>
          </div>
        }
      >
        {error && <div className="error-banner">{error}</div>}
        {message && <p className="toast-line">{message}</p>}

        <section className="kb-dashboard-metrics">
          <article className="kb-metric-card primary">
            <span><UiIcon name="database" /></span>
            <div>
              <p>知识库总数</p>
              <strong>{total}</strong>
              <small>当前页 {records.length} 个</small>
            </div>
          </article>
          <article className="kb-metric-card green">
            <span><UiIcon name="fileText" /></span>
            <div>
              <p>文档总数</p>
              <strong>{totalDocuments}</strong>
              <small>当前页汇总</small>
            </div>
          </article>
          <article className="kb-metric-card violet">
            <span><UiIcon name="filePlus" /></span>
            <div>
              <p>Chunk 数量</p>
              <strong>{totalChunks || '--'}</strong>
              <small>已生成分块</small>
            </div>
          </article>
          <article className="kb-metric-card amber">
            <span><UiIcon name="shield" /></span>
            <div>
              <p>启用中</p>
              <strong>{enabledCount}</strong>
              <small>{records.length ? `${Math.round((enabledCount / records.length) * 100)}% 可用` : '暂无数据'}</small>
            </div>
          </article>
        </section>

        <section className="kb-dashboard-grid">
          <div className="kb-main-panel">
            <div className="kb-quick-actions">
              <button className="btn btn-primary" type="button" onClick={openCreateModal}>＋ 新建知识库</button>
              <button className="btn btn-light" type="button" onClick={() => setJoinOpen(true)}>加入知识库</button>
              <form className="kb-inline-filter" onSubmit={submitSearch}>
                <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索知识库名称" />
                <select value={status} onChange={(event) => setStatus(event.target.value as KnowledgeBaseStatus | '')}>
                  <option value="">状态：全部</option>
                  <option value="enabled">启用</option>
                  <option value="disabled">停用</option>
                </select>
                <button className="btn btn-light" type="submit">筛选</button>
                <button className="btn btn-light" type="button" onClick={resetSearch}>重置</button>
              </form>
            </div>

            <article className="card table-card knowledge-table-card kb-console-table">
              <div className="card-title">
                <div>
                  <h3>知识库列表</h3>
                  <p>{total} 条记录</p>
                </div>
              </div>

              {loading ? (
                <div className="loading-state">正在加载知识库...</div>
              ) : records.length ? (
                <table className="data-table knowledge-table">
                  <thead>
                    <tr>
                      <th>知识库名称</th>
                      <th>文档数</th>
                      <th>更新时间</th>
                      <th>状态</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.map((item, index) => (
                      <tr key={item.id}>
                        <td>
                          <div className="kb-name-cell">
                            <span className={`kb-row-icon tone-${index % 5}`}><UiIcon name={index % 2 === 0 ? 'database' : 'fileText'} /></span>
                            <div>
                              <strong>{item.name}</strong>
                              <small>{item.description || item.collectionName}</small>
                            </div>
                          </div>
                        </td>
                        <td>{item.documentCount ?? 0}</td>
                        <td>{formatDate(item.updateTime || item.createTime)}</td>
                        <td><StatusBadge status={item.status} /></td>
                        <td>
                          <div className="table-actions">
                            <button className="btn btn-light" type="button" onClick={() => openDetail(item)}>查看</button>
                            <button className="btn btn-primary" type="button" onClick={() => navigate(`/admin/knowledge-bases/${item.id}/documents`)}>进入</button>
                            <button className="btn btn-light" type="button" onClick={() => openEditModal(item)}>编辑</button>
                            <button className="btn btn-danger" type="button" onClick={() => deleteKnowledgeBase(item)}>删除</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div className="empty-state">
                  <div className="empty-icon">知</div>
                  <h3>暂无知识库</h3>
                  <p>可以新建知识库，或调整搜索条件后再试。</p>
                </div>
              )}

              <div className="pagination-bar">
                <span>第 {pages ? pageNo : 0} / {pages} 页</span>
                <label>
                  每页
                  <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPageNo(1); }}>
                    {pageSizeOptions.map((size) => <option key={size} value={size}>{size}</option>)}
                  </select>
                </label>
                <button className="btn btn-light" type="button" disabled={loading || pageNo <= 1} onClick={() => setPageNo((value) => Math.max(1, value - 1))}>上一页</button>
                <button className="btn btn-light" type="button" disabled={loading || pageNo >= pages || pages === 0} onClick={() => setPageNo((value) => value + 1)}>下一页</button>
              </div>
            </article>
          </div>

          <aside className="kb-side-panel">
            <article className="card kb-activity-card">
              <header>
                <h3>最近更新</h3>
                <button type="button" onClick={refreshList}>查看全部</button>
              </header>
              <div className="kb-activity-list">
                {recentRecords.length ? recentRecords.map((item, index) => (
                  <button key={item.id} type="button" onClick={() => navigate(`/admin/knowledge-bases/${item.id}/documents`)}>
                    <span className={`kb-row-icon tone-${index % 5}`}><UiIcon name={index % 2 === 0 ? 'book' : 'settings'} /></span>
                    <div>
                      <strong>{item.name}</strong>
                      <small>{item.updatedBy || item.createdBy || '系统'} 更新了知识库</small>
                    </div>
                    <em>{formatShortDate(item.updateTime || item.createTime)}</em>
                  </button>
                )) : <p className="muted-empty">暂无更新记录。</p>}
              </div>
            </article>

            <article className="card kb-progress-card">
              <header>
                <h3>处理进度</h3>
                <span>当前页</span>
              </header>
              <div className="kb-progress-metrics">
                {progressMetrics.map((item) => (
                  <div key={item.label} className={`kb-progress-value ${item.color}`}>
                    <span>{item.label}</span>
                    <strong>{item.value}</strong>
                  </div>
                ))}
              </div>
              <div className="kb-progress-chart" aria-hidden="true">
                <span style={{ height: '42%' }} />
                <span style={{ height: '56%' }} />
                <span style={{ height: '48%' }} />
                <span style={{ height: '72%' }} />
                <span style={{ height: '68%' }} />
                <span style={{ height: '61%' }} />
                <span style={{ height: '74%' }} />
              </div>
            </article>
          </aside>
        </section>

        {modalMode && (
          <Modal
            title={modalMode === 'create' ? '创建知识库' : '编辑知识库'}
            onClose={() => { if (!saving) setModalMode(null); }}
            footer={(
              <>
                <button className="btn btn-light" type="button" disabled={saving} onClick={() => setModalMode(null)}>取消</button>
                <button className="btn btn-primary" type="submit" form="knowledge-base-form" disabled={saving}>{saving ? '保存中...' : '保存'}</button>
              </>
            )}
          >
            {formError && <div className="error-banner modal-error">{formError}</div>}
            <form id="knowledge-base-form" className="stack-form" onSubmit={submitKnowledgeBase}>
              <label>知识库名称<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required maxLength={128} /></label>
              <label>描述<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} maxLength={512} rows={4} /></label>
              {modalMode === 'create' && (
                <>
                  <label>集合名<input value={form.collectionName} onChange={(event) => setForm({ ...form, collectionName: event.target.value })} required maxLength={64} placeholder="dev_knowledge" /></label>
                  <label>
                    访问权限
                    <select value={form.accessLevel} onChange={(event) => setForm({ ...form, accessLevel: event.target.value as KnowledgeBaseFormState['accessLevel'] })}>
                      <option value="private">仅创建者</option>
                      <option value="team">团队成员</option>
                      <option value="organization">组织可见</option>
                    </select>
                  </label>
                </>
              )}
              <label>
                Embedding 模型
                <select
                  value={form.embeddingModel}
                  onChange={(event) => setForm({ ...form, embeddingModel: event.target.value })}
                  required
                  aria-describedby="embedding-model-hint"
                >
                  <option value="" disabled>请选择 Embedding 模型</option>
                  {form.embeddingModel && !selectedEmbeddingModel && (
                    <option value={form.embeddingModel}>当前配置 - {form.embeddingModel}</option>
                  )}
                  {embeddingModelGroups.map((group) => (
                    <optgroup key={group.label} label={group.label}>
                      {group.options.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </optgroup>
                  ))}
                </select>
                <small id="embedding-model-hint" className="field-hint">{embeddingModelHint}</small>
              </label>
              <label>
                状态
                <select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as KnowledgeBaseStatus })}>
                  <option value="enabled">启用</option>
                  <option value="disabled">停用</option>
                </select>
              </label>
            </form>
          </Modal>
        )}

        {joinOpen && (
          <JoinKnowledgeBaseModal
            records={records}
            onClose={() => setJoinOpen(false)}
            onEnter={(item) => {
              setJoinOpen(false);
              navigate(`/admin/knowledge-bases/${item.id}/documents`);
            }}
          />
        )}

        {detailOpen && (
          <Modal title="知识库详情" onClose={() => setDetailOpen(false)} footer={<button className="btn btn-primary" type="button" onClick={() => setDetailOpen(false)}>关闭</button>}>
            {detailLoading && <div className="loading-state">正在加载详情...</div>}
            {detailError && <div className="error-banner">{detailError}</div>}
            {detail && (
              <div className="detail-grid">
                <span>名称</span><strong>{detail.name}</strong>
                <span>描述</span><p>{detail.description || '--'}</p>
                <span>Embedding 模型</span><strong>{detail.embeddingModel}</strong>
                <span>集合名</span><code>{detail.collectionName}</code>
                <span>文档数量</span><strong>{detail.documentCount ?? 0}</strong>
                <span>Chunk 数量</span><strong>{detail.chunkCount ?? '--'}</strong>
                <span>状态</span><StatusBadge status={detail.status} />
                <span>创建时间</span><strong>{formatDate(detail.createTime)}</strong>
                <span>更新时间</span><strong>{formatDate(detail.updateTime)}</strong>
              </div>
            )}
          </Modal>
        )}
      </PageContainer>
    </AppShell>
  );
}

/**
 * 加入知识库弹窗组件
 * 支持搜索加入、邀请链接、组织列表三种方式
 */
function JoinKnowledgeBaseModal({
  records,
  onClose,
  onEnter,
}: {
  records: KnowledgeBaseItem[];
  onClose: () => void;
  onEnter: (item: KnowledgeBaseItem) => void;
}) {
  const [mode, setMode] = useState<JoinKnowledgeBaseMode>('search');
  const [keyword, setKeyword] = useState('');
  const [inviteLink, setInviteLink] = useState('');
  const matched = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return records.slice(0, 5);
    return records.filter((item) =>
      item.name.toLowerCase().includes(value)
      || item.collectionName.toLowerCase().includes(value)
      || (item.description || '').toLowerCase().includes(value),
    ).slice(0, 8);
  }, [keyword, records]);

  return (
    <Modal
      title="加入知识库"
      onClose={onClose}
      footer={<button className="btn btn-light" type="button" onClick={onClose}>关闭</button>}
    >
      <div className="join-kb-modal">
        <div className="source-mode-tabs" role="tablist" aria-label="加入方式">
          <button className={mode === 'search' ? 'active' : ''} type="button" onClick={() => setMode('search')}>搜索加入</button>
          <button className={mode === 'invite' ? 'active' : ''} type="button" onClick={() => setMode('invite')}>邀请链接</button>
          <button className={mode === 'organization' ? 'active' : ''} type="button" onClick={() => setMode('organization')}>组织列表</button>
        </div>

        {mode === 'search' && (
          <div className="stack-form">
            <label>
              搜索知识库
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="输入知识库名称、集合名或描述" />
            </label>
            <div className="join-result-list">
              {matched.map((item) => (
                <button key={item.id} type="button" onClick={() => onEnter(item)}>
                  <strong>{item.name}</strong>
                  <span>{item.description || item.collectionName}</span>
                  <em>{item.status === 'disabled' ? '停用' : '进入'}</em>
                </button>
              ))}
              {!matched.length && <p className="muted-empty">没有匹配的知识库。</p>}
            </div>
          </div>
        )}

        {mode === 'invite' && (
          <div className="stack-form">
            <label>
              邀请链接或邀请码
              <input value={inviteLink} onChange={(event) => setInviteLink(event.target.value)} placeholder="粘贴邀请链接或输入邀请码" />
            </label>
            <div className="hint-panel">
              <strong>邀请加入</strong>
              <p>当前前端已预留入口；后端成员邀请接口接入后，这里会校验链接并完成加入。</p>
            </div>
          </div>
        )}

        {mode === 'organization' && (
          <div className="org-kb-grid">
            {records.slice(0, 6).map((item) => (
              <button key={item.id} type="button" onClick={() => onEnter(item)}>
                <UiIcon name="database" />
                <strong>{item.name}</strong>
                <span>{item.documentCount ?? 0} 个文档</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
}

/**
 * 管理后台 - 文档管理页面
 * 跨知识库管理所有文档，支持筛选、启停用、删除和上传
 */
function AdminDocumentsPage() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [records, setRecords] = useState<KnowledgeDocumentItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [kbId, setKbId] = useState('');
  const [status, setStatus] = useState('');
  const [enabled, setEnabled] = useState<number | ''>('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [appliedKbId, setAppliedKbId] = useState('');
  const [appliedStatus, setAppliedStatus] = useState('');
  const [appliedEnabled, setAppliedEnabled] = useState<number | ''>('');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [lookupLoading, setLookupLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);

  useEffect(() => {
    let active = true;
    setLookupLoading(true);
    knowledgeBaseApi.getKnowledgeBases({ pageNo: 1, pageSize: 100 })
      .then((page) => {
        if (active) setKnowledgeBases(page.records || []);
      })
      .catch((nextError) => {
        if (active) setError(getErrorMessage(nextError));
      })
      .finally(() => {
        if (active) setLookupLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    knowledgeBaseApi.getAllKnowledgeDocuments({
      pageNo,
      pageSize,
      kbId: appliedKbId,
      keyword: appliedKeyword,
      status: appliedStatus,
      enabled: appliedEnabled,
    }).then((page) => {
      if (!active) return;
      setRecords(page.records || []);
      setTotal(page.total || 0);
      setPages(page.pages || 0);
    }).catch((nextError) => {
      if (!active) return;
      setRecords([]);
      setTotal(0);
      setPages(0);
      setError(getErrorMessage(nextError));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [appliedEnabled, appliedKbId, appliedKeyword, appliedStatus, pageNo, pageSize, reloadKey]);

  const knowledgeBaseNameById = useMemo(() => {
    return new Map(knowledgeBases.map((item) => [item.id, item.name]));
  }, [knowledgeBases]);

  function refresh() {
    setReloadKey((value) => value + 1);
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setAppliedKeyword(keyword.trim());
    setAppliedKbId(kbId);
    setAppliedStatus(status);
    setAppliedEnabled(enabled);
    setPageNo(1);
  }

  function resetSearch() {
    setKeyword('');
    setKbId('');
    setStatus('');
    setEnabled('');
    setAppliedKeyword('');
    setAppliedKbId('');
    setAppliedStatus('');
    setAppliedEnabled('');
    setPageNo(1);
  }

  async function handleDelete(doc: KnowledgeDocumentItem) {
    if (!window.confirm(`确认删除文档「${doc.docName}」吗？`)) return;
    setMessage(null);
    setError(null);
    try {
      await knowledgeBaseApi.deleteKnowledgeDocument(doc.kbId, doc.id);
      setMessage('文档已删除');
      if (records.length === 1 && pageNo > 1) {
        setPageNo((value) => Math.max(1, value - 1));
      } else {
        refresh();
      }
    } catch (nextError) {
      setError(getErrorMessage(nextError));
    }
  }

  async function handleToggleEnabled(doc: KnowledgeDocumentItem) {
    setMessage(null);
    setError(null);
    try {
      await knowledgeBaseApi.toggleDocumentEnabled(doc.kbId, doc.id, doc.enabled === 1 ? 0 : 1);
      setMessage(doc.enabled === 1 ? '文档已禁用' : '文档已启用');
      refresh();
    } catch (nextError) {
      setError(getErrorMessage(nextError));
    }
  }

  return (
    <AppShell mode="admin">
      <PageContainer
        title="文档管理"
        description="集中管理全部知识库文档、上传入口、解析状态和启停操作。"
        actions={<button className="btn btn-primary" type="button" onClick={() => setUploadOpen(true)}>上传文档</button>}
      >
        <form className="card filter-toolbar document-filter-toolbar" onSubmit={submitSearch}>
          <label className="toolbar-field">
            知识库
            <select value={kbId} onChange={(event) => setKbId(event.target.value)} disabled={lookupLoading}>
              <option value="">全部知识库</option>
              {knowledgeBases.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <label className="toolbar-field">
            文档名称
            <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索文档名称" />
          </label>
          <label className="toolbar-field compact">
            状态
            <select value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">全部</option>
              <option value="pending">等待处理</option>
              <option value="processing">处理中</option>
              <option value="completed">处理成功</option>
              <option value="failed">处理失败</option>
            </select>
          </label>
          <label className="toolbar-field compact">
            启用
            <select value={enabled} onChange={(event) => setEnabled(event.target.value === '' ? '' : Number(event.target.value))}>
              <option value="">全部</option>
              <option value={1}>已启用</option>
              <option value={0}>已禁用</option>
            </select>
          </label>
          <div className="toolbar-actions">
            <button className="btn btn-primary" type="submit">搜索</button>
            <button className="btn btn-light" type="button" onClick={resetSearch}>重置</button>
          </div>
        </form>

        {error && <div className="error-banner">{error}</div>}
        {message && <p className="toast-line">{message}</p>}

        <article className="card table-card knowledge-table-card">
          <div className="card-title">
            <div>
              <h3>文档列表</h3>
              <p>{total} 条记录</p>
            </div>
            <button className="btn btn-light" type="button" onClick={refresh} disabled={loading}>刷新</button>
          </div>

          {loading ? (
            <div className="loading-state">正在加载文档...</div>
          ) : records.length ? (
            <table className="data-table document-table">
              <thead>
                <tr>
                  <th>文档名称</th>
                  <th>知识库</th>
                  <th>类型</th>
                  <th>大小</th>
                  <th>处理模式</th>
                  <th>状态</th>
                  <th>启用</th>
                  <th>上传时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((doc) => (
                  <tr key={doc.id}>
                    <td><strong>{doc.docName}</strong></td>
                    <td className="muted-cell">{knowledgeBaseNameById.get(doc.kbId) || doc.kbId}</td>
                    <td><span className="status-pill muted">{doc.fileType || '--'}</span></td>
                    <td>{formatFileSize(doc.fileSize)}</td>
                    <td>{doc.processMode || '--'}</td>
                    <td><span className={`status-pill doc-status-${docStatusClass(doc.status)}`}>{docStatusLabel(doc.status)}</span></td>
                    <td>{doc.enabled === 1 ? '已启用' : '已禁用'}</td>
                    <td>{formatDate(doc.createTime)}</td>
                    <td>
                      <div className="table-actions">
                        <button className={doc.enabled === 1 ? 'btn btn-light' : 'btn btn-primary'} type="button" onClick={() => handleToggleEnabled(doc)}>
                          {doc.enabled === 1 ? '禁用' : '启用'}
                        </button>
                        <button className="btn btn-danger" type="button" onClick={() => handleDelete(doc)}>删除</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="empty-state">
              <div className="empty-icon">文</div>
              <h3>暂无文档</h3>
              <p>点击右上角「上传文档」添加第一份资料。</p>
              <button className="btn btn-primary" type="button" onClick={() => setUploadOpen(true)}>上传文档</button>
            </div>
          )}

          <div className="pagination-bar">
            <span>第 {pages ? pageNo : 0} / {pages} 页</span>
            <label>
              每页
              <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPageNo(1); }}>
                {pageSizeOptions.map((size) => <option key={size} value={size}>{size}</option>)}
              </select>
            </label>
            <button className="btn btn-light" type="button" disabled={loading || pageNo <= 1} onClick={() => setPageNo((value) => Math.max(1, value - 1))}>上一页</button>
            <button className="btn btn-light" type="button" disabled={loading || pageNo >= pages || pages === 0} onClick={() => setPageNo((value) => value + 1)}>下一页</button>
          </div>
        </article>

        {uploadOpen && (
          <DocumentUploadModal
            knowledgeBases={knowledgeBases}
            onClose={() => setUploadOpen(false)}
            onSuccess={() => { setUploadOpen(false); setPageNo(1); refresh(); setMessage('文档上传成功'); }}
          />
        )}
      </PageContainer>
    </AppShell>
  );
}

/**
 * 知识库文档列表页面
 * 展示指定知识库下的所有文档，支持搜索、筛选、上传、编辑、分块处理等操作
 * @param mode - 页面模式：'front' 前台，'admin' 管理后台
 */
function KnowledgeBaseDocumentsPage({ mode = 'front' }: { mode?: ShellMode }) {
  const { id: kbId } = useParams();
  const [knowledgeBase, setKnowledgeBase] = useState<KnowledgeBaseItem | null>(null);
  const [allDocuments, setAllDocuments] = useState<KnowledgeDocumentItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [documentAction, setDocumentAction] = useState<DocumentActionMode | null>(null);
  const [editingDocument, setEditingDocument] = useState<KnowledgeDocumentItem | null>(null);
  const [chunkingDocument, setChunkingDocument] = useState<KnowledgeDocumentItem | null>(null);
  const [chunkResultDocument, setChunkResultDocument] = useState<KnowledgeDocumentItem | null>(null);

  useEffect(() => {
    if (!kbId) return;
    let active = true;
    setLoading(true);
    setError(null);
    Promise.all([
      knowledgeBaseApi.getKnowledgeBase(kbId),
      knowledgeBaseApi.getKnowledgeDocuments(kbId),
    ])
      .then(([kb, list]) => {
        if (!active) return;
        setKnowledgeBase(kb);
        const filtered = list || [];
        filtered.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime());
        setAllDocuments(filtered);
      })
      .catch((err) => { if (active) { setAllDocuments([]); setError(getErrorMessage(err)); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [kbId, reloadKey]);

  const hasProcessingDocument = useMemo(
    () => allDocuments.some((doc) => normalizeDocStatus(doc.status) === 'processing'),
    [allDocuments],
  );

  useEffect(() => {
    if (!kbId || !hasProcessingDocument) return;
    let active = true;
    const timer = window.setInterval(() => {
      knowledgeBaseApi.getKnowledgeDocuments(kbId)
        .then((list) => {
          if (!active) return;
          const next = list || [];
          next.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime());
          setAllDocuments(next);
        })
        .catch((err) => {
          if (active) setError(getErrorMessage(err));
        });
    }, 2000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [kbId, hasProcessingDocument]);

  const filteredDocuments = useMemo(() => {
    let result = allDocuments;
    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase();
      result = result.filter((doc) => doc.docName.toLowerCase().includes(kw));
    }
    if (statusFilter) {
      result = result.filter((doc) => normalizeDocStatus(doc.status) === statusFilter);
    }
    return result;
  }, [allDocuments, keyword, statusFilter]);

  const total = filteredDocuments.length;
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const safePageNo = Math.min(pageNo, pages);
  const pagedDocuments = filteredDocuments.slice((safePageNo - 1) * pageSize, safePageNo * pageSize);

  useEffect(() => {
    if (pageNo > pages) setPageNo(pages);
  }, [pages, pageNo]);

  const refresh = useCallback(() => setReloadKey((k) => k + 1), []);

  function resetFilters() {
    setKeyword('');
    setStatusFilter('');
    setPageNo(1);
  }

  async function handleDelete(doc: KnowledgeDocumentItem) {
    if (!window.confirm(`确认删除文档「${doc.docName}」吗？`)) return;
    setMessage(null);
    setError(null);
    try {
      await knowledgeBaseApi.deleteKnowledgeDocument(kbId!, doc.id);
      setMessage('文档已删除');
      refresh();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function handleToggleEnabled(doc: KnowledgeDocumentItem) {
    setMessage(null);
    setError(null);
    try {
      await knowledgeBaseApi.toggleDocumentEnabled(kbId!, doc.id, doc.enabled === 1 ? 0 : 1);
      setMessage(doc.enabled === 1 ? '文档已禁用' : '文档已启用');
      refresh();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  function handleActionSuccess(nextMessage: string) {
    setDocumentAction(null);
    setEditingDocument(null);
    setChunkingDocument(null);
    refresh();
    setMessage(nextMessage);
  }

  return (
    <AppShell mode={mode}>
      <PageContainer
        title={knowledgeBase ? knowledgeBase.name : '知识库文档'}
        description="在当前知识库内创建、上传、编辑文档，并由用户手动触发分块处理。"
        actions={
          <div className="page-actions">
            <Link className="btn btn-light" to={mode === 'admin' ? '/admin/knowledge-bases' : '/knowledge-bases'}>返回知识库</Link>
            <button className="btn btn-light" type="button" onClick={() => setDocumentAction('blank')}>新建文档</button>
            <button className="btn btn-primary" type="button" onClick={() => { setDocumentAction('upload'); setUploadOpen(true); }}>上传文件</button>
          </div>
        }
      >
        <section className="kb-context-panel">
          <div>
            <span>当前知识库</span>
            <strong>{knowledgeBase?.name || kbId}</strong>
            <small>{knowledgeBase?.description || '文档与分块结果都会归属到该知识库。'}</small>
          </div>
          <div>
            <span>文档数</span>
            <strong>{allDocuments.length}</strong>
          </div>
          <div>
            <span>分块数</span>
            <strong>{allDocuments.reduce((sum, doc) => sum + (doc.chunkCount || 0), 0)}</strong>
          </div>
          <div>
            <span>集合名</span>
            <code>{knowledgeBase?.collectionName || '--'}</code>
          </div>
        </section>

        <form className="card filter-toolbar" onSubmit={(e) => { e.preventDefault(); setPageNo(1); }}>
          <label className="toolbar-field">
            文档名称
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜索文档名称" />
          </label>
          <label className="toolbar-field compact">
            状态
            <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPageNo(1); }}>
              <option value="">全部</option>
              <option value="pending">等待处理</option>
              <option value="processing">处理中</option>
              <option value="completed">处理成功</option>
              <option value="failed">处理失败</option>
            </select>
          </label>
          <div className="toolbar-actions">
            <button className="btn btn-primary" type="submit">搜索</button>
            <button className="btn btn-light" type="button" onClick={resetFilters}>重置</button>
          </div>
        </form>

        {error && <div className="error-banner">{error}</div>}
        {message && <p className="toast-line">{message}</p>}

        <article className="card table-card">
          <div className="card-title">
            <div>
              <h3>文档列表</h3>
              <p>{total} 条记录</p>
            </div>
          </div>

          {loading ? (
            <div className="loading-state">正在加载文档...</div>
          ) : pagedDocuments.length ? (
            <table className="data-table">
              <thead>
                <tr>
                  <th>文档名称</th>
                  <th>类型</th>
                  <th>大小</th>
                  <th>处理模式</th>
                  <th>分块数</th>
                  <th>状态</th>
                  <th>启用</th>
                  <th>上传时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {pagedDocuments.map((doc) => (
                  <tr key={doc.id}>
                    <td><strong>{doc.docName}</strong></td>
                    <td><span className="status-pill muted">{doc.fileType}</span></td>
                    <td>{formatFileSize(doc.fileSize)}</td>
                    <td>{doc.processMode}</td>
                    <td>{doc.chunkCount ?? 0}</td>
                    <td><span className={`status-pill doc-status-${docStatusClass(doc.status)}`}>{docStatusLabel(doc.status)}</span></td>
                    <td>
                      <button
                        className={`btn ${doc.enabled === 1 ? 'btn-light' : 'btn-danger'}`}
                        type="button"
                        onClick={() => handleToggleEnabled(doc)}
                      >
                        {doc.enabled === 1 ? '已启用' : '已禁用'}
                      </button>
                    </td>
                    <td>{formatDate(doc.createTime)}</td>
                    <td>
                      <div className="table-actions">
                        <button className="btn btn-light" type="button" onClick={() => setEditingDocument(doc)}>编辑</button>
                        <button className="btn btn-primary" type="button" onClick={() => setChunkingDocument(doc)}>分块处理</button>
                        {mode === 'admin' ? (
                          <Link className="btn btn-light" to={`/admin/knowledge-bases/${kbId}/documents/${doc.id}/chunks`}>查看分块结果</Link>
                        ) : (
                          <button className="btn btn-light" type="button" onClick={() => setChunkResultDocument(doc)}>查看分块结果</button>
                        )}
                        <button className="btn btn-danger" type="button" onClick={() => handleDelete(doc)}>删除</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="empty-state">
              <div className="empty-icon">文</div>
              <h3>暂无文档</h3>
              <p>点击右上角「新建文档」或「上传文件」添加文档。</p>
            </div>
          )}

          {total > 0 && (
            <div className="pagination-bar">
              <span>第 {pages ? safePageNo : 0} / {pages} 页</span>
              <label>
                每页
                <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPageNo(1); }}>
                  {pageSizeOptions.map((size) => <option key={size} value={size}>{size}</option>)}
                </select>
              </label>
              <button className="btn btn-light" type="button" disabled={loading || safePageNo <= 1} onClick={() => setPageNo((v) => Math.max(1, v - 1))}>上一页</button>
              <button className="btn btn-light" type="button" disabled={loading || safePageNo >= pages} onClick={() => setPageNo((v) => v + 1)}>下一页</button>
            </div>
          )}
        </article>

        {documentAction === 'blank' && kbId && (
          <BlankDocumentModal
            kbId={kbId}
            onClose={() => setDocumentAction(null)}
            onSuccess={() => handleActionSuccess('空白文档已创建')}
          />
        )}

        {uploadOpen && kbId && documentAction === 'upload' && (
          <DocumentUploadModal
            kbId={kbId}
            onClose={() => { setUploadOpen(false); setDocumentAction(null); }}
            onSuccess={() => { setUploadOpen(false); handleActionSuccess('文档上传成功'); }}
          />
        )}

        {editingDocument && kbId && (
          <DocumentEditModal
            kbId={kbId}
            doc={editingDocument}
            onClose={() => setEditingDocument(null)}
            onSuccess={() => handleActionSuccess('文档新版本已保存')}
          />
        )}

        {chunkingDocument && (
          <DocumentChunkModal
            doc={chunkingDocument}
            onClose={() => setChunkingDocument(null)}
            onSuccess={() => handleActionSuccess('已触发分块处理')}
          />
        )}

        {chunkResultDocument && (
          <ChunkResultModal
            doc={chunkResultDocument}
            onClose={() => setChunkResultDocument(null)}
          />
        )}
      </PageContainer>
    </AppShell>
  );
}

/**
 * 新建空白文档弹窗
 * 用户输入标题和正文内容，创建 Markdown 文件并上传到知识库
 */
function BlankDocumentModal({
  kbId,
  onClose,
  onSuccess,
}: {
  kbId: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [chunkForm, setChunkForm] = useState<ChunkFormState>(defaultChunkForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const canSubmit = Boolean(title.trim()) && Boolean(content.trim()) && !saving;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) {
      setError('请输入文档标题');
      return;
    }
    if (!content.trim()) {
      setError('请输入文档内容');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const fileName = ensureMarkdownFileName(title.trim());
      const file = new File([content], fileName, { type: 'text/markdown;charset=utf-8' });
      await knowledgeBaseApi.uploadDocument(kbId, {
        file,
        processMode: 'manual',
        chunkStrategy: chunkForm.strategy,
        chunkConfig: buildChunkConfig(chunkForm),
      });
      onSuccess();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="新建文档"
      onClose={() => { if (!saving) onClose(); }}
      footer={(
        <>
          <button className="btn btn-light" type="button" disabled={saving} onClick={onClose}>取消</button>
          <button className="btn btn-primary" type="submit" form="blank-document-form" disabled={!canSubmit}>
            {saving ? '创建中...' : '创建文档'}
          </button>
        </>
      )}
    >
      {error && <div className="error-banner modal-error">{error}</div>}
      <form id="blank-document-form" className="stack-form document-editor-form" onSubmit={handleSubmit}>
        <label>
          文档标题
          <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={120} placeholder="例如：接口联调记录" disabled={saving} />
        </label>
        <label>
          正文
          <textarea
            className="document-editor-textarea"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={12}
            placeholder="输入 Markdown 或纯文本内容"
            disabled={saving}
          />
        </label>
        <ChunkConfigPanel value={chunkForm} onChange={setChunkForm} disabled={saving} compact />
      </form>
    </Modal>
  );
}

/**
 * 文档编辑弹窗
 * 基于现有文档内容创建新版本，支持版本历史查看
 */
function DocumentEditModal({
  kbId,
  doc,
  onClose,
  onSuccess,
}: {
  kbId: string;
  doc: KnowledgeDocumentItem;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const existingVersions = useMemo(() => readLocalDocumentVersions(doc.id), [doc.id]);
  const [title, setTitle] = useState(stripKnownExtension(doc.docName));
  const [content, setContent] = useState(() => buildEditableDocumentTemplate(doc));
  const [note, setNote] = useState('');
  const [chunkForm, setChunkForm] = useState<ChunkFormState>(() => parseChunkForm(doc.chunkStrategy, doc.chunkConfig));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextVersion = `v${existingVersions.length + 2}`;
  const canSubmit = Boolean(title.trim()) && Boolean(content.trim()) && !saving;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim() || !content.trim()) {
      setError('文档标题和正文不能为空');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const fileName = ensureMarkdownFileName(`${title.trim()}-${nextVersion}`);
      const file = new File([content], fileName, { type: 'text/markdown;charset=utf-8' });
      const result = await knowledgeBaseApi.uploadDocument(kbId, {
        file,
        processMode: 'manual',
        chunkStrategy: chunkForm.strategy,
        chunkConfig: buildChunkConfig(chunkForm),
      });
      appendLocalDocumentVersion({
        id: `${doc.id}-${Date.now()}`,
        docId: doc.id,
        docName: result.docName,
        version: nextVersion,
        note: note.trim() || '保存为新版本',
        createdAt: new Date().toISOString(),
      });
      onSuccess();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="编辑文档"
      onClose={() => { if (!saving) onClose(); }}
      footer={(
        <>
          <button className="btn btn-light" type="button" disabled={saving} onClick={onClose}>取消</button>
          <button className="btn btn-primary" type="submit" form="document-edit-form" disabled={!canSubmit}>
            {saving ? '保存中...' : '保存为新版本'}
          </button>
        </>
      )}
    >
      {error && <div className="error-banner modal-error">{error}</div>}
      <form id="document-edit-form" className="stack-form document-editor-form" onSubmit={handleSubmit}>
        <section className="version-panel">
          <div>
            <span>当前文档</span>
            <strong>{doc.docName}</strong>
          </div>
          <div>
            <span>下一版本</span>
            <strong>{nextVersion}</strong>
          </div>
          <div>
            <span>分块数</span>
            <strong>{doc.chunkCount ?? 0}</strong>
          </div>
        </section>
        <label>
          新版本标题
          <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={120} disabled={saving} />
        </label>
        <label>
          正文
          <textarea
            className="document-editor-textarea"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={12}
            disabled={saving}
          />
        </label>
        <label>
          版本说明
          <input value={note} onChange={(event) => setNote(event.target.value)} maxLength={160} placeholder="例如：补充部署回滚步骤" disabled={saving} />
        </label>
        <ChunkConfigPanel value={chunkForm} onChange={setChunkForm} disabled={saving} compact />
        <div className="version-history">
          <strong>版本记录</strong>
          <div>
            <span>v1</span>
            <p>{doc.docName} · {formatDate(doc.createTime)}</p>
          </div>
          {existingVersions.map((item) => (
            <div key={item.id}>
              <span>{item.version}</span>
              <p>{item.docName} · {formatDate(item.createdAt)} · {item.note}</p>
            </div>
          ))}
        </div>
      </form>
    </Modal>
  );
}

/**
 * 文档分块处理弹窗
 * 配置分块策略参数并触发文档分块解析
 */
function DocumentChunkModal({
  doc,
  onClose,
  onSuccess,
}: {
  doc: KnowledgeDocumentItem;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [chunkForm, setChunkForm] = useState<ChunkFormState>(() => parseChunkForm(doc.chunkStrategy, doc.chunkConfig));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const configJson = buildChunkConfig(chunkForm);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await knowledgeBaseApi.triggerDocumentChunk(doc.id, {
        chunkStrategy: chunkForm.strategy,
        chunkConfig: configJson,
      });
      onSuccess();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="分块处理"
      onClose={() => { if (!submitting) onClose(); }}
      footer={(
        <>
          <button className="btn btn-light" type="button" disabled={submitting} onClick={onClose}>取消</button>
          <button className="btn btn-primary" type="submit" form="document-chunk-form" disabled={submitting}>
            {submitting ? '触发中...' : '确认分块处理'}
          </button>
        </>
      )}
    >
      {error && <div className="error-banner modal-error">{error}</div>}
      <form id="document-chunk-form" className="stack-form chunk-action-form" onSubmit={handleSubmit}>
        <section className="chunk-document-summary">
          <div>
            <span>文档</span>
            <strong>{doc.docName}</strong>
          </div>
          <div>
            <span>当前状态</span>
            <strong>{docStatusLabel(doc.status)}</strong>
          </div>
          <div>
            <span>已有分块</span>
            <strong>{doc.chunkCount ?? 0}</strong>
          </div>
        </section>
        <ChunkConfigPanel value={chunkForm} onChange={setChunkForm} disabled={submitting} />
        <div className="chunk-config-preview">
          <span>本次配置</span>
          <code>{configJson}</code>
        </div>
      </form>
    </Modal>
  );
}

/**
 * 分块结果查看弹窗
 * 分页展示文档的分块列表，包含分块内容预览和统计信息
 */
function ChunkResultModal({
  doc,
  onClose,
}: {
  doc: KnowledgeDocumentItem;
  onClose: () => void;
}) {
  const [records, setRecords] = useState<DocumentChunkItem[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    knowledgeBaseApi.getDocumentChunks(doc.id, pageNo, pageSize)
      .then((page) => {
        if (!active) return;
        setRecords(page.records || []);
        setTotal(page.total || 0);
        setPages(page.pages || 0);
      })
      .catch((err) => {
        if (!active) return;
        setRecords([]);
        setTotal(0);
        setPages(0);
        setError(getErrorMessage(err));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [doc.id, pageNo, pageSize]);

  const totalChars = records.reduce((sum, item) => sum + (item.charCount || 0), 0);
  const averageChars = records.length ? Math.round(totalChars / records.length) : 0;

  return (
    <Modal
      title="查看分块结果"
      onClose={onClose}
      footer={<button className="btn btn-primary" type="button" onClick={onClose}>关闭</button>}
    >
      {error && <div className="error-banner modal-error">{error}</div>}
      <section className="chunk-result-header">
        <div>
          <span>文档</span>
          <strong>{doc.docName}</strong>
        </div>
        <div>
          <span>总分块</span>
          <strong>{total}</strong>
        </div>
        <div>
          <span>本页字符</span>
          <strong>{totalChars}</strong>
        </div>
        <div>
          <span>平均大小</span>
          <strong>{averageChars || '--'}</strong>
        </div>
      </section>

      {loading ? (
        <div className="loading-state">正在加载分块结果...</div>
      ) : records.length ? (
        <>
          <div className="chunk-flow" aria-label="分块顺序">
            {records.slice(0, 12).map((item) => (
              <span key={item.chunkId}>#{item.index + 1}</span>
            ))}
          </div>
          <div className="chunk-list">
            {records.map((item) => (
              <article className="chunk-card" key={item.chunkId}>
                <header>
                  <strong>Chunk #{item.index + 1}</strong>
                  <span>{item.charCount} 字符</span>
                </header>
                <p>{item.content || '暂无内容预览'}</p>
                <footer>
                  <code>{item.chunkId}</code>
                  <span>{item.index === 0 ? '起始块' : `承接 #${item.index}`}</span>
                </footer>
              </article>
            ))}
          </div>
          <div className="pagination-bar">
            <span>第 {pages ? pageNo : 0} / {pages} 页</span>
            <label>
              每页
              <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPageNo(1); }}>
                {[10, 20, 50, 100].map((size) => <option key={size} value={size}>{size}</option>)}
              </select>
            </label>
            <button className="btn btn-light" type="button" disabled={loading || pageNo <= 1} onClick={() => setPageNo((value) => Math.max(1, value - 1))}>上一页</button>
            <button className="btn btn-light" type="button" disabled={loading || pageNo >= pages || pages === 0} onClick={() => setPageNo((value) => value + 1)}>下一页</button>
          </div>
        </>
      ) : (
        <div className="empty-state compact-empty">
          <div className="empty-icon">块</div>
          <h3>暂无分块结果</h3>
          <p>请先在文档列表中触发「分块处理」。</p>
        </div>
      )}
    </Modal>
  );
}

/**
 * 分块配置面板组件
 * 提供分块策略选择、分块大小、重叠度等参数的可视化配置
 * @param value - 当前配置值
 * @param onChange - 配置变更回调
 * @param disabled - 是否禁用
 * @param compact - 是否使用紧凑模式（隐藏滑块）
 */
function ChunkConfigPanel({
  value,
  onChange,
  disabled = false,
  compact = false,
}: {
  value: ChunkFormState;
  onChange: (value: ChunkFormState) => void;
  disabled?: boolean;
  compact?: boolean;
}) {
  const strategy = chunkStrategyOptions.find((item) => item.value === value.strategy) || chunkStrategyOptions[0];
  const usesBoundaryConfig = value.strategy === 'structure_aware' || value.strategy === 'table_aware';
  const sizeLabel = usesBoundaryConfig ? '目标大小' : '分块大小';
  const overlapLabel = usesBoundaryConfig ? '重叠字符' : '重叠度';

  function update(partial: Partial<ChunkFormState>) {
    onChange({ ...value, ...partial });
  }

  function updateNumber(key: keyof ChunkFormState, nextValue: string) {
    const parsed = Number(nextValue);
    update({ [key]: Number.isFinite(parsed) ? parsed : 0 } as Partial<ChunkFormState>);
  }

  return (
    <section className={compact ? 'chunk-config-panel compact' : 'chunk-config-panel'}>
      <div className="chunk-config-title">
        <div>
          <strong>分块参数</strong>
          <span>{strategy.hint}</span>
        </div>
        <span>{strategy.label}</span>
      </div>
      <label>
        分块策略
        <select value={value.strategy} onChange={(event) => update({ strategy: event.target.value as ChunkStrategyMode })} disabled={disabled}>
          {chunkStrategyOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select>
      </label>
      <div className="chunk-control-grid">
        <label>
          {sizeLabel}
          <input
            type="number"
            min={128}
            max={4000}
            step={64}
            value={value.chunkSize}
            onChange={(event) => updateNumber('chunkSize', event.target.value)}
            disabled={disabled}
          />
        </label>
        <label>
          {overlapLabel}
          <input
            type="number"
            min={0}
            max={Math.max(0, value.chunkSize - 1)}
            step={16}
            value={value.overlapSize}
            onChange={(event) => updateNumber('overlapSize', event.target.value)}
            disabled={disabled}
          />
        </label>
      </div>
      {!compact && (
        <div className="chunk-range-row">
          <label>
            {sizeLabel}
            <input
              type="range"
              min={128}
              max={4000}
              step={64}
              value={value.chunkSize}
              onChange={(event) => updateNumber('chunkSize', event.target.value)}
              disabled={disabled}
            />
          </label>
          <label>
            {overlapLabel}
            <input
              type="range"
              min={0}
              max={Math.max(0, value.chunkSize - 1)}
              step={16}
              value={Math.min(value.overlapSize, Math.max(0, value.chunkSize - 1))}
              onChange={(event) => updateNumber('overlapSize', event.target.value)}
              disabled={disabled}
            />
          </label>
        </div>
      )}
      {usesBoundaryConfig && (
        <div className="chunk-control-grid">
          <label>
            最小字符
            <input
              type="number"
              min={0}
              max={value.maxChars}
              step={50}
              value={value.minChars}
              onChange={(event) => updateNumber('minChars', event.target.value)}
              disabled={disabled}
            />
          </label>
          <label>
            最大字符
            <input
              type="number"
              min={value.chunkSize}
              max={8000}
              step={50}
              value={value.maxChars}
              onChange={(event) => updateNumber('maxChars', event.target.value)}
              disabled={disabled}
            />
          </label>
        </div>
      )}
    </section>
  );
}

/**
 * 定时同步配置弹窗
 * 配置在线文档（飞书/URL）的定时同步计划
 */
function ScheduleConfigModal({
  kbId,
  doc,
  onClose,
  onSuccess,
}: {
  kbId: string;
  doc: KnowledgeDocumentItem;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const initialSchedule = parseScheduleCron(doc.scheduleCron);
  const [sourceType, setSourceType] = useState(doc.sourceType || 'file');
  const [sourceLocation, setSourceLocation] = useState(doc.sourceLocation || '');
  const [syncFrequency, setSyncFrequency] = useState<SyncFrequency>((doc.scheduleEnabled ?? 0) === 1 ? initialSchedule.frequency : 'none');
  const [syncTime, setSyncTime] = useState(initialSchedule.time);
  const [syncWeekday, setSyncWeekday] = useState(initialSchedule.weekday);
  const [syncMonthDay, setSyncMonthDay] = useState(initialSchedule.monthDay);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    setSaving(true);
    setError(null);
    try {
      const scheduleCron = buildScheduleCron(syncFrequency, syncTime, syncWeekday, syncMonthDay);
      await syncApi.configureSchedule(kbId, doc.id, {
        sourceType,
        sourceLocation: sourceType === 'feishu' ? normalizeFeishuSourceLocation(sourceLocation) : sourceLocation,
        scheduleEnabled: syncFrequency === 'none' ? 0 : 1,
        scheduleCron,
      });
      onSuccess();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={e => e.stopPropagation()}>
        <div className="modal-header"><h3>定时同步配置</h3></div>
        <div className="modal-body">
          {error && <div className="error-banner modal-error">{error}</div>}
          <div className="stack-form">
            <label>
              来源类型
              <select value={sourceType} onChange={e => setSourceType(e.target.value)}>
                <option value="file">本地文件</option>
                <option value="feishu">飞书文档</option>
                <option value="url">网页 URL</option>
              </select>
            </label>
            {sourceType !== 'file' && (
              <label>
                来源地址
                <input
                  value={sourceLocation}
                  onChange={e => setSourceLocation(e.target.value)}
                  placeholder={sourceType === 'feishu' ? 'docx:xxxxxx 或 wiki:xxxxxx' : 'https://example.com/article'}
                />
              </label>
            )}
            <label>
              启用定时同步
              <select value={syncFrequency} onChange={e => setSyncFrequency(e.target.value as SyncFrequency)}>
                <option value="none">不启用</option>
                <option value="daily">每天</option>
                <option value="weekly">每周</option>
                <option value="monthly">每月</option>
              </select>
            </label>
            {syncFrequency !== 'none' && (
              <label>
                同步时间
                <input
                  type="time"
                  value={syncTime}
                  onChange={e => setSyncTime(e.target.value)}
                />
              </label>
            )}
            {syncFrequency === 'weekly' && (
              <label>
                每周几同步
                <select value={syncWeekday} onChange={e => setSyncWeekday(e.target.value)}>
                  <option value="1">周一</option>
                  <option value="2">周二</option>
                  <option value="3">周三</option>
                  <option value="4">周四</option>
                  <option value="5">周五</option>
                  <option value="6">周六</option>
                  <option value="7">周日</option>
                </select>
              </label>
            )}
            {syncFrequency === 'monthly' && (
              <label>
                每月几号同步
                <select value={syncMonthDay} onChange={e => setSyncMonthDay(e.target.value)}>
                  {Array.from({ length: 28 }, (_, index) => String(index + 1)).map((day) => (
                    <option key={day} value={day}>{day} 号</option>
                  ))}
                </select>
              </label>
            )}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-light" onClick={onClose} disabled={saving}>取消</button>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 文档上传弹窗组件
 * 支持本地文件上传、飞书文档导入、URL 导入三种模式
 * 包含分块配置、定时同步、流水线配置等高级选项
 */
function DocumentUploadModal({
  kbId,
  knowledgeBases = [],
  onClose,
  onSuccess,
}: {
  kbId?: string;
  knowledgeBases?: KnowledgeBaseItem[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [sourceMode, setSourceMode] = useState<DocumentSourceMode>('file');
  const [file, setFile] = useState<File | null>(null);
  const [targetKbId, setTargetKbId] = useState(kbId || knowledgeBases[0]?.id || '');
  const [sourceLocation, setSourceLocation] = useState('');
  const [docName, setDocName] = useState('');
  const [syncFrequency, setSyncFrequency] = useState<SyncFrequency>('none');
  const [syncTime, setSyncTime] = useState('02:00');
  const [syncWeekday, setSyncWeekday] = useState('1');
  const [syncMonthDay, setSyncMonthDay] = useState('1');
  const [chunkForm, setChunkForm] = useState<ChunkFormState>(defaultChunkForm);
  const [pipelineId, setPipelineId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const isFileMode = sourceMode === 'file';
  const canSubmit = Boolean(kbId || targetKbId)
    && (isFileMode ? Boolean(file) : Boolean(sourceLocation.trim()))
    && !submitting;

  useEffect(() => {
    if (!kbId && !targetKbId && knowledgeBases[0]) {
      setTargetKbId(knowledgeBases[0].id);
    }
  }, [kbId, knowledgeBases, targetKbId]);

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null;
    setFile(selected);
    if (selected && !docName.trim()) {
      setDocName(selected.name);
    }
    setError(null);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const actualKbId = kbId || targetKbId;
    if (!actualKbId) {
      setError('请选择目标知识库');
      return;
    }
    if (isFileMode && !file) {
      setError('请选择要上传的文件');
      return;
    }
    if (!isFileMode && !sourceLocation.trim()) {
      setError(sourceMode === 'feishu' ? '请输入飞书文档地址' : '请输入网页 URL');
      return;
    }
    setSubmitting(true);
    setError(null);
    setProgress(0);
    try {
      const normalizedSourceLocation = sourceMode === 'feishu'
        ? normalizeFeishuSourceLocation(sourceLocation)
        : sourceLocation.trim();
      const scheduleCron = buildScheduleCron(syncFrequency, syncTime, syncWeekday, syncMonthDay);
      if (isFileMode) {
        await knowledgeBaseApi.uploadDocument(actualKbId, {
          file: file!,
          processMode: 'manual',
          chunkStrategy: chunkForm.strategy,
          chunkConfig: buildChunkConfig(chunkForm),
          pipelineId: pipelineId || undefined,
        }, (event: AxiosProgressEvent) => {
          if (event.total) setProgress(Math.round((event.loaded / event.total) * 100));
        });
      } else {
        await knowledgeBaseApi.importOnlineDocument(actualKbId, {
          sourceType: sourceMode,
          sourceLocation: normalizedSourceLocation,
          docName: docName.trim() || undefined,
          processMode: 'manual',
          chunkStrategy: chunkForm.strategy,
          chunkConfig: buildChunkConfig(chunkForm),
          pipelineId: pipelineId || undefined,
          scheduleEnabled: syncFrequency === 'none' ? 0 : 1,
          scheduleCron,
        });
        setProgress(100);
      }
      onSuccess();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  function formatSize(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  return (
    <Modal
      title="上传文档"
      onClose={() => { if (!submitting) onClose(); }}
      footer={
        <>
          <button className="btn btn-light" type="button" disabled={submitting} onClick={onClose}>取消</button>
          <button className="btn btn-primary" type="submit" form="document-upload-form" disabled={!canSubmit}>
            {submitting ? (isFileMode ? `上传中 ${progress}%` : '导入中...') : (isFileMode ? '开始上传' : '开始导入')}
          </button>
        </>
      }
    >
      {error && <div className="error-banner modal-error">{error}</div>}
      <form id="document-upload-form" className="stack-form" onSubmit={handleSubmit}>
        <div className="source-mode-tabs" role="tablist" aria-label="文档来源">
          <button
            className={sourceMode === 'file' ? 'active' : ''}
            type="button"
            role="tab"
            aria-selected={sourceMode === 'file'}
            onClick={() => setSourceMode('file')}
            disabled={submitting}
          >
            <UiIcon name="filePlus" />
            本地文件
          </button>
          <button
            className={sourceMode === 'feishu' ? 'active' : ''}
            type="button"
            role="tab"
            aria-selected={sourceMode === 'feishu'}
            onClick={() => setSourceMode('feishu')}
            disabled={submitting}
          >
            <UiIcon name="fileText" />
            飞书文档
          </button>
          <button
            className={sourceMode === 'url' ? 'active' : ''}
            type="button"
            role="tab"
            aria-selected={sourceMode === 'url'}
            onClick={() => setSourceMode('url')}
            disabled={submitting}
          >
            <UiIcon name="fileSearch" />
            网页 URL
          </button>
        </div>

        {!kbId && (
          <label>
            目标知识库
            <select value={targetKbId} onChange={(e) => setTargetKbId(e.target.value)} disabled={submitting}>
              <option value="">请选择知识库</option>
              {knowledgeBases.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
        )}

        {isFileMode ? (
          <>
            <label>
              选择文件
              <div className="file-picker">
                <input ref={fileInputRef} type="file" onChange={handleFileChange} style={{ display: 'none' }} />
                <button className="btn btn-light" type="button" onClick={() => fileInputRef.current?.click()} disabled={submitting}>
                  {file ? '重新选择' : '浏览文件'}
                </button>
                <span className="file-picker-name">{file ? file.name : '未选择文件'}</span>
              </div>
            </label>

            {file && (
              <div className="file-info-grid">
                <span>文件名</span><strong>{file.name}</strong>
                <span>文件大小</span><strong>{formatSize(file.size)}</strong>
                <span>文件类型</span><strong>{file.type || '未知'}</strong>
              </div>
            )}
          </>
        ) : (
          <div className="online-source-panel">
            <label>
              {sourceMode === 'feishu' ? '飞书文档地址' : '网页 URL'}
              <input
                value={sourceLocation}
                onChange={(e) => setSourceLocation(e.target.value)}
                disabled={submitting}
                placeholder={sourceMode === 'feishu' ? 'docx:xxxxxx、wiki:xxxxxx 或 sheet:xxxxxx' : 'https://example.com/article'}
              />
            </label>
            <label>
              文档名称
              <input
                value={docName}
                onChange={(e) => setDocName(e.target.value)}
                disabled={submitting}
                placeholder={sourceMode === 'feishu' ? '留空时使用飞书标题' : '留空时使用网页标题'}
              />
            </label>
            <div className="inline-switch-row">
              <label>
                定时同步
                <select value={syncFrequency} onChange={(e) => setSyncFrequency(e.target.value as SyncFrequency)} disabled={submitting}>
                  <option value="none">不启用</option>
                  <option value="daily">每天</option>
                  <option value="weekly">每周</option>
                  <option value="monthly">每月</option>
                </select>
              </label>
              {syncFrequency !== 'none' && (
                <label>
                  同步时间
                  <input
                    type="time"
                    value={syncTime}
                    onChange={(e) => setSyncTime(e.target.value)}
                    disabled={submitting}
                  />
                </label>
              )}
            </div>
            {syncFrequency === 'weekly' && (
              <label>
                每周几同步
                <select value={syncWeekday} onChange={(e) => setSyncWeekday(e.target.value)} disabled={submitting}>
                  <option value="1">周一</option>
                  <option value="2">周二</option>
                  <option value="3">周三</option>
                  <option value="4">周四</option>
                  <option value="5">周五</option>
                  <option value="6">周六</option>
                  <option value="7">周日</option>
                </select>
              </label>
            )}
            {syncFrequency === 'monthly' && (
              <label>
                每月几号同步
                <select value={syncMonthDay} onChange={(e) => setSyncMonthDay(e.target.value)} disabled={submitting}>
                  {Array.from({ length: 28 }, (_, index) => String(index + 1)).map((day) => (
                    <option key={day} value={day}>{day} 号</option>
                  ))}
                </select>
              </label>
            )}
          </div>
        )}

        {submitting && (
          <div className="upload-progress">
            <div className="progress-track">
              <div className="progress-fill" style={{ width: `${isFileMode ? progress : 70}%` }} />
            </div>
            <small>{isFileMode ? `${progress}%` : '正在抓取来源内容'}</small>
          </div>
        )}

        <ChunkConfigPanel value={chunkForm} onChange={setChunkForm} disabled={submitting} />

        <details className="advanced-section">
          <summary>流水线配置</summary>
          <label>
            Pipeline ID
            <input
              value={pipelineId}
              onChange={(e) => setPipelineId(e.target.value)}
              disabled={submitting}
              placeholder="可选，留空则使用默认处理链路"
            />
          </label>
        </details>
      </form>
    </Modal>
  );
}

/**
 * 格式化文件大小
 * @param bytes - 字节数
 * @returns 格式化后的字符串（B/KB/MB）
 */
function formatFileSize(bytes?: number | null) {
  if (!bytes && bytes !== 0) return '--';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * 构建分块配置 JSON 字符串
 * 根据表单状态生成后端所需的分块配置参数
 * @param form - 分块配置表单状态
 * @returns JSON 格式的配置字符串
 */
function buildChunkConfig(form: ChunkFormState) {
  const chunkSize = Math.max(128, Math.round(form.chunkSize || defaultChunkForm.chunkSize));
  const overlapSize = Math.min(Math.max(0, Math.round(form.overlapSize || 0)), Math.max(0, chunkSize - 1));
  if (form.strategy === 'structure_aware' || form.strategy === 'table_aware') {
    const minChars = Math.max(0, Math.round(form.minChars || 0));
    const maxChars = Math.max(chunkSize, Math.round(form.maxChars || chunkSize));
    return JSON.stringify({
      targetChars: chunkSize,
      overlapChars: overlapSize,
      maxChars,
      minChars: Math.min(minChars, maxChars),
    });
  }
  return JSON.stringify({ chunkSize, overlapSize });
}

/**
 * 解析分块配置
 * 从后端返回的策略和配置字符串解析为表单状态
 * @param strategy - 分块策略名称
 * @param config - JSON 格式的配置字符串
 * @returns 分块配置表单状态
 */
function parseChunkForm(strategy?: string | null, config?: string | null): ChunkFormState {
  const normalizedStrategy = normalizeChunkStrategy(strategy);
  const next = { ...defaultChunkForm, strategy: normalizedStrategy };
  if (!config) return next;
  try {
    const parsed = JSON.parse(config) as Record<string, unknown>;
    const chunkSize = readConfigNumber(parsed.chunkSize, parsed.targetChars, next.chunkSize);
    const overlapSize = readConfigNumber(parsed.overlapSize, parsed.overlapChars, next.overlapSize);
    return {
      ...next,
      chunkSize,
      overlapSize,
      minChars: readConfigNumber(parsed.minChars, undefined, next.minChars),
      maxChars: readConfigNumber(parsed.maxChars, undefined, Math.max(next.maxChars, chunkSize)),
    };
  } catch {
    return next;
  }
}

/**
 * 标准化分块策略名称
 * 处理不同的命名格式（如 recursive -> recursive_character）
 * @param value - 原始策略名称
 * @returns 标准化后的策略枚举值
 */
function normalizeChunkStrategy(value?: string | null): ChunkStrategyMode {
  const normalized = (value || '').trim().toLowerCase().replace(/-/g, '_');
  if (normalized === 'recursive') return 'recursive_character';
  if (normalized === 'fixed') return 'fixed_size';
  if (chunkStrategyOptions.some((item) => item.value === normalized)) {
    return normalized as ChunkStrategyMode;
  }
  return defaultChunkForm.strategy;
}

function readConfigNumber(primary: unknown, fallback: unknown, defaultValue: number) {
  const value = typeof primary === 'number' || typeof primary === 'string' ? primary : fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : defaultValue;
}

function ensureMarkdownFileName(value: string) {
  const cleaned = value.replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, ' ').trim() || '未命名文档';
  return /\.(md|markdown|txt)$/i.test(cleaned) ? cleaned : `${cleaned}.md`;
}

function stripKnownExtension(value: string) {
  return value.replace(/\.(md|markdown|txt|pdf|docx?|xlsx?|pptx?)$/i, '');
}

function buildEditableDocumentTemplate(doc: KnowledgeDocumentItem) {
  return [
    `# ${stripKnownExtension(doc.docName)}`,
    '',
    `> 来源：${doc.sourceType || 'file'} · 创建时间：${formatDate(doc.createTime)}`,
    '',
    '请在这里整理本版本正文内容。',
  ].join('\n');
}

function readLocalDocumentVersions(docId: string): LocalDocumentVersion[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(documentVersionStoreKey);
    const parsed = raw ? JSON.parse(raw) as LocalDocumentVersion[] : [];
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item) => item.docId === docId);
  } catch {
    return [];
  }
}

function appendLocalDocumentVersion(version: LocalDocumentVersion) {
  if (typeof window === 'undefined') return;
  try {
    const raw = window.localStorage.getItem(documentVersionStoreKey);
    const parsed = raw ? JSON.parse(raw) as LocalDocumentVersion[] : [];
    const next = Array.isArray(parsed) ? parsed.concat(version).slice(-100) : [version];
    window.localStorage.setItem(documentVersionStoreKey, JSON.stringify(next));
  } catch {
    // Local version history is a convenience cache; upload success is the source of truth.
  }
}

/**
 * 构建定时同步 Cron 表达式
 * 根据频率、时间、星期几、月几号生成 Quartz 格式的 Cron 表达式
 * @param frequency - 同步频率
 * @param time - 同步时间（HH:mm 格式）
 * @param weekday - 星期几（1-7）
 * @param monthDay - 每月几号（1-28）
 * @returns Cron 表达式或 undefined（不启用时）
 */
function buildScheduleCron(frequency: SyncFrequency, time: string, weekday: string, monthDay: string) {
  if (frequency === 'none') return undefined;
  const [hour = '2', minute = '0'] = time.split(':');
  const safeHour = Math.min(Math.max(Number(hour) || 0, 0), 23);
  const safeMinute = Math.min(Math.max(Number(minute) || 0, 0), 59);
  if (frequency === 'weekly') {
    const safeWeekday = Math.min(Math.max(Number(weekday) || 1, 1), 7);
    return `0 ${safeMinute} ${safeHour} ? * ${safeWeekday}`;
  }
  if (frequency === 'monthly') {
    const safeMonthDay = Math.min(Math.max(Number(monthDay) || 1, 1), 28);
    return `0 ${safeMinute} ${safeHour} ${safeMonthDay} * ?`;
  }
  return `0 ${safeMinute} ${safeHour} * * ?`;
}

/**
 * 解析 Cron 表达式为定时配置
 * @param value - Cron 表达式
 * @returns 解析后的频率、时间、星期几、月几号
 */
function parseScheduleCron(value?: string | null): { frequency: SyncFrequency; time: string; weekday: string; monthDay: string } {
  if (!value) {
    return { frequency: 'daily', time: '02:00', weekday: '1', monthDay: '1' };
  }
  const parts = value.trim().split(/\s+/);
  if (parts.length < 6) {
    return { frequency: 'daily', time: '02:00', weekday: '1', monthDay: '1' };
  }
  const minute = parts[1]?.padStart(2, '0') || '00';
  const hour = parts[2]?.padStart(2, '0') || '02';
  const time = `${hour}:${minute}`;
  if (parts[3] === '?' && parts[5] !== '?') {
    return { frequency: 'weekly', time, weekday: parts[5], monthDay: '1' };
  }
  if (parts[3] !== '*' && parts[3] !== '?' && parts[5] === '?') {
    return { frequency: 'monthly', time, weekday: '1', monthDay: parts[3] };
  }
  return { frequency: 'daily', time, weekday: '1', monthDay: '1' };
}

/**
 * 格式化 Cron 表达式为可读文本
 * @param value - Cron 表达式
 * @returns 可读的定时描述文本
 */
function formatScheduleCron(value?: string | null) {
  if (!value) return '未启用';
  const parsed = parseScheduleCron(value);
  if (parsed.frequency === 'weekly') {
    const weekdayLabels: Record<string, string> = {
      '1': '周一',
      '2': '周二',
      '3': '周三',
      '4': '周四',
      '5': '周五',
      '6': '周六',
      '7': '周日',
    };
    return `每${weekdayLabels[parsed.weekday] || '周'} ${parsed.time}`;
  }
  if (parsed.frequency === 'monthly') {
    return `每月 ${parsed.monthDay} 号 ${parsed.time}`;
  }
  return `每天 ${parsed.time}`;
}

/**
 * 标准化飞书文档来源地址
 * 将飞书 URL 转换为标准格式（docx:xxx、wiki:xxx、sheet:xxx）
 * @param value - 飞书文档 URL 或标识
 * @returns 标准化的来源地址
 */
function normalizeFeishuSourceLocation(value: string) {
  const input = value.trim();
  if (/^(docx|wiki|sheet):/i.test(input)) {
    return input.replace(/^(docx|wiki|sheet):/i, (prefix) => prefix.toLowerCase());
  }
  try {
    const url = new URL(input);
    const segments = url.pathname.split('/').filter(Boolean);
    const wikiIndex = segments.findIndex((segment) => segment === 'wiki');
    if (wikiIndex >= 0 && segments[wikiIndex + 1]) {
      return `wiki:${segments[wikiIndex + 1]}`;
    }
    const docxIndex = segments.findIndex((segment) => segment === 'docx');
    if (docxIndex >= 0 && segments[docxIndex + 1]) {
      return `docx:${segments[docxIndex + 1]}`;
    }
    const sheetsIndex = segments.findIndex((segment) => segment === 'sheets' || segment === 'sheet');
    if (sheetsIndex >= 0 && segments[sheetsIndex + 1]) {
      return `sheet:${segments[sheetsIndex + 1]}`;
    }
  } catch {
    return input;
  }
  return input;
}

/**
 * 管理后台 - 文档分块详情页面
 * 展示文档的所有分块，支持分块内容编辑和保存
 */
function AdminDocumentChunksPage() {
  const { id: kbId, documentId } = useParams();
  const [knowledgeBase, setKnowledgeBase] = useState<KnowledgeBaseItem | null>(null);
  const [document, setDocument] = useState<KnowledgeDocumentItem | null>(null);
  const [records, setRecords] = useState<KnowledgeChunkItem[]>([]);
  const [selectedChunk, setSelectedChunk] = useState<KnowledgeChunkItem | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [loadingChunks, setLoadingChunks] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [draftContent, setDraftContent] = useState('');
  const [saving, setSaving] = useState(false);

  const isDirty = editing && selectedChunk != null && draftContent !== (selectedChunk.content || '');
  const totalChars = records.reduce((sum, item) => sum + (item.charCount || 0), 0);
  const averageChars = records.length ? Math.round(totalChars / records.length) : 0;

  useEffect(() => {
    if (!kbId || !documentId) return;
    let active = true;
    setLoadingMeta(true);
    setError(null);
    Promise.all([
      knowledgeBaseApi.getKnowledgeBase(kbId),
      knowledgeBaseApi.getKnowledgeDocuments(kbId),
    ])
      .then(([kb, documents]) => {
        if (!active) return;
        setKnowledgeBase(kb);
        const found = (documents || []).find((item) => item.id === documentId) || null;
        setDocument(found);
        if (!found) {
          setError('文档不存在或不属于当前知识库');
        }
      })
      .catch((err) => {
        if (active) setError(getErrorMessage(err));
      })
      .finally(() => {
        if (active) setLoadingMeta(false);
      });
    return () => { active = false; };
  }, [kbId, documentId]);

  useEffect(() => {
    if (!documentId) return;
    let active = true;
    setLoadingChunks(true);
    setError(null);
    knowledgeBaseApi.getKnowledgeDocumentChunks(documentId, pageNo, pageSize)
      .then((page) => {
        if (!active) return;
        const nextRecords = page.records || [];
        setRecords(nextRecords);
        setTotal(page.total || 0);
        setPages(page.pages || 0);
        const stillSelected = nextRecords.find((item) => item.id === selectedChunk?.id) || null;
        const nextSelected = stillSelected || nextRecords[0] || null;
        setSelectedChunk(nextSelected);
        setDraftContent(nextSelected?.content || '');
        setEditing(false);
      })
      .catch((err) => {
        if (!active) return;
        setRecords([]);
        setTotal(0);
        setPages(0);
        setSelectedChunk(null);
        setDraftContent('');
        setError(getErrorMessage(err));
      })
      .finally(() => {
        if (active) setLoadingChunks(false);
      });
    return () => { active = false; };
  }, [documentId, pageNo, pageSize]);

  function confirmDiscard() {
    return !isDirty || window.confirm('当前分块有未保存修改，确认放弃并继续吗？');
  }

  function selectChunk(chunk: KnowledgeChunkItem) {
    if (selectedChunk?.id === chunk.id) return;
    if (!confirmDiscard()) return;
    setSelectedChunk(chunk);
    setDraftContent(chunk.content || '');
    setEditing(false);
    setMessage(null);
  }

  function changePage(nextPageNo: number) {
    if (!confirmDiscard()) return;
    setMessage(null);
    setPageNo(Math.min(Math.max(1, nextPageNo), Math.max(1, pages)));
  }

  function changePageSize(nextPageSize: number) {
    if (!confirmDiscard()) return;
    setMessage(null);
    setPageSize(nextPageSize);
    setPageNo(1);
  }

  function startEdit() {
    if (!selectedChunk) return;
    setDraftContent(selectedChunk.content || '');
    setEditing(true);
    setMessage(null);
    setError(null);
  }

  function cancelEdit() {
    if (!confirmDiscard()) return;
    setDraftContent(selectedChunk?.content || '');
    setEditing(false);
  }

  async function saveChunk() {
    if (!selectedChunk || !documentId) return;
    if (!draftContent.trim()) {
      setError('分块内容不能为空');
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await knowledgeBaseApi.updateKnowledgeDocumentChunk(documentId, selectedChunk.id, draftContent);
      setRecords((items) => items.map((item) => (item.id === updated.id ? updated : item)));
      setSelectedChunk(updated);
      setDraftContent(updated.content || '');
      setEditing(false);
      setMessage('保存成功');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell mode="admin">
      <PageContainer
        title="分块结果"
        description={document ? `文档：${document.docName}` : '查看并编辑文档分块内容'}
        actions={(
          <div className="page-actions">
            <Link className="btn btn-light" to={`/admin/knowledge-bases/${kbId || ''}/documents`}>返回文档列表</Link>
          </div>
        )}
      >
        <section className="chunk-workbench-summary">
          <div>
            <span>知识库</span>
            <strong>{knowledgeBase?.name || kbId || '--'}</strong>
          </div>
          <div>
            <span>文档</span>
            <strong>{document?.docName || documentId || '--'}</strong>
          </div>
          <div>
            <span>总分块</span>
            <strong>{total || document?.chunkCount || 0}</strong>
          </div>
          <div>
            <span>本页平均</span>
            <strong>{averageChars ? `${averageChars} 字符` : '--'}</strong>
          </div>
        </section>

        {error && <div className="error-banner">{error}</div>}
        {message && <p className="toast-line">{message}</p>}

        <section className="chunk-workbench card">
          <aside className="chunk-index-panel">
            <div className="chunk-panel-title">
              <div>
                <h3>分块列表</h3>
                <p>{loadingChunks ? '正在加载...' : `${total} 条分块`}</p>
              </div>
              <label>
                每页
                <select value={pageSize} onChange={(event) => changePageSize(Number(event.target.value))} disabled={loadingChunks || saving}>
                  {[10, 20, 50, 100].map((size) => <option key={size} value={size}>{size}</option>)}
                </select>
              </label>
            </div>

            {loadingMeta || loadingChunks ? (
              <div className="loading-state compact-empty">正在加载分块...</div>
            ) : records.length ? (
              <div className="chunk-index-list">
                {records.map((chunk) => (
                  <button
                    key={chunk.id}
                    className={selectedChunk?.id === chunk.id ? 'chunk-index-item active' : 'chunk-index-item'}
                    type="button"
                    onClick={() => selectChunk(chunk)}
                  >
                    <span>
                      <strong>Chunk #{(chunk.chunkIndex ?? 0) + 1}</strong>
                      <em>{chunk.enabled === 0 ? '已禁用' : '已启用'}</em>
                    </span>
                    <p>{chunk.content || '暂无内容'}</p>
                    <small>{chunk.charCount ?? 0} 字符 · {formatDate(chunk.updateTime)}</small>
                  </button>
                ))}
              </div>
            ) : (
              <div className="empty-state compact-empty">
                <div className="empty-icon">块</div>
                <h3>暂无分块结果</h3>
                <p>请先在文档列表中触发「分块处理」。</p>
              </div>
            )}

            {total > 0 && (
              <div className="pagination-bar chunk-pagination">
                <span>第 {pages ? pageNo : 0} / {pages} 页</span>
                <button className="btn btn-light" type="button" disabled={loadingChunks || pageNo <= 1} onClick={() => changePage(pageNo - 1)}>上一页</button>
                <button className="btn btn-light" type="button" disabled={loadingChunks || pageNo >= pages || pages === 0} onClick={() => changePage(pageNo + 1)}>下一页</button>
              </div>
            )}
          </aside>

          <article className="chunk-detail-panel">
            {selectedChunk ? (
              <>
                <header className="chunk-detail-header">
                  <div>
                    <span>当前分块</span>
                    <h3>Chunk #{(selectedChunk.chunkIndex ?? 0) + 1}</h3>
                    <p>{selectedChunk.id}</p>
                  </div>
                  <div className="chunk-detail-actions">
                    {editing && isDirty && <span className="edit-state">未保存</span>}
                    {editing ? (
                      <>
                        <button className="btn btn-light" type="button" onClick={cancelEdit} disabled={saving}>取消</button>
                        <button className="btn btn-primary" type="button" onClick={saveChunk} disabled={saving || !isDirty}>
                          {saving ? '保存中...' : '保存修改'}
                        </button>
                      </>
                    ) : (
                      <button className="btn btn-primary" type="button" onClick={startEdit}>编辑内容</button>
                    )}
                  </div>
                </header>

                <div className="chunk-meta-grid">
                  <div><span>字符数</span><strong>{selectedChunk.charCount ?? 0}</strong></div>
                  <div><span>Token 数</span><strong>{selectedChunk.tokenCount ?? '--'}</strong></div>
                  <div><span>状态</span><strong>{selectedChunk.enabled === 0 ? '已禁用' : '已启用'}</strong></div>
                  <div><span>更新时间</span><strong>{formatDate(selectedChunk.updateTime)}</strong></div>
                </div>

                <div className="chunk-hash-line">
                  <span>内容 Hash</span>
                  <code>{selectedChunk.contentHash || '--'}</code>
                </div>

                {editing ? (
                  <label className="chunk-editor">
                    分块内容
                    <textarea
                      value={draftContent}
                      onChange={(event) => setDraftContent(event.target.value)}
                      disabled={saving}
                      spellCheck={false}
                    />
                  </label>
                ) : (
                  <section className="chunk-full-content">
                    <pre>{selectedChunk.content || '暂无内容'}</pre>
                  </section>
                )}
              </>
            ) : (
              <div className="empty-state compact-empty">
                <div className="empty-icon">读</div>
                <h3>请选择分块</h3>
                <p>从左侧列表点击任意分块查看完整内容。</p>
              </div>
            )}
          </article>
        </section>
      </PageContainer>
    </AppShell>
  );
}

/**
 * 前台文档详情页面
 * 展示单个文档的详细信息和摘要内容
 */
function DocumentDetailPage() {
  const { id, documentId } = useParams();
  return (
    <AppShell>
      <PageContainer
        title="文档详情"
        description={`知识库 ${id || '--'} / 文档 ${documentId || '--'}`}
        actions={<Link className="btn btn-light" to={`/knowledge-bases/${id || ''}/documents`}>返回文档列表</Link>}
      >
        <article className="card document-detail">
          <h3>Redis 部署手册.md</h3>
          <p>本文档记录 Redis 高可用部署、连接配置、故障排查和运维注意事项。</p>
          <div className="detail-grid">
            <span>所属知识库</span><strong>运维故障知识库</strong>
            <span>文档类型</span><strong>README</strong>
            <span>更新时间</span><strong>2024-05-12 10:20</strong>
            <span>引用次数</span><strong>128</strong>
          </div>
          <div className="doc-content-preview">
            <h4>摘要</h4>
            <p>部署前确认 Redis 版本、网络策略、认证配置和实例资源。生产连接异常时优先检查网络连通性、认证信息、连接池耗尽和实例负载。</p>
          </div>
        </article>
      </PageContainer>
    </AppShell>
  );
}

function FrontModulePage({ title, description }: { title: string; description: string }) {
  return (
    <AppShell>
      <PageContainer title={title} description={description}>
        <section className="content-grid three-columns">
          {['最近访问', '重点内容', '快捷操作'].map((item, index) => (
            <article className="card" key={item}>
              <div className="empty-icon">{index + 1}</div>
              <h3>{item}</h3>
              <p>前台模块入口已就绪，后续可接入用户真实数据。</p>
            </article>
          ))}
        </section>
      </PageContainer>
    </AppShell>
  );
}

function DocumentPage() {
  return (
    <AppShell>
      <PageContainer
        title="文档管理"
        description="用于研发文档上传、归档、版本与解析状态管理。"
        actions={<button className="btn btn-primary" type="button" disabled>上传文档</button>}
      >
        <section className="content-grid three-columns">
          {[
            ['需求方案', '产品需求、研发方案与评审记录'],
            ['接口文档', '接口说明、联调记录与变更说明'],
            ['运维手册', '部署手册、故障复盘与巡检记录'],
          ].map(([title, text]) => (
            <article className="card" key={title}>
              <div className="empty-icon">{title.slice(0, 1)}</div>
              <h3>{title}</h3>
              <p>{text}</p>
              <span className="status-pill muted">待接入</span>
            </article>
          ))}
        </section>
      </PageContainer>
    </AppShell>
  );
}

/**
 * 智能问答页面
 * 提供基于知识库的 AI 问答交互界面
 */
function AssistantPage() {
  return (
    <AppShell>
      <section className="qa-workspace">
        <aside className="qa-sidebar card">
          <button className="btn btn-primary" type="button">新建会话</button>
          <h3>历史会话</h3>
          {['Redis 连接失败排查', '接口 500 错误定位', 'K8s 部署回滚方案', '数据库慢查询分析'].map((item, index) => (
            <button className={index === 0 ? 'qa-session active' : 'qa-session'} type="button" key={item}>{item}</button>
          ))}
        </aside>
        <article className="assistant-panel qa-panel">
          <header className="qa-header">
            <div>
              <h2>智能问答</h2>
              <p>已选择：DevBrain 项目知识库、运维故障知识库</p>
            </div>
            <button className="btn btn-light" type="button">选择知识库</button>
          </header>
          <div className="assistant-thread">
            <div className="message-row user">
              <strong>张开发</strong>
              <p>生产环境 Redis 连接失败如何排查？</p>
            </div>
            <div className="message-row assistant">
              <strong>DevBrain Assistant</strong>
              <p>建议先确认网络连通性、连接参数、Redis 实例状态和应用日志。下面是可执行的排查顺序。</p>
              <ol>
                <li>使用 telnet 或 nc 验证端口连通。</li>
                <li>核对连接地址、密码、超时时间和连接池配置。</li>
                <li>查看 Redis 控制台、slowlog 和应用异常栈。</li>
              </ol>
              <div className="citation-box">
                <strong>引用来源</strong>
                <span>Redis 部署手册.md</span>
                <span>运维故障 SOP</span>
              </div>
            </div>
          </div>
          <form className="assistant-input">
            <input aria-label="输入问题" placeholder="继续追问，或输入新的研发问题..." />
            <button className="btn btn-primary" type="button">发送</button>
          </form>
        </article>
      </section>
    </AppShell>
  );
}

/**
 * 系统设置页面
 * 管理个人资料（邮箱、显示名称、头像）和安全设置（修改密码）
 */
function SettingsPage() {
  const { user, updateProfile, changePassword, message } = useAuthStore();
  const [profile, setProfile] = useState({ email: user?.email || '', displayName: user?.displayName || '', avatar: user?.avatar || '' });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' });

  useEffect(() => {
    setProfile({ email: user?.email || '', displayName: user?.displayName || '', avatar: user?.avatar || '' });
  }, [user]);

  if (!user) return null;

  return (
    <AppShell>
      <PageContainer
        title="系统设置"
        description="管理个人资料、安全设置与管理员入口。"
        actions={user.roles.includes('admin') && <Link className="btn btn-light" to="/admin">权限控制</Link>}
      >
        <section className="content-grid two-columns">
          <article className="card">
            <div className="card-title">
              <div>
                <h3>个人资料</h3>
                <p>用于顶部栏与协作场景展示</p>
              </div>
            </div>
            <form className="stack-form" onSubmit={(event) => { event.preventDefault(); updateProfile(profile); }}>
              <label>邮箱<input value={profile.email} onChange={(event) => setProfile({ ...profile, email: event.target.value })} /></label>
              <label>显示名称<input value={profile.displayName || ''} onChange={(event) => setProfile({ ...profile, displayName: event.target.value })} /></label>
              <label>头像 URL<input value={profile.avatar || ''} onChange={(event) => setProfile({ ...profile, avatar: event.target.value })} /></label>
              <button className="btn btn-primary">保存资料</button>
            </form>
          </article>

          <article className="card">
            <div className="card-title">
              <div>
                <h3>安全设置</h3>
                <p>定期更新密码以保护账号</p>
              </div>
            </div>
            <form
              className="stack-form"
              onSubmit={(event) => {
                event.preventDefault();
                changePassword(passwords);
                setPasswords({ currentPassword: '', newPassword: '' });
              }}
            >
              <label>当前密码<input type="password" value={passwords.currentPassword} onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })} /></label>
              <label>新密码<input type="password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} minLength={8} /></label>
              <button className="btn btn-primary">更新密码</button>
            </form>
          </article>
        </section>
        {message && <p className="toast-line">{message}</p>}
      </PageContainer>
    </AppShell>
  );
}

/**
 * 管理后台仪表盘页面
 * 展示系统统计指标、问答趋势、知识库排行、系统健康状态等
 */
function AdminDashboardPage() {
  const trend = [
    ['05-06', '42%', '38%'],
    ['05-07', '48%', '40%'],
    ['05-08', '36%', '22%'],
    ['05-09', '72%', '34%'],
    ['05-10', '62%', '48%'],
    ['05-11', '46%', '28%'],
    ['05-12', '66%', '58%'],
  ];
  const ranking = [
    ['DevBrain 项目知识库', '12,842', '92%'],
    ['运维故障知识库', '8,731', '74%'],
    ['接口文档中心', '6,245', '58%'],
    ['部署手册合集', '4,913', '44%'],
    ['FAQ 标准库', '3,287', '31%'],
  ];
  const health = [
    ['API 服务', '正常', '120ms', 'ok'],
    ['PostgreSQL', '正常', '18ms', 'ok'],
    ['Redis', '正常', '5ms', 'ok'],
    ['向量库', '正常', '32ms', 'ok'],
    ['对象存储', '警告', '320ms', 'warn'],
    ['模型服务', '正常', '210ms', 'ok'],
  ];

  return (
    <AppShell mode="admin">
      <section className="admin-dashboard-page">
        <section className="admin-hero">
          <div>
            <h1>后台管理工作台</h1>
            <p>统一管理知识库、文档入库、问答效果与系统运行状态。</p>
          </div>
          <div className="admin-visual" aria-hidden="true">
            <span>DB</span>
          </div>
        </section>

        <section className="admin-metrics-grid">
          <FrontMetric label="知识库总数" value="12" delta="较昨日 ↑ 2" tone="blue" icon="database" />
          <FrontMetric label="文档总数" value="2,480" delta="较昨日 ↑ 68" tone="green" icon="fileText" />
          <FrontMetric label="今日问答量" value="1,328" delta="较昨日 ↑ 15.6%" tone="purple" icon="message" />
          <FrontMetric label="活跃用户" value="286" delta="较昨日 ↑ 8.3%" tone="cyan" icon="users" />
          <FrontMetric label="文档解析成功率" value="98.6%" delta="较昨日 ↑ 0.8%" tone="amber" icon="fileSearch" />
          <FrontMetric label="问答命中率" value="91.8%" delta="较昨日 ↑ 1.2%" tone="blue" icon="target" />
        </section>

        <section className="admin-dashboard-grid">
          <article className="card chart-card wide">
            <div className="card-title">
              <div>
                <h3>近7日问答与入库趋势</h3>
                <p>问答次数与新增文档同步观察</p>
              </div>
              <button className="btn btn-light" type="button">近7日</button>
            </div>
            <div className="trend-chart">
              {trend.map(([day, qa, docs]) => (
                <div className="trend-column" key={day}>
                  <div className="trend-bars">
                    <span style={{ height: qa }} />
                    <span style={{ height: docs }} />
                  </div>
                  <small>{day}</small>
                </div>
              ))}
            </div>
          </article>

          <article className="card ranking-card">
            <div className="card-title">
              <h3>知识库使用排行</h3>
              <Link to="/admin/knowledge-bases">查看更多</Link>
            </div>
            {ranking.map(([name, count, width], index) => (
              <div className="rank-row" key={name}>
                <span>{index + 1}</span>
                <strong>{name}</strong>
                <i><b style={{ width }} /></i>
                <small>{count}</small>
              </div>
            ))}
          </article>

          <article className="card health-card">
            <div className="card-title">
              <h3>系统健康状态</h3>
              <Link to="/admin/audit">查看详情</Link>
            </div>
            {health.map(([name, status, latency, tone], index) => (
              <div className="health-row" key={name}>
                <span>{index + 1}</span>
                <strong>{name}</strong>
                <em className={tone}>{status}</em>
                <small>{latency}</small>
              </div>
            ))}
          </article>

          <AdminTableCard title="待处理入库任务" action="/admin/ingestion" rows={[
            ['Redis部署手册解析', '文档解析', '进行中', '张三', '10:26'],
            ['运维SOP重建索引', '向量化', '待处理', '李四', '10:18'],
            ['接口文档同步', '入库同步', '失败', '王五', '09:52'],
            ['部署手册文档解析', '文档解析', '已完成', '赵六', '09:31'],
          ]} />

          <article className="card recent-doc-card">
            <div className="card-title">
              <h3>最近更新文档</h3>
              <Link to="/admin/documents">查看更多</Link>
            </div>
            {['Redis部署手册.md', '运维SOP v2.3.pdf', '接口文档规范.xlsx', '部署手册（K8s）.md', 'FAQ标准库更新.pptx'].map((item, index) => (
              <div className="doc-row admin-doc-row" key={item}>
                <span><UiIcon name="fileText" /></span>
                <div>
                  <strong>{item}</strong>
                  <small>{['张三', '李四', '王五', '赵六', '孙七'][index]}　2024-05-12</small>
                </div>
              </div>
            ))}
          </article>

          <article className="card feedback-card">
            <div className="card-title">
              <h3>用户反馈</h3>
              <Link to="/admin/qa">查看更多</Link>
            </div>
            <div className="feedback-summary">
              <strong>82%<span>满意</span></strong>
              <strong>13%<span>一般</span></strong>
              <strong>5%<span>不满意</span></strong>
            </div>
            <div className="feedback-bars">
              <span><b style={{ width: '82%' }} /></span>
              <span><b style={{ width: '13%' }} /></span>
              <span><b style={{ width: '5%' }} /></span>
            </div>
          </article>
        </section>
      </section>
    </AppShell>
  );
}

/**
 * 管理后台 - 入库任务页面
 * 跟踪文档定时同步任务，支持手动触发同步和查看同步历史
 */
function AdminIngestionPage() {
  const [tasks, setTasks] = useState<SyncTaskOverviewItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [historyDocId, setHistoryDocId] = useState<string | null>(null);
  const [triggering, setTriggering] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTasks(await syncApi.getSyncTaskOverview());
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleTrigger = async (docId: string) => {
    setTriggering(docId);
    try {
      const result = await syncApi.triggerSync(docId);
      alert(result.message);
      load();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : '触发同步失败');
    } finally {
      setTriggering(null);
    }
  };

  return (
    <AppShell mode="admin">
      <PageContainer title="入库任务" description="跟踪文档定时同步、解析和向量化任务。">
        {error && <div className="error-banner" style={{ marginBottom: 12 }}>{error}</div>}
        <div className="card" style={{ overflow: 'auto' }}>
          <table className="document-table">
            <thead>
              <tr>
                <th>文档名称</th>
                <th>来源类型</th>
                <th>来源地址</th>
                <th>同步计划</th>
                <th>最近同步</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={6} style={{ textAlign: 'center', padding: 24 }}>加载中...</td></tr>
              ) : tasks.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: 'center', padding: 24 }}>暂无定时同步任务</td></tr>
              ) : tasks.map(t => (
                <tr key={t.docId}>
                  <td>{t.docName}</td>
                  <td><span className={`doc-status-${t.sourceType === 'feishu' ? 'running' : 'success'}`}>{t.sourceType}</span></td>
                  <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.sourceLocation}</td>
                  <td>{formatScheduleCron(t.scheduleCron)}</td>
                  <td>{t.lastSyncTime ? new Date(t.lastSyncTime).toLocaleString() : '从未同步'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button className="btn-light" disabled={triggering === t.docId} onClick={() => handleTrigger(t.docId)}>
                        {triggering === t.docId ? '同步中...' : '立即同步'}
                      </button>
                      <button className="btn-light" onClick={() => setHistoryDocId(t.docId)}>历史</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {historyDocId && <SyncHistoryModal docId={historyDocId} onClose={() => setHistoryDocId(null)} />}
      </PageContainer>
    </AppShell>
  );
}

/**
 * 同步历史弹窗
 * 展示指定文档的同步历史记录，包含状态、内容变更、耗时等信息
 */
function SyncHistoryModal({ docId, onClose }: { docId: string; onClose: () => void }) {
  const [history, setHistory] = useState<SyncHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  useEffect(() => {
    setLoading(true);
    syncApi.getSyncHistory(docId, pageNo, pageSize)
      .then(res => { setHistory(res.records); setTotal(res.total); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [docId, pageNo]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={e => e.stopPropagation()} style={{ maxWidth: 800 }}>
        <div className="modal-header"><h3>同步历史</h3></div>
        <div className="modal-body">
          <table className="document-table">
            <thead>
              <tr><th>时间</th><th>状态</th><th>内容变更</th><th>耗时</th><th>错误信息</th></tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} style={{ textAlign: 'center' }}>加载中...</td></tr>
              ) : history.length === 0 ? (
                <tr><td colSpan={5} style={{ textAlign: 'center' }}>暂无记录</td></tr>
              ) : history.map(h => (
                <tr key={h.id}>
                  <td>{new Date(h.createTime).toLocaleString()}</td>
                  <td><span className={`doc-status-${h.syncStatus === 'success' ? 'success' : 'error'}`}>{h.syncStatus === 'success' ? '成功' : '失败'}</span></td>
                  <td>{h.contentChanged ? '是' : '否'}</td>
                  <td>{h.durationMs != null ? `${h.durationMs}ms` : '-'}</td>
                  <td style={{ maxWidth: 200, color: '#a33a2f' }}>{h.errorMessage || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {total > pageSize && (
            <div className="pagination-bar" style={{ marginTop: 12 }}>
              <button className="btn-light" disabled={pageNo <= 1} onClick={() => setPageNo(p => p - 1)}>上一页</button>
              <span>第 {pageNo} 页 / 共 {Math.ceil(total / pageSize)} 页</span>
              <button className="btn-light" disabled={pageNo * pageSize >= total} onClick={() => setPageNo(p => p + 1)}>下一页</button>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn-light" onClick={onClose}>关闭</button>
        </div>
      </div>
    </div>
  );
}

function AdminModulePage({ title, description }: { title: string; description: string }) {
  return (
    <AppShell mode="admin">
      <PageContainer title={title} description={description}>
        <section className="content-grid three-columns">
          {['列表管理', '状态追踪', '配置维护'].map((item, index) => (
            <article className="card" key={item}>
              <div className="empty-icon">{index + 1}</div>
              <h3>{item}</h3>
              <p>模块入口已完成，后续可继续接入真实接口和业务操作。</p>
            </article>
          ))}
        </section>
      </PageContainer>
    </AppShell>
  );
}

/**
 * 管理后台 - 用户权限管理页面
 * 包含用户管理、角色管理、权限管理、部门管理四个 Tab
 */
function AdminPage() {
  const [tab, setTab] = useState<AdminTab>('users');

  return (
    <AppShell mode="admin">
      <PageContainer title="用户权限" description="维护用户、角色、权限和部门组织关系。">
        <nav className="tabbar" aria-label="管理视图">
          {[
            ['users', '用户管理'],
            ['roles', '角色管理'],
            ['permissions', '权限管理'],
            ['departments', '部门管理'],
          ].map(([key, label]) => (
            <button key={key} type="button" className={tab === key ? 'active' : ''} onClick={() => setTab(key as AdminTab)}>{label}</button>
          ))}
        </nav>
        {tab === 'users' ? <UsersPanel /> : tab === 'roles' ? <RolesPanel /> : tab === 'permissions' ? <ResourcesPanel /> : <DepartmentsPanel />}
      </PageContainer>
    </AppShell>
  );
}

/**
 * 用户管理面板
 * 提供用户创建表单和用户列表展示
 */
function UsersPanel() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [draft, setDraft] = useState({ username: '', email: '', password: '', displayName: '', roleCodes: 'user' });
  const [message, setMessage] = useState<string | null>(null);
  const load = () => authApi.getUsers().then((page) => setUsers(page.records));

  useEffect(() => { load(); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      await authApi.saveUser({ ...draft, roleCodes: draft.roleCodes.split(',').map((item) => item.trim()).filter(Boolean) });
      setDraft({ username: '', email: '', password: '', displayName: '', roleCodes: 'user' });
      await load();
      setMessage('用户已保存');
    } catch (error) {
      setMessage((error as Error).message);
    }
  }

  return (
    <section className="content-grid admin-grid">
      <form className="card stack-form" onSubmit={submit}>
        <h3>创建用户</h3>
        <label>用户名<input value={draft.username} onChange={(e) => setDraft({ ...draft, username: e.target.value })} required /></label>
        <label>邮箱<input value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} type="email" required /></label>
        <label>初始密码<input value={draft.password} onChange={(e) => setDraft({ ...draft, password: e.target.value })} type="password" minLength={8} required /></label>
        <label>显示名称<input value={draft.displayName} onChange={(e) => setDraft({ ...draft, displayName: e.target.value })} /></label>
        <label>角色编码<input value={draft.roleCodes} onChange={(e) => setDraft({ ...draft, roleCodes: e.target.value })} /></label>
        <button className="btn btn-primary">保存</button>
        {message && <p className="notice">{message}</p>}
      </form>
      <DataList
        title="用户列表"
        columns={['用户', '邮箱 / 状态', '角色', '操作']}
        rows={users.map((user) => ({
          key: user.id,
          cells: [
            user.username,
            `${user.email} / ${user.status}`,
            <ChipRow items={user.roles} />,
            <button className="btn btn-danger" type="button" onClick={() => authApi.deleteUser(user.id).then(load)}>删除</button>,
          ],
        }))}
      />
    </section>
  );
}

/**
 * 角色管理面板
 * 提供角色创建和权限分配功能
 */
function RolesPanel() {
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [permissions, setPermissions] = useState<PermissionItem[]>([]);
  const [draft, setDraft] = useState({ roleCode: '', roleName: '', description: '' });
  const load = () => Promise.all([authApi.getRoles(), authApi.getPermissions()]).then(([nextRoles, nextPermissions]) => { setRoles(nextRoles); setPermissions(nextPermissions); });

  useEffect(() => { load(); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await authApi.saveRole(draft);
    setDraft({ roleCode: '', roleName: '', description: '' });
    await load();
  }

  return (
    <section className="content-grid admin-grid">
      <form className="card stack-form" onSubmit={submit}>
        <h3>角色节点</h3>
        <label>角色编码<input value={draft.roleCode} onChange={(e) => setDraft({ ...draft, roleCode: e.target.value })} required /></label>
        <label>角色名称<input value={draft.roleName} onChange={(e) => setDraft({ ...draft, roleName: e.target.value })} required /></label>
        <label>描述<input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} /></label>
        <button className="btn btn-primary">新增角色</button>
      </form>
      <article className="card">
        <div className="card-title">
          <div>
            <h3>权限分配</h3>
            <p>点击权限标签为角色授权或取消授权</p>
          </div>
        </div>
        <div className="role-list">
          {roles.map((role) => (
            <article className="role-row" key={role.id}>
              <div>
                <strong>{role.roleName}</strong>
                <span>{role.roleCode}</span>
              </div>
              <div className="permission-cloud">
                {permissions.map((permission) => (
                  <button
                    key={permission.id}
                    type="button"
                    className={role.permissionCodes.includes(permission.permissionCode) ? 'active perm-button' : 'perm-button'}
                    onClick={() => {
                      const next = role.permissionCodes.includes(permission.permissionCode)
                        ? role.permissionCodes.filter((item) => item !== permission.permissionCode)
                        : [...role.permissionCodes, permission.permissionCode];
                      authApi.assignRolePermissions(role.id, next).then(load);
                    }}
                  >
                    {permission.permissionCode}
                  </button>
                ))}
              </div>
            </article>
          ))}
        </div>
      </article>
    </section>
  );
}

/**
 * 资源管理面板
 * 管理 API 接口资源规则，定义 HTTP 方法、路径与权限的映射关系
 */
function ResourcesPanel() {
  const [resources, setResources] = useState<ResourceItem[]>([]);
  const [draft, setDraft] = useState({ resourceName: '', httpMethod: 'GET', pathPattern: '', permissionCode: '', publicAccess: 0 });
  const load = () => authApi.getResources().then(setResources);

  useEffect(() => { load(); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await authApi.saveResource(draft);
    setDraft({ resourceName: '', httpMethod: 'GET', pathPattern: '', permissionCode: '', publicAccess: 0 });
    await load();
  }

  return (
    <section className="content-grid admin-grid">
      <form className="card stack-form" onSubmit={submit}>
        <h3>接口资源规则</h3>
        <label>资源名称<input value={draft.resourceName} onChange={(e) => setDraft({ ...draft, resourceName: e.target.value })} required /></label>
        <label>HTTP 方法<input value={draft.httpMethod} onChange={(e) => setDraft({ ...draft, httpMethod: e.target.value.toUpperCase() })} required /></label>
        <label>路径模式<input value={draft.pathPattern} onChange={(e) => setDraft({ ...draft, pathPattern: e.target.value })} required /></label>
        <label>权限码<input value={draft.permissionCode} onChange={(e) => setDraft({ ...draft, permissionCode: e.target.value })} /></label>
        <button className="btn btn-primary">写入规则</button>
      </form>
      <DataList
        title="资源规则"
        columns={['接口', '资源名称', '权限', '操作']}
        rows={resources.map((resource) => ({
          key: resource.id,
          cells: [
            `${resource.httpMethod} ${resource.pathPattern}`,
            resource.resourceName,
            <ChipRow items={[resource.permissionCode || 'login-only']} />,
            <button className="btn btn-danger" type="button" onClick={() => authApi.deleteResource(resource.id).then(load)}>删除</button>,
          ],
        }))}
      />
    </section>
  );
}

function DepartmentsPanel() {
  return (
    <section className="content-grid three-columns">
      {['研发中心', '平台组', '运维组'].map((item, index) => (
        <article className="card" key={item}>
          <div className="empty-icon">{index + 1}</div>
          <h3>{item}</h3>
          <p>部门管理入口已完成，后续可接入组织架构、成员与权限继承接口。</p>
          <span className="status-pill muted">待接入</span>
        </article>
      ))}
    </section>
  );
}

/**
 * 前台统计指标卡片组件
 * @param label - 指标名称
 * @param value - 指标值
 * @param delta - 变化趋势描述
 * @param tone - 颜色主题
 * @param icon - 图标名称
 */
function FrontMetric({
  label,
  value,
  delta,
  tone,
  icon = 'target',
}: {
  label: string;
  value: string;
  delta: string;
  tone: 'blue' | 'green' | 'purple' | 'cyan' | 'amber';
  icon?: IconName;
}) {
  return (
    <article className={`front-metric ${tone}`}>
      <span className="metric-icon"><UiIcon name={icon} /></span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        <em>{delta}</em>
      </div>
    </article>
  );
}

/**
 * 洞察卡片组件
 * 用于侧边栏展示引用来源、相关文档、推荐问题等内容
 */
function InsightCard({ title, action, children }: { title: string; action?: string; children: ReactNode }) {
  return (
    <article className="insight-card">
      <div className="card-title">
        <h3>{title}</h3>
        {action && <button type="button">{action}</button>}
      </div>
      <div className="insight-stack">{children}</div>
    </article>
  );
}

function AdminTableCard({ title, action, rows }: { title: string; action: string; rows: string[][] }) {
  return (
    <article className="card admin-task-card">
      <div className="card-title">
        <h3>{title}<span className="count-dot">8</span></h3>
        <Link to={action}>查看全部</Link>
      </div>
      <table className="mini-table">
        <thead>
          <tr>
            <th>任务名称</th>
            <th>类型</th>
            <th>状态</th>
            <th>提交人</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row[0]}>
              {row.map((cell, index) => <td key={`${row[0]}-${index}`}>{index === 2 ? <span className="status-pill muted">{cell}</span> : cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </article>
  );
}

function MetricCard({ label, value, helper, tone }: { label: string; value: string; helper: string; tone?: 'success' }) {
  return (
    <article className={tone ? `metric-card ${tone}` : 'metric-card'}>
      <span>{label}</span>
      <strong>{value}</strong>
      <p>{helper}</p>
    </article>
  );
}

/**
 * 通用数据列表组件
 * 渲染表格形式的数据列表，支持自定义列和操作按钮
 * @param title - 表格标题
 * @param columns - 列名数组
 * @param rows - 数据行数组，每行包含 key 和 cells
 */
function DataList({ title, columns, rows }: { title: string; columns: string[]; rows: { key: string; cells: ReactNode[] }[] }) {
  return (
    <article className="card table-card">
      <div className="card-title">
        <div>
          <h3>{title}</h3>
          <p>{rows.length} 条记录</p>
        </div>
      </div>
      <table className="data-table">
        <thead>
          <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>
          {rows.length ? rows.map((row) => (
            <tr key={row.key}>{row.cells.map((cell, index) => <td key={`${row.key}-${index}`}>{cell}</td>)}</tr>
          )) : (
            <tr>
              <td colSpan={columns.length}>暂无数据</td>
            </tr>
          )}
        </tbody>
      </table>
    </article>
  );
}

function PermissionCloud({ items }: { items: string[] }) {
  return (
    <div className="permission-cloud">
      {items.map((item) => <span key={item}>{item}</span>)}
    </div>
  );
}

function ChipRow({ items }: { items: string[] }) {
  return (
    <div className="chip-row">
      {items.map((item) => <span className="chip" key={item}>{item}</span>)}
    </div>
  );
}

/**
 * 状态徽章组件
 * 根据状态值（enabled/disabled）渲染不同样式的标签
 */
function StatusBadge({ status }: { status: string }) {
  const normalized = status === 'enabled' || status === 'disabled' ? status : 'unknown';
  return <span className={`status-pill ${normalized}`}>{statusLabel(status)}</span>;
}

/**
 * 通用弹窗组件
 * @param title - 弹窗标题
 * @param children - 弹窗主体内容
 * @param footer - 弹窗底部操作区域，可选
 * @param onClose - 关闭回调（点击遮罩或关闭按钮触发）
 */
function Modal({ title, children, footer, onClose }: { title: string; children: ReactNode; footer?: ReactNode; onClose: () => void }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="modal-panel" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <header className="modal-header">
          <h3 id="modal-title">{title}</h3>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose}>×</button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-footer">{footer}</footer>}
      </section>
    </div>
  );
}

/**
 * 格式化日期时间
 * @param value - ISO 格式的日期字符串
 * @returns 本地化的日期时间字符串
 */
function formatDate(value?: string | null) {
  if (!value) return '--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

/**
 * 格式化短日期
 * 今天的时间显示为时分，其他日期显示为月-日
 * @param value - ISO 格式的日期字符串
 * @returns 短格式日期字符串
 */
function formatShortDate(value?: string | null) {
  if (!value) return '--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return `${date.getMonth() + 1}-${date.getDate().toString().padStart(2, '0')}`;
}

function statusLabel(status: string) {
  if (status === 'enabled') return '启用';
  if (status === 'disabled') return '停用';
  return status || '--';
}

/**
 * 获取文档状态的中文标签
 * @param status - 原始状态值
 * @returns 中文状态标签
 */
function docStatusLabel(status: string) {
  const normalized = normalizeDocStatus(status);
  if (normalized === 'pending') return '等待处理';
  if (normalized === 'processing') return '处理中';
  if (normalized === 'completed') return '处理成功';
  if (normalized === 'failed') return '处理失败';
  return status || '--';
}

function docStatusClass(status: string) {
  const normalized = normalizeDocStatus(status);
  if (normalized === 'completed') return 'success';
  if (normalized === 'failed') return 'error';
  if (normalized === 'processing') return 'running';
  return 'pending';
}

function normalizeDocStatus(status: string) {
  if (status === 'running') return 'processing';
  if (status === 'success') return 'completed';
  return status;
}

/**
 * 提取错误信息
 * @param error - 错误对象
 * @returns 错误消息字符串
 */
function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败';
}

/**
 * 启动加载屏幕
 * 应用初始化时显示的加载提示
 */
function BootScreen() {
  return <main className="boot-screen"><span />正在同步身份信息...</main>;
}

/**
 * 状态页面组件
 * 用于 403、404 等错误状态页面的展示
 * @param code - 状态码
 * @param title - 页面标题
 * @param text - 页面描述文本
 */
function StatusPage({ code, title, text }: { code: string; title: string; text: string }) {
  return (
    <main className="auth-stage compact">
      <section className="auth-panel status-panel">
        <p className="status-code">{code}</p>
        <h2>{title}</h2>
        <p>{text}</p>
        <Link className="btn btn-primary" to="/">返回入口</Link>
      </section>
    </main>
  );
}

export default App;
