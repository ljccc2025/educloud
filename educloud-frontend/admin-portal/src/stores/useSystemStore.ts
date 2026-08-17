import { create } from 'zustand';
import { systemApi } from '../services/api';
import type { SystemConfig } from '../types';

interface SystemStats {
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  uptime: string;
  nodeCount: number;
  serviceStatus: 'healthy' | 'warning' | 'error';
}

interface SystemState {
  config: SystemConfig | null;
  stats: SystemStats | null;
  loading: boolean;
  saving: boolean;
  fetchConfig: () => Promise<void>;
  saveConfig: (config: SystemConfig) => Promise<boolean>;
  fetchStats: () => Promise<void>;
}

export const useSystemStore = create<SystemState>((set) => ({
  config: null,
  stats: null,
  loading: false,
  saving: false,
  fetchConfig: async () => {
    set({ loading: true });
    const config = await systemApi.getConfig();
    set({ config, loading: false });
  },
  saveConfig: async (config) => {
    set({ saving: true });
    try {
      const saved = await systemApi.saveConfig(config);
      set({ config: saved, saving: false });
      return true;
    } catch {
      set({ saving: false });
      return false;
    }
  },
  fetchStats: async () => {
    const stats = await systemApi.getSystemStats();
    set({ stats });
  },
}));
