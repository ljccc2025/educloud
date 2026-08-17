import { create } from 'zustand';
import type { LiveRoom } from '../types';
import { api } from '../services/api';

interface LiveState {
  liveRooms: LiveRoom[];
  currentRoom: LiveRoom | null;
  loading: boolean;
  fetchLiveRooms: () => Promise<void>;
  createLiveRoom: (data: Partial<LiveRoom>) => Promise<LiveRoom>;
  startLive: (id: string) => Promise<void>;
  endLive: (id: string) => Promise<void>;
  setCurrentRoom: (room: LiveRoom | null) => void;
}

export const useLiveStore = create<LiveState>((set, get) => ({
  liveRooms: [],
  currentRoom: null,
  loading: false,

  fetchLiveRooms: async () => {
    set({ loading: true });
    const rooms = await api.getLiveRooms();
    set({ liveRooms: rooms, loading: false });
  },

  createLiveRoom: async (data) => {
    const room = await api.createLiveRoom(data);
    set((state) => ({ liveRooms: [room, ...state.liveRooms] }));
    return room;
  },

  startLive: async (id) => {
    const updated = await api.startLive(id);
    if (updated) {
      set((state) => ({
        liveRooms: state.liveRooms.map((r) => (r.id === id ? updated : r)),
        currentRoom: state.currentRoom?.id === id ? updated : state.currentRoom,
      }));
    }
  },

  endLive: async (id) => {
    const updated = await api.endLive(id);
    if (updated) {
      set((state) => ({
        liveRooms: state.liveRooms.map((r) => (r.id === id ? updated : r)),
        currentRoom: state.currentRoom?.id === id ? updated : state.currentRoom,
      }));
    }
  },

  setCurrentRoom: (room) => set({ currentRoom: room }),
}));
