import { beforeEach, describe, expect, it } from 'vitest';
import {
  initialTeacherNotifications,
  selectUnreadCount,
  useTeacherNotificationStore,
} from './useTeacherNotificationStore';

const resetStore = () => {
  localStorage.clear();
  useTeacherNotificationStore.setState({
    notifications: initialTeacherNotifications.map((notification) => ({ ...notification })),
  });
};

describe('useTeacherNotificationStore', () => {
  beforeEach(resetStore);

  it('提供教师业务通知并计算未读数量', () => {
    const state = useTeacherNotificationStore.getState();

    expect(state.notifications).toHaveLength(6);
    expect(selectUnreadCount(state)).toBe(4);
  });

  it('只将指定通知标记为已读', () => {
    useTeacherNotificationStore.getState().markRead('assignment-submission-1');

    const state = useTeacherNotificationStore.getState();
    expect(state.notifications.find(({ id }) => id === 'assignment-submission-1')?.read).toBe(true);
    expect(selectUnreadCount(state)).toBe(3);
  });

  it('可以将全部通知标记为已读', () => {
    useTeacherNotificationStore.getState().markAllRead();

    const state = useTeacherNotificationStore.getState();
    expect(state.notifications.every(({ read }) => read)).toBe(true);
    expect(selectUnreadCount(state)).toBe(0);
  });

  it('将已读状态持久化到稳定的浏览器存储键', () => {
    useTeacherNotificationStore.getState().markRead('assignment-submission-1');

    const persisted = localStorage.getItem('educloud-teacher-notifications-v1');
    expect(persisted).not.toBeNull();
    expect(persisted).toContain('assignment-submission-1');
    expect(persisted).toContain('"read":true');
  });

  it('重新水合后恢复已持久化的已读状态', async () => {
    useTeacherNotificationStore.getState().markRead('assignment-submission-1');
    const persisted = localStorage.getItem('educloud-teacher-notifications-v1');

    useTeacherNotificationStore.setState({
      notifications: initialTeacherNotifications.map((notification) => ({ ...notification })),
    });
    if (persisted) localStorage.setItem('educloud-teacher-notifications-v1', persisted);
    await useTeacherNotificationStore.persist.rehydrate();

    expect(
      useTeacherNotificationStore.getState().notifications
        .find(({ id }) => id === 'assignment-submission-1')?.read,
    ).toBe(true);
  });
});
