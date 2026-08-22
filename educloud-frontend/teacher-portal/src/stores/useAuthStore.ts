import { create } from 'zustand';
import type { User } from '../types';
import { api } from '../services/api';
import { apiErrorText } from '../services/http';

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  token: localStorage.getItem('teacher_token'),
  loading: false,
  error: null,
  login: async (email: string, password: string) => {
    set({ loading: true, error: null });
    try {
      const res = await api.login(email, password);
      set({ user: res.user, token: res.token, loading: false });
    } catch (err) {
      set({ error: apiErrorText(err), loading: false });
      throw err;
    }
  },
  logout: async () => {
    try {
      await api.logout();
    } catch {
      // 本地始终清理。
    }
    set({ user: null, token: null });
  },
  restore: async () => {
    if (!get().token) return;
    set({ loading: true });
    try {
      const user = await api.getCurrentUser();
      set({ user, loading: false });
    } catch {
      localStorage.removeItem('teacher_token');
      set({ user: null, token: null, loading: false });
    }
  },
  clearError: () => set({ error: null }),
}));

// 401 刷新失败（会话过期）：同步清理 store 登录态，供路由跳转。
if (typeof window !== 'undefined') {
  window.addEventListener('auth:session-expired', () => {
    useAuthStore.setState({ user: null, token: null, loading: false });
  });
}
