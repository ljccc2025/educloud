import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { TeacherNotification } from './types';

export const TEACHER_NOTIFICATION_STORAGE_KEY = 'educloud-teacher-notifications-v1';

export const initialTeacherNotifications: TeacherNotification[] = [
  {
    id: 'assignment-submission-1',
    kind: 'ASSIGNMENT',
    title: '有新的作业提交',
    content: '李思远提交了「RESTful 博客 API」作业，当前共有 22 份作业等待批改。',
    createdAt: '2026-08-20T09:25:00+08:00',
    read: false,
    actionLabel: '前往批改',
    actionPath: '/assignments',
  },
  {
    id: 'live-reminder-1',
    kind: 'LIVE',
    title: '直播课堂即将开始',
    content: '「Spring Boot 3 微服务架构直播答疑」将在今天 19:00 开始，请提前检查直播设置。',
    createdAt: '2026-08-20T08:40:00+08:00',
    read: false,
    actionLabel: '管理直播',
    actionPath: '/live',
  },
  {
    id: 'student-enrollment-1',
    kind: 'STUDENT',
    title: '新学员报名课程',
    content: '罗嘉豪报名了「Spring Boot 3 实战」，课程学员人数已更新。',
    createdAt: '2026-08-19T16:18:00+08:00',
    read: false,
    actionLabel: '查看学员',
    actionPath: '/students',
  },
  {
    id: 'exam-review-1',
    kind: 'EXAM',
    title: '考试已结束，等待处理',
    content: '「Java 核心能力阶段测验」已结束，可前往考试管理查看提交情况。',
    createdAt: '2026-08-19T14:06:00+08:00',
    read: false,
    actionLabel: '查看考试',
    actionPath: '/exams',
  },
  {
    id: 'course-published-1',
    kind: 'COURSE',
    title: '课程内容已发布',
    content: '「Python 数据分析项目实战」的新章节已成功发布，学员现在可以查看。',
    createdAt: '2026-08-18T11:30:00+08:00',
    read: true,
    actionLabel: '查看课程',
    actionPath: '/courses',
  },
  {
    id: 'system-maintenance-1',
    kind: 'SYSTEM',
    title: '平台维护通知',
    content: '平台将于 8 月 23 日凌晨进行短时维护，请提前保存课程编辑内容。',
    createdAt: '2026-08-18T09:00:00+08:00',
    read: true,
  },
];

export interface TeacherNotificationState {
  notifications: TeacherNotification[];
  markRead: (id: string) => void;
  markAllRead: () => void;
}

export const selectUnreadCount = (
  state: Pick<TeacherNotificationState, 'notifications'>,
) => state.notifications.reduce((count, notification) => count + Number(!notification.read), 0);

export const useTeacherNotificationStore = create<TeacherNotificationState>()(
  persist(
    (set) => ({
      notifications: initialTeacherNotifications.map((notification) => ({ ...notification })),
      markRead: (id) => set((state) => ({
        notifications: state.notifications.map((notification) => (
          notification.id === id && !notification.read
            ? { ...notification, read: true }
            : notification
        )),
      })),
      markAllRead: () => set((state) => ({
        notifications: state.notifications.map((notification) => (
          notification.read ? notification : { ...notification, read: true }
        )),
      })),
    }),
    {
      name: TEACHER_NOTIFICATION_STORAGE_KEY,
      storage: createJSONStorage(() => localStorage),
      partialize: ({ notifications }) => ({ notifications }),
    },
  ),
);
