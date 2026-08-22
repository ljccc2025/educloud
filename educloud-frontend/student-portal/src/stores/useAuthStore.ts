import { create } from 'zustand';
import { authApi } from '../services/api';
import { apiErrorText } from '../services/http';
import type { StudentUser } from '../types';

interface AuthState {
  user: StudentUser | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  login: (loginName: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  token: localStorage.getItem('student_token'),
  loading: false,
  error: null,
  login: async (loginName, password) => {
    set({ loading: true, error: null });
    try {
      const { token, user } = await authApi.login(loginName, password);
      set({ user, token, loading: false });
      return true;
    } catch (e) {
      set({ error: apiErrorText(e), loading: false });
      return false;
    }
  },
  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      // 本地始终清理。
    }
    set({ user: null, token: null });
  },
  restore: async () => {
    if (!get().token) return;
    set({ loading: true });
    try {
      const user = await authApi.me();
      set({ user, loading: false });
    } catch {
      localStorage.removeItem('student_token');
      set({ user: null, token: null, loading: false });
    }
  },
  clearError: () => set({ error: null }),
}));
