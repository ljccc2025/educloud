import { create } from 'zustand';
import { http } from '../../services/http';
import type { StudentNotification, NotificationKind } from './types';

/**
 * 通知中心 Store（M10 联调修复）：
 * 此前是写死的 6 条演示数据，页面永不请求后端；
 * 现改为调用 notification 服务真实 API，并支持已读状态同步。
 */
interface NotificationItemDto {
  id: string;
  notificationId: string;
  title: string;
  content: string;
  kind: NotificationKind;
  actionLabel?: string | null;
  actionPath?: string | null;
  read: boolean;
  createdAt: string;
}

interface NotificationState {
  notifications: StudentNotification[];
  loading: boolean;
  fetchNotifications: () => Promise<void>;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
}

/** 后端 ISO 时间（2026-08-25T16:02:41.37）→ 页面展示格式（2026-08-25 16:02）。 */
function toView(dto: NotificationItemDto): StudentNotification {
  return {
    // Snowflake ID 保留字符串，Number() 会丢失精度导致已读/删除接口 404
    id: dto.id,
    kind: dto.kind,
    title: dto.title,
    content: dto.content,
    createdAt: dto.createdAt.replace('T', ' ').slice(0, 16),
    read: dto.read,
    actionLabel: dto.actionLabel ?? undefined,
    actionPath: dto.actionPath ?? undefined,
  };
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  loading: false,

  fetchNotifications: async () => {
    if (get().loading) return;
    set({ loading: true });
    try {
      const resp = await http.get<{ code: string; data: { items: NotificationItemDto[] } }>(
        '/notifications',
        { params: { page: 1, size: 50 } },
      );
      const items = resp.data?.data?.items ?? [];
      set({ notifications: items.map(toView) });
    } catch {
      // 后端暂不可用时保留现有数据，不打断页面
    } finally {
      set({ loading: false });
    }
  },

  markRead: async (id) => {
    // 乐观更新：先本地置为已读，再同步后端
    set((state) => ({
      notifications: state.notifications.map((notification) =>
        notification.id === id ? { ...notification, read: true } : notification,
      ),
    }));
    try {
      await http.put(`/notifications/${id}/read`);
    } catch {
      // 忽略同步失败，下次拉取会纠正
    }
  },

  markAllRead: async () => {
    set((state) => ({
      notifications: state.notifications.map((notification) =>
        notification.read ? notification : { ...notification, read: true },
      ),
    }));
    try {
      await http.put('/notifications/read-all');
    } catch {
      // 忽略同步失败，下次拉取会纠正
    }
  },
}));
