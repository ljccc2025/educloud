import { create } from 'zustand';
import type { User } from '../types';
import { api } from '../services/api';

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  loading: false,
  login: async (email: string, password: string) => {
    set({ loading: true });
    try {
      const res = await api.login(email, password);
      set({ user: res.user, token: res.token, loading: false });
    } catch (err) {
      set({ loading: false });
      throw err;
    }
  },
  logout: () => {
    set({ user: null, token: null });
  },
}));
