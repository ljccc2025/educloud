import { create } from 'zustand';
import type { StudentNotification } from './types';

const initialNotifications: StudentNotification[] = [
  {
    id: 1,
    kind: 'ASSIGNMENT',
    title: '作业截止时间提醒',
    content: '“第三章习题：导数与微分”将在 3 天后截止，请合理安排提交时间。',
    createdAt: '2026-08-18 09:20',
    read: false,
    actionLabel: '查看作业',
    actionPath: '/assignments',
  },
  {
    id: 2,
    kind: 'LIVE',
    title: '直播课堂即将开始',
    content: '李明远老师的“第三章直播答疑：导数的应用”将在今晚 19:30 开始。',
    createdAt: '2026-08-18 08:45',
    read: false,
    actionLabel: '进入直播',
    actionPath: '/live/1',
  },
  {
    id: 3,
    kind: 'EXAM',
    title: '考试成绩已发布',
    content: '“NumPy 基础测验”已完成批改，本次得分 45 / 50。',
    createdAt: '2026-08-17 18:10',
    read: false,
    actionLabel: '查看成绩',
    actionPath: '/exams',
  },
  {
    id: 4,
    kind: 'COURSE',
    title: '课程内容更新',
    content: '“前端工程化与 React 进阶”新增了性能优化专题与配套资料。',
    createdAt: '2026-08-17 14:30',
    read: true,
    actionLabel: '继续学习',
    actionPath: '/my-courses',
  },
  {
    id: 5,
    kind: 'SYSTEM',
    title: '完善学习档案',
    content: '补充学习方向和个人简介，可以获得更准确的课程推荐。',
    createdAt: '2026-08-16 10:00',
    read: false,
    actionLabel: '完善资料',
    actionPath: '/profile',
  },
  {
    id: 6,
    kind: 'SYSTEM',
    title: '订单退款已完成',
    content: '课程订单 EC2026062720004 的退款已原路退回。',
    createdAt: '2026-08-15 16:40',
    read: true,
    actionLabel: '查看订单',
    actionPath: '/orders',
  },
];

interface NotificationState {
  notifications: StudentNotification[];
  markRead: (id: number) => void;
  markAllRead: () => void;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: initialNotifications,
  markRead: (id) => set((state) => ({
    notifications: state.notifications.map((notification) =>
      notification.id === id ? { ...notification, read: true } : notification,
    ),
  })),
  markAllRead: () => set((state) => ({
    notifications: state.notifications.map((notification) =>
      notification.read ? notification : { ...notification, read: true },
    ),
  })),
}));
