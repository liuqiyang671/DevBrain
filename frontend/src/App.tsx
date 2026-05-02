import { FormEvent, useEffect, useMemo, useState } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from './stores/authStore';
import * as authApi from './services/auth';
import type { PermissionItem, ResourceItem, RoleItem, UserItem } from './types';

type AuthMode = 'login' | 'register' | 'forgot';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/workspace" element={<RequireAuth><WorkspacePage /></RequireAuth>} />
        <Route path="/admin" element={<RequireAuth><RequireAdmin><AdminPage /></RequireAdmin></RequireAuth>} />
        <Route path="/403" element={<StatusPage code="403" title="权限矩阵未授权" text="当前身份没有访问这个控制面的权限。" />} />
        <Route path="*" element={<StatusPage code="404" title="信标丢失" text="没有找到对应的 DevBrain 工作区坐标。" />} />
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
        setLocalMessage('如果邮箱存在，重置链接已经发送；本地开发可查看后端日志。');
      }
    } catch (error) {
      setLocalMessage((error as Error).message);
    }
  }

  return (
    <main className="auth-stage">
      <section className="signal-board" aria-label="DevBrain access console">
        <p className="eyebrow">DevBrain-CQUPT</p>
        <h1>AI knowledge command grid</h1>
        <p>
          Built-in identity, permission graph, CSRF shield, and login risk control are wired into one
          operating surface for campus knowledge operations.
        </p>
        <div className="radar" aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
        </div>
      </section>

      <section className="auth-panel" aria-labelledby="auth-title">
        <div className="mode-switch" role="tablist" aria-label="authentication mode">
          {(['login', 'register', 'forgot'] as AuthMode[]).map((item) => (
            <button key={item} className={mode === item ? 'active' : ''} onClick={() => { setMode(item); setMessage(null); }}>
              {item === 'login' ? '登录' : item === 'register' ? '注册' : '找回'}
            </button>
          ))}
        </div>
        <h2 id="auth-title">{mode === 'login' ? '进入工作台' : mode === 'register' ? '创建身份信标' : '重置访问密钥'}</h2>
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
              <input value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={8} />
            </label>
          )}
          <button className="primary-action" type="submit" disabled={loading}>
            {loading ? '同步中...' : mode === 'login' ? '启动会话' : mode === 'register' ? '创建账号' : '发送重置链接'}
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
        <p className="eyebrow">Reset key</p>
        <h2>重置访问密钥</h2>
        <form className="stack-form" onSubmit={submit}>
          <label>
            新密码
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} required />
          </label>
          <button className="primary-action">确认重置</button>
        </form>
        {message && <p className="notice">{message}</p>}
      </section>
    </main>
  );
}

function WorkspacePage() {
  const { user, logout, updateProfile, changePassword, message } = useAuthStore();
  const [profile, setProfile] = useState({ email: user?.email || '', displayName: user?.displayName || '', avatar: user?.avatar || '' });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' });
  const navigate = useNavigate();

  if (!user) return null;

  return (
    <main className="workspace">
      <WorkspaceHeader onLogout={logout} />
      <section className="console-grid">
        <article className="identity-panel">
          <p className="eyebrow">Identity</p>
          <h2>{user.displayName || user.username}</h2>
          <p>{user.email}</p>
          <div className="chip-row">
            {user.roles.map((role) => <span className="chip" key={role}>{role}</span>)}
          </div>
          <button className="ghost-action" onClick={() => navigate('/admin')} disabled={!user.roles.includes('admin')}>
            打开权限控制台
          </button>
        </article>
        <article className="matrix-panel">
          <p className="eyebrow">Permission matrix</p>
          <div className="permission-cloud">
            {user.permissions.length ? user.permissions.map((permission) => <span key={permission}>{permission}</span>) : <span>basic:workspace</span>}
          </div>
        </article>
        <article className="form-panel">
          <h3>个人资料</h3>
          <form className="stack-form" onSubmit={(event) => { event.preventDefault(); updateProfile(profile); }}>
            <label>邮箱<input value={profile.email} onChange={(event) => setProfile({ ...profile, email: event.target.value })} /></label>
            <label>显示名称<input value={profile.displayName} onChange={(event) => setProfile({ ...profile, displayName: event.target.value })} /></label>
            <label>头像 URL<input value={profile.avatar} onChange={(event) => setProfile({ ...profile, avatar: event.target.value })} /></label>
            <button className="primary-action">保存资料</button>
          </form>
        </article>
        <article className="form-panel">
          <h3>安全设置</h3>
          <form className="stack-form" onSubmit={(event) => { event.preventDefault(); changePassword(passwords); setPasswords({ currentPassword: '', newPassword: '' }); }}>
            <label>当前密码<input type="password" value={passwords.currentPassword} onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })} /></label>
            <label>新密码<input type="password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} minLength={8} /></label>
            <button className="primary-action">更新密码</button>
          </form>
        </article>
      </section>
      {message && <p className="toast-line">{message}</p>}
    </main>
  );
}

function AdminPage() {
  const [tab, setTab] = useState<'users' | 'roles' | 'resources'>('users');
  return (
    <main className="workspace">
      <WorkspaceHeader />
      <nav className="admin-tabs">
        {[
          ['users', '用户'],
          ['roles', '角色权限'],
          ['resources', '资源规则'],
        ].map(([key, label]) => (
          <button key={key} className={tab === key ? 'active' : ''} onClick={() => setTab(key as typeof tab)}>{label}</button>
        ))}
      </nav>
      {tab === 'users' ? <UsersPanel /> : tab === 'roles' ? <RolesPanel /> : <ResourcesPanel />}
    </main>
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
    <section className="admin-grid">
      <form className="form-panel stack-form" onSubmit={submit}>
        <h3>创建用户</h3>
        <label>用户名<input value={draft.username} onChange={(e) => setDraft({ ...draft, username: e.target.value })} required /></label>
        <label>邮箱<input value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} type="email" required /></label>
        <label>初始密码<input value={draft.password} onChange={(e) => setDraft({ ...draft, password: e.target.value })} type="password" minLength={8} required /></label>
        <label>显示名称<input value={draft.displayName} onChange={(e) => setDraft({ ...draft, displayName: e.target.value })} /></label>
        <label>角色编码<input value={draft.roleCodes} onChange={(e) => setDraft({ ...draft, roleCodes: e.target.value })} /></label>
        <button className="primary-action">保存</button>
        {message && <p className="notice">{message}</p>}
      </form>
      <DataList title="用户矩阵" items={users.map((user) => ({
        key: user.id,
        title: user.username,
        meta: `${user.email} / ${user.status}`,
        chips: user.roles,
        action: () => authApi.deleteUser(user.id).then(load),
      }))} />
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
    <section className="admin-grid">
      <form className="form-panel stack-form" onSubmit={submit}>
        <h3>角色节点</h3>
        <label>角色编码<input value={draft.roleCode} onChange={(e) => setDraft({ ...draft, roleCode: e.target.value })} required /></label>
        <label>角色名称<input value={draft.roleName} onChange={(e) => setDraft({ ...draft, roleName: e.target.value })} required /></label>
        <label>描述<input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} /></label>
        <button className="primary-action">新增角色</button>
      </form>
      <div className="table-panel">
        <h3>权限分配</h3>
        {roles.map((role) => (
          <article className="row-card" key={role.id}>
            <strong>{role.roleName}</strong>
            <span>{role.roleCode}</span>
            <div className="permission-cloud">
              {permissions.map((permission) => (
                <button
                  key={permission.id}
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
    <section className="admin-grid">
      <form className="form-panel stack-form" onSubmit={submit}>
        <h3>接口资源规则</h3>
        <label>资源名称<input value={draft.resourceName} onChange={(e) => setDraft({ ...draft, resourceName: e.target.value })} required /></label>
        <label>HTTP 方法<input value={draft.httpMethod} onChange={(e) => setDraft({ ...draft, httpMethod: e.target.value.toUpperCase() })} required /></label>
        <label>路径模式<input value={draft.pathPattern} onChange={(e) => setDraft({ ...draft, pathPattern: e.target.value })} required /></label>
        <label>权限码<input value={draft.permissionCode} onChange={(e) => setDraft({ ...draft, permissionCode: e.target.value })} /></label>
        <button className="primary-action">写入规则</button>
      </form>
      <DataList title="资源防线" items={resources.map((resource) => ({
        key: resource.id,
        title: `${resource.httpMethod} ${resource.pathPattern}`,
        meta: resource.resourceName,
        chips: [resource.permissionCode || 'login-only'],
        action: () => authApi.deleteResource(resource.id).then(load),
      }))} />
    </section>
  );
}

function DataList({ title, items }: { title: string; items: { key: string; title: string; meta: string; chips: string[]; action?: () => void }[] }) {
  return (
    <div className="table-panel">
      <h3>{title}</h3>
      {items.map((item) => (
        <article className="row-card" key={item.key}>
          <strong>{item.title}</strong>
          <span>{item.meta}</span>
          <div className="chip-row">{item.chips.map((chip) => <span className="chip" key={chip}>{chip}</span>)}</div>
          {item.action && <button className="danger-action" onClick={item.action}>删除</button>}
        </article>
      ))}
    </div>
  );
}

function WorkspaceHeader({ onLogout }: { onLogout?: () => Promise<void> }) {
  const user = useAuthStore((state) => state.user);
  const initials = useMemo(() => (user?.displayName || user?.username || 'D').slice(0, 2).toUpperCase(), [user]);
  return (
    <header className="workspace-header">
      <Link to="/workspace" className="brand-mark"><span>{initials}</span>DevBrain</Link>
      <nav>
        <Link to="/workspace">工作台</Link>
        {user?.roles.includes('admin') && <Link to="/admin">权限控制</Link>}
        {onLogout && <button onClick={onLogout}>退出</button>}
      </nav>
    </header>
  );
}

function BootScreen() {
  return <main className="boot-screen"><span />正在同步身份信标...</main>;
}

function StatusPage({ code, title, text }: { code: string; title: string; text: string }) {
  return (
    <main className="auth-stage compact">
      <section className="auth-panel status-panel">
        <p className="status-code">{code}</p>
        <h2>{title}</h2>
        <p>{text}</p>
        <Link className="primary-action link-action" to="/">返回入口</Link>
      </section>
    </main>
  );
}

export default App;
