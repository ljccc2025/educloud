import { create } from 'zustand';
import { authApi, type AdminUser } from '../services/api';

interface AuthState {
  admin: AdminUser | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
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
      set({ error: (e as Error).message, loading: false });
      return false;
    }
  },
  logout: () => {
    localStorage.removeItem('admin_token');
    set({ admin: null, token: null });
  },
  clearError: () => set({ error: null }),
}));
