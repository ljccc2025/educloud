import { create } from 'zustand';
import { authApi, currentUser } from '../services/api';
import type { StudentUser } from '../types';

interface AuthState {
  user: StudentUser | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: localStorage.getItem('student_token'),
  loading: false,
  error: null,
  login: async (username, password) => {
    set({ loading: true, error: null });
    try {
      const { token } = await authApi.login(username, password);
      localStorage.setItem('student_token', token);
      set({ user: currentUser, token, loading: false });
      return true;
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
      return false;
    }
  },
  logout: () => {
    localStorage.removeItem('student_token');
    set({ user: null, token: null });
  },
  clearError: () => set({ error: null }),
}));
