import { create } from 'zustand';
import { authApi, type AdminUser } from '../services/api';
import { apiErrorText } from '../services/http';

interface AuthState {
  admin: AdminUser | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  admin: null,
  token: localStorage.getItem('admin_token'),
  loading: false,
  error: null,
  login: async (username, password) => {
    set({ loading: true, error: null });
    try {
      const { token, admin } = await authApi.login(username, password);
      localStorage.setItem('admin_token', token);
      set({ admin, token, loading: false });
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
    localStorage.removeItem('admin_token');
    set({ admin: null, token: null });
  },
  restore: async () => {
    if (!get().token) return;
    set({ loading: true });
    try {
      const admin = await authApi.me();
      set({ admin, loading: false });
    } catch {
      localStorage.removeItem('admin_token');
      set({ admin: null, token: null, loading: false });
    }
  },
  clearError: () => set({ error: null }),
}));
