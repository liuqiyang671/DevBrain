import { create } from 'zustand';
import type { CurrentUser } from '../types';
import * as authApi from '../services/auth';

interface AuthState {
  user: CurrentUser | null;
  loading: boolean;
  message: string | null;
  setMessage: (message: string | null) => void;
  hasPermission: (permission: string) => boolean;
  refresh: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  register: (payload: { username: string; email: string; password: string; displayName?: string }) => Promise<void>;
  logout: () => Promise<void>;
  updateProfile: (payload: { email?: string; displayName?: string; avatar?: string }) => Promise<void>;
  changePassword: (payload: { currentPassword: string; newPassword: string }) => Promise<void>;
}

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

window.addEventListener('devbrain-auth-expired', () => {
  useAuthStore.setState({ user: null, message: '登录已过期' });
});
