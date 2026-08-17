import { create } from 'zustand';
import { userApi } from '../services/api';
import type { User, UserStatus } from '../types';

interface UserState {
  users: User[];
  total: number;
  page: number;
  pageSize: number;
  loading: boolean;
  keyword: string;
  role: string;
  status: string;
  setPage: (page: number) => void;
  setKeyword: (keyword: string) => void;
  setRole: (role: string) => void;
  setStatus: (status: string) => void;
  fetchUsers: () => Promise<void>;
  updateUserStatus: (id: number, status: UserStatus) => Promise<void>;
}

export const useUserStore = create<UserState>((set, get) => ({
  users: [],
  total: 0,
  page: 1,
  pageSize: 10,
  loading: false,
  keyword: '',
  role: 'ALL',
  status: 'ALL',
  setPage: (page) => {
    set({ page });
    void get().fetchUsers();
  },
  setKeyword: (keyword) => {
    set({ keyword, page: 1 });
    void get().fetchUsers();
  },
  setRole: (role) => {
    set({ role, page: 1 });
    void get().fetchUsers();
  },
  setStatus: (status) => {
    set({ status, page: 1 });
    void get().fetchUsers();
  },
  fetchUsers: async () => {
    set({ loading: true });
    const { page, pageSize, keyword, role, status } = get();
    const res = await userApi.getUsers({ page, pageSize, keyword, role, status });
    set({ users: res.list, total: res.total, loading: false });
  },
  updateUserStatus: async (id, status) => {
    await userApi.updateStatus(id, status);
    set((state) => ({
      users: state.users.map((u) => (u.id === id ? { ...u, status } : u)),
    }));
  },
}));
