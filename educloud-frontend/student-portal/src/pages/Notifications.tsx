import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  BookOpen,
  CalendarCheck,
  CheckCheck,
  ClipboardCheck,
  CreditCard,
  Radio,
  Settings,
} from 'lucide-react';
import { useNotificationStore } from '../features/engagement/useNotificationStore';
import type { NotificationKind } from '../features/engagement/types';
import { cn } from '../utils/cn';

const kindConfig: Record<NotificationKind, { label: string; icon: typeof Bell; className: string }> = {
  COURSE: { label: '课程', icon: BookOpen, className: 'bg-indigo-50 text-indigo-700' },
  ASSIGNMENT: { label: '作业', icon: ClipboardCheck, className: 'bg-amber-50 text-amber-700' },
  EXAM: { label: '考试', icon: CalendarCheck, className: 'bg-green-50 text-green-700' },
  LIVE: { label: '直播', icon: Radio, className: 'bg-red-50 text-red-700' },
  SYSTEM: { label: '系统', icon: Settings, className: 'bg-ink-100 text-ink-600' },
  PAYMENT: { label: '支付', icon: CreditCard, className: 'bg-emerald-50 text-emerald-700' },
};

type NotificationFilter = 'ALL' | 'UNREAD';

export default function Notifications() {
  const navigate = useNavigate();
  const notifications = useNotificationStore((state) => state.notifications);
  const loading = useNotificationStore((state) => state.loading);
  const fetchNotifications = useNotificationStore((state) => state.fetchNotifications);
  const markRead = useNotificationStore((state) => state.markRead);
  const markAllRead = useNotificationStore((state) => state.markAllRead);
  const [filter, setFilter] = useState<NotificationFilter>('ALL');

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  const unreadCount = useMemo(
    () => notifications.reduce((count, notification) => count + Number(!notification.read), 0),
    [notifications],
  );
  const visibleNotifications = useMemo(
    () => filter === 'UNREAD' ? notifications.filter((notification) => !notification.read) : notifications,
    [filter, notifications],
  );

  const openNotification = (id: string, actionPath?: string) => {
    markRead(id);
    if (actionPath) navigate(actionPath);
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 md:px-8 md:py-14 animate-fade-up">
      <div className="flex flex-col gap-5 border-b border-ink-100 pb-8 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="section-label mb-3">消息中心</p>
          <h1 className="display-heading text-4xl md:text-5xl">通知中心</h1>
          <p className="mt-3 text-sm text-ink-500">及时查看课程、直播、作业和考试动态</p>
        </div>
        <button
          type="button"
          onClick={markAllRead}
          disabled={unreadCount === 0}
          className="btn-outline self-start disabled:cursor-not-allowed disabled:opacity-45 md:self-auto"
        >
          <CheckCheck size={17} />
          全部标记为已读
        </button>
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="card-editorial h-fit p-4">
          <div className="mb-4 flex items-center justify-between border-b border-ink-100 px-2 pb-4">
            <span className="text-sm font-semibold text-ink-800">消息概览</span>
            <span className="font-display text-2xl font-bold text-indigo-800">{unreadCount}</span>
          </div>
          <div className="space-y-1">
            {([
              ['ALL', '全部通知', notifications.length],
              ['UNREAD', '未读通知', unreadCount],
            ] as const).map(([value, label, count]) => (
              <button
                key={value}
                type="button"
                onClick={() => setFilter(value)}
                className={cn(
                  'flex w-full items-center justify-between px-3 py-3 text-left text-sm transition-colors',
                  filter === value ? 'bg-indigo-50 font-medium text-indigo-800' : 'text-ink-600 hover:bg-ink-50',
                )}
              >
                <span>{label}</span>
                <span className="text-xs text-ink-400">{count}</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="min-w-0 space-y-3" aria-live="polite">
          {loading && notifications.length === 0 ? (
            <div className="card-editorial flex min-h-64 items-center justify-center px-6 text-center">
              <p className="text-sm text-ink-400">加载中…</p>
            </div>
          ) : visibleNotifications.length === 0 ? (
            <div className="card-editorial flex min-h-64 flex-col items-center justify-center px-6 text-center">
              <CheckCheck className="mb-4 text-green-600" size={36} />
              <h2 className="font-display text-xl font-semibold text-ink-900">
                {filter === 'UNREAD' ? '没有未读通知' : '暂无通知'}
              </h2>
              <p className="mt-2 text-sm text-ink-400">新的学习动态会及时出现在这里</p>
            </div>
          ) : visibleNotifications.map((notification) => {
            const config = kindConfig[notification.kind];
            const Icon = config.icon;
            return (
              <article
                key={notification.id}
                className={cn(
                  'card-editorial relative p-5 md:p-6',
                  !notification.read && 'border-l-2 border-l-amber-600 bg-amber-50/25',
                )}
              >
                <div className="flex gap-4">
                  <div className={cn('flex h-10 w-10 shrink-0 items-center justify-center', config.className)}>
                    <Icon size={19} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-xs font-medium text-ink-400">{config.label}</span>
                          {!notification.read && <span className="h-1.5 w-1.5 rounded-full bg-amber-600" />}
                        </div>
                        <h2 className="mt-1 font-display text-lg font-semibold text-ink-900">{notification.title}</h2>
                      </div>
                      <time className="shrink-0 text-xs text-ink-400">{notification.createdAt}</time>
                    </div>
                    <p className="mt-2 text-sm leading-6 text-ink-600">{notification.content}</p>
                    <div className="mt-4 flex flex-wrap items-center gap-4">
                      {notification.actionPath && (
                        <button
                          type="button"
                          onClick={() => openNotification(notification.id, notification.actionPath)}
                          className="link-underline text-sm"
                        >
                          {notification.actionLabel}
                        </button>
                      )}
                      {!notification.read && (
                        <button
                          type="button"
                          onClick={() => markRead(notification.id)}
                          className="text-sm text-ink-400 transition-colors hover:text-indigo-800"
                        >
                          标记为已读
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </article>
            );
          })}
        </section>
      </div>
    </div>
  );
}
