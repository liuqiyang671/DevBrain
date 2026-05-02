import { FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import { BrowserRouter, Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from './stores/authStore';
import * as authApi from './services/auth';
import * as knowledgeBaseApi from './services/knowledgeBase';
import type { KnowledgeBaseItem, KnowledgeBaseStatus, PermissionItem, ResourceItem, RoleItem, UserItem } from './types';

type AuthMode = 'login' | 'register' | 'forgot';
type AdminTab = 'users' | 'roles' | 'permissions' | 'departments';
type KnowledgeBaseModalMode = 'create' | 'edit';
type ShellMode = 'front' | 'admin';
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

interface ShellMenuItem {
  label: string;
  path: string;
  icon: IconName;
}

interface KnowledgeBaseFormState {
  name: string;
  description: string;
  collectionName: string;
  embeddingModel: string;
  status: KnowledgeBaseStatus;
}

const pageSizeOptions = [10, 20, 50];
const emptyKnowledgeBaseForm: KnowledgeBaseFormState = {
  name: '',
  description: '',
  collectionName: '',
  embeddingModel: '',
  status: 'enabled',
};

const frontMenuItems: ShellMenuItem[] = [
  { label: '首页', path: '/workspace', icon: 'home' },
  { label: '智能问答', path: '/assistant', icon: 'message' },
  { label: '知识库', path: '/knowledge-bases', icon: 'book' },
  { label: '历史记录', path: '/history', icon: 'history' },
  { label: '我的收藏', path: '/favorites', icon: 'star' },
  { label: '个人中心', path: '/profile', icon: 'user' },
];

const adminMenuItems: ShellMenuItem[] = [
  { label: '工作台', path: '/admin', icon: 'home' },
  { label: '知识库管理', path: '/admin/knowledge-bases', icon: 'database' },
  { label: '文档管理', path: '/admin/documents', icon: 'fileText' },
  { label: '问答管理', path: '/admin/qa', icon: 'message' },
  { label: '用户权限', path: '/admin/users', icon: 'shield' },
  { label: '标签分类', path: '/admin/tags', icon: 'tag' },
  { label: '入库任务', path: '/admin/ingestion', icon: 'box' },
  { label: '模型配置', path: '/admin/models', icon: 'target' },
  { label: '系统配置', path: '/admin/system', icon: 'settings' },
  { label: '日志审计', path: '/admin/audit', icon: 'fileSearch' },
  { label: '数据统计', path: '/admin/stats', icon: 'chart' },
];

const workspacePrompts: Array<{ title: string; text: string; icon: IconName }> = [
  { title: '接口报错排查', text: '定位接口异常原因', icon: 'code' },
  { title: '部署失败处理', text: '解决部署过程问题', icon: 'cloudUpload' },
  { title: '数据库连接问题', text: '排查连接异常', icon: 'database' },
  { title: '日志定位指南', text: '快速定位问题日志', icon: 'fileSearch' },
];

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
        <Route path="/admin/knowledge-bases/:id/documents" element={<RequireAuth><RequireAdmin><AdminModulePage title="知识库文档管理" description="管理该知识库下的文档列表、解析状态与分块数据。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/documents" element={<RequireAuth><RequireAdmin><AdminModulePage title="文档管理" description="包含文档列表、文档上传、解析状态和分块管理。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/qa" element={<RequireAuth><RequireAdmin><AdminModulePage title="问答管理" description="管理问答记录、反馈记录和 FAQ 内容。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/users" element={<RequireAuth><RequireAdmin><AdminPage /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/tags" element={<RequireAuth><RequireAdmin><AdminModulePage title="标签分类" description="维护知识库、文档和问答内容的标签体系。" /></RequireAdmin></RequireAuth>} />
        <Route path="/admin/ingestion" element={<RequireAuth><RequireAdmin><AdminModulePage title="入库任务" description="跟踪文档入库、解析、向量化和失败重试任务。" /></RequireAdmin></RequireAuth>} />
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

function HomeRedirect() {
  const user = useAuthStore((state) => state.user);
  return <Navigate to={user ? '/workspace' : '/auth'} replace />;
}

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

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  if (!user?.roles.includes('admin')) return <Navigate to="/403" replace />;
  return children;
}

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

  return (
    <AppShell mode="admin">
      <PageContainer
        title="知识库管理"
        description="统一管理知识空间、向量集合、模型配置和后续文档入口。"
        actions={<button className="btn btn-primary" type="button" onClick={openCreateModal}>新建知识库</button>}
      >
        <form className="card filter-toolbar" onSubmit={submitSearch}>
          <label className="toolbar-field">
            关键词
            <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索名称、描述或集合名" />
          </label>
          <label className="toolbar-field compact">
            状态
            <select value={status} onChange={(event) => setStatus(event.target.value as KnowledgeBaseStatus | '')}>
              <option value="">全部</option>
              <option value="enabled">启用</option>
              <option value="disabled">停用</option>
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
                  <th>描述</th>
                  <th>Embedding 模型</th>
                  <th>集合名</th>
                  <th>文档数量</th>
                  <th>Chunk 数量</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((item) => (
                  <tr key={item.id}>
                    <td><strong>{item.name}</strong></td>
                    <td className="muted-cell">{item.description || '--'}</td>
                    <td>{item.embeddingModel}</td>
                    <td><code>{item.collectionName}</code></td>
                    <td>{item.documentCount ?? 0}</td>
                    <td>{item.chunkCount ?? '--'}</td>
                    <td><StatusBadge status={item.status} /></td>
                    <td>{formatDate(item.createTime)}</td>
                    <td>
                      <div className="table-actions">
                        <button className="btn btn-light" type="button" onClick={() => openDetail(item)}>查看</button>
                        <button className="btn btn-light" type="button" onClick={() => navigate(`/admin/knowledge-bases/${item.id}/documents`)}>进入</button>
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
              <label>name<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required maxLength={128} /></label>
              <label>description<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} maxLength={512} rows={4} /></label>
              {modalMode === 'create' && (
                <label>collectionName<input value={form.collectionName} onChange={(event) => setForm({ ...form, collectionName: event.target.value })} required maxLength={64} placeholder="dev_knowledge" /></label>
              )}
              <label>embeddingModel<input value={form.embeddingModel} onChange={(event) => setForm({ ...form, embeddingModel: event.target.value })} required maxLength={64} placeholder="text-embedding-3-small" /></label>
              {modalMode === 'edit' && (
                <label>
                  status
                  <select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as KnowledgeBaseStatus })}>
                    <option value="enabled">enabled</option>
                    <option value="disabled">disabled</option>
                  </select>
                </label>
              )}
            </form>
          </Modal>
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

function KnowledgeBaseDocumentsPage() {
  const { id } = useParams();
  return (
    <AppShell>
      <PageContainer
        title="知识库文档"
        description={`当前知识库 ID：${id || '--'}。文档列表接口接入后将在这里展示。`}
        actions={<Link className="btn btn-light" to="/knowledge-bases">返回知识库</Link>}
      >
        <article className="card">
          <div className="empty-state">
            <div className="empty-icon">文</div>
            <h3>文档页入口已就绪</h3>
            <p>后续文档上传、解析、Chunk 和向量化状态可以接入此页面。</p>
          </div>
        </article>
      </PageContainer>
    </AppShell>
  );
}

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

function StatusBadge({ status }: { status: string }) {
  const normalized = status === 'enabled' || status === 'disabled' ? status : 'unknown';
  return <span className={`status-pill ${normalized}`}>{statusLabel(status)}</span>;
}

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

function formatDate(value?: string | null) {
  if (!value) return '--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function statusLabel(status: string) {
  if (status === 'enabled') return '启用';
  if (status === 'disabled') return '停用';
  return status || '--';
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败';
}

function BootScreen() {
  return <main className="boot-screen"><span />正在同步身份信息...</main>;
}

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
