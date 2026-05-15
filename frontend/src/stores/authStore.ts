import { create } from 'zustand';
import type { CurrentUser } from '../types';
import * as authApi from '../services/auth';

/**
 * 认证状态接口
 * 定义用户认证相关的状态和操作方法
 */
interface AuthState {
  /** 当前登录用户信息，未登录时为 null */
  user: CurrentUser | null;
  /** 是否正在执行认证操作（登录、注册等） */
  loading: boolean;
  /** 全局提示消息（成功/失败提示） */
  message: string | null;
  /** 设置全局提示消息 */
  setMessage: (message: string | null) => void;
  /**
   * 检查当前用户是否拥有指定权限
   * 管理员角色自动拥有所有权限
   */
  hasPermission: (permission: string) => boolean;
  /** 刷新当前用户信息（从服务端重新获取） */
  refresh: () => Promise<void>;
  /** 用户登录 */
  login: (username: string, password: string) => Promise<void>;
  /** 用户注册 */
  register: (payload: { username: string; email: string; password: string; displayName?: string }) => Promise<void>;
  /** 用户登出 */
  logout: () => Promise<void>;
  /** 更新个人资料 */
  updateProfile: (payload: { email?: string; displayName?: string; avatar?: string }) => Promise<void>;
  /** 修改密码 */
  changePassword: (payload: { currentPassword: string; newPassword: string }) => Promise<void>;
}

/**
 * 认证状态管理 Store
 * 使用 Zustand 管理全局认证状态，提供登录、注册、登出、权限校验等功能
 *
 * 设计要点：
 * - JWT 存储在 HttpOnly Cookie 中，前端只存储用户信息
 * - 401 响应通过全局事件 'ai-shopping-agent-auth-expired' 触发状态重置
 * - loading 状态用于控制表单按钮的禁用状态
 */
export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  loading: false,
  message: null,
  setMessage: (message) => set({ message }),
  hasPermission: (permission) => {
    const user = get().user;
    return Boolean(user?.roles.includes('admin') || user?.permissions.includes(permission));
  },
  refresh: async () => {
    try {
      const user = await authApi.getCurrentUser();
      set({ user });
    } catch {
      set({ user: null });
    }
  },
  login: async (username, password) => {
    set({ loading: true, message: null });
    try {
      const result = await authApi.login({ username, password });
      set({ user: result.user, message: '登录成功' });
    } finally {
      set({ loading: false });
    }
  },
  register: async (payload) => {
    set({ loading: true, message: null });
    try {
      await authApi.register(payload);
      set({ message: '注册成功，请登录' });
    } finally {
      set({ loading: false });
    }
  },
  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      set({ user: null, message: '已退出登录' });
    }
  },
  updateProfile: async (payload) => {
    const user = await authApi.updateProfile(payload);
    set({ user, message: '资料已更新' });
  },
  changePassword: async (payload) => {
    await authApi.changePassword(payload);
    set({ message: '密码已更新' });
  },
}));

/**
 * 全局监听认证过期事件
 * 当 Axios 响应拦截器检测到 401 状态码时，会派发此事件
 * 触发后自动清除用户状态并显示过期提示
 */
window.addEventListener('ai-shopping-agent-auth-expired', () => {
  useAuthStore.setState({ user: null, message: '登录已过期' });
});
