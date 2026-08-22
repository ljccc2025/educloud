import { useMemo, useState } from 'react';
import { Bell, CheckCheck, ChevronRight } from 'lucide-react';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { teacherNotificationKindConfig } from '../features/notifications/notificationConfig';
import {
  selectUnreadCount,
  useTeacherNotificationStore,
} from '../features/notifications/useTeacherNotificationStore';
import { cn } from '../utils/cn';

type NotificationFilter = 'ALL' | 'UNREAD';

export default function Notifications() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<NotificationFilter>('ALL');
  const notifications = useTeacherNotificationStore((state) => state.notifications);
  const markRead = useTeacherNotificationStore((state) => state.markRead);
  const markAllRead = useTeacherNotificationStore((state) => state.markAllRead);
  const unreadCount = useMemo(
    () => selectUnreadCount({ notifications }),
    [notifications],
  );
  const visibleNotifications = useMemo(
    () => filter === 'UNREAD'
      ? notifications.filter((notification) => !notification.read)
      : notifications,
    [filter, notifications],
  );

  const openNotification = (id: string, actionPath?: string) => {
    markRead(id);
    if (actionPath) navigate(actionPath);
  };

  return (
    <div className="mx-auto max-w-6xl space-y-8 animate-fade-up motion-reduce:animate-none">
      <header className="flex flex-col gap-5 border-b border-ink-100 pb-7 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="section-label mb-3">教学动态</p>
          <h1 className="display-heading text-3xl md:text-4xl">通知中心</h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-ink-500">
            集中查看课程、直播、作业、考试和学员动态，不错过需要处理的教学事项。
          </p>
        </div>
        <button
          type="button"
          onClick={markAllRead}
          disabled={unreadCount === 0}
          aria-label="全部标记为已读"
          className="btn-outline min-h-11 self-start disabled:cursor-not-allowed disabled:opacity-40 sm:self-auto"
        >
          <CheckCheck className="h-4 w-4" aria-hidden="true" />
          全部已读
        </button>
      </header>

      <div className="grid gap-6 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="card-editorial h-fit p-4" aria-label="通知筛选">
          <div className="mb-4 flex items-center justify-between border-b border-ink-100 px-2 pb-4">
            <div>
              <p className="text-sm font-semibold text-ink-800">消息概览</p>
              <p className="mt-1 text-xs text-ink-400">待查看消息</p>
            </div>
            <span className="font-display text-3xl font-bold text-indigo-800">{unreadCount}</span>
          </div>
          <div className="space-y-1">
            {([
              ['ALL', '全部通知', notifications.length],
              ['UNREAD', '未读通知', unreadCount],
            ] as const).map(([value, label, count]) => (
              <button
                key={value}
                type="button"
                aria-pressed={filter === value}
                onClick={() => setFilter(value)}
                className={cn(
                  'flex min-h-11 w-full items-center justify-between rounded-xl px-3 py-2.5 text-left text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-amber-500 motion-reduce:transition-none',
                  filter === value
                    ? 'bg-indigo-50 font-medium text-indigo-800'
                    : 'text-ink-600 hover:bg-ink-50',
                )}
              >
                <span>{label}</span>
                <span className={cn(
                  'min-w-6 rounded-full px-1.5 text-center text-xs leading-5',
                  filter === value ? 'bg-white text-indigo-800' : 'bg-ink-50 text-ink-400',
                )}>
                  {count}
                </span>
              </button>
            ))}
          </div>
        </aside>

        <section className="min-w-0 space-y-3" aria-live="polite" aria-label="通知列表">
          {visibleNotifications.length === 0 ? (
            <div className="card-editorial flex min-h-72 flex-col items-center justify-center px-6 text-center">
              <span className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-700">
                <CheckCheck className="h-7 w-7" aria-hidden="true" />
              </span>
              <h2 className="font-display text-xl font-semibold text-ink-900">
                {filter === 'UNREAD' ? '暂无未读通知' : '暂无通知'}
              </h2>
              <p className="mt-2 text-sm text-ink-400">
                {filter === 'UNREAD'
                  ? '新的教学动态会及时出现在这里'
                  : '新的教学通知会及时出现在这里'}
              </p>
            </div>
          ) : visibleNotifications.map((notification) => {
            const config = teacherNotificationKindConfig[notification.kind];
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
                  <span className={cn(
                    'flex h-11 w-11 shrink-0 items-center justify-center rounded-xl',
                    config.className,
                  )}>
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-xs font-medium text-ink-400">{config.label}</span>
                          {!notification.read && (
                            <span className="inline-flex items-center gap-1.5 text-xs font-medium text-amber-700">
                              <span className="h-1.5 w-1.5 rounded-full bg-amber-600" aria-hidden="true" />
                              未读
                            </span>
                          )}
                        </div>
                        <h2 className="mt-1 font-display text-lg font-semibold text-ink-900">
                          {notification.title}
                        </h2>
                      </div>
                      <time
                        className="shrink-0 text-xs text-ink-400"
                        dateTime={notification.createdAt}
                      >
                        {dayjs(notification.createdAt).format('YYYY年MM月DD日 HH:mm')}
                      </time>
                    </div>

                    <p className="mt-2 text-sm leading-6 text-ink-600">{notification.content}</p>

                    <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-3">
                      {notification.actionPath && notification.actionLabel && (
                        <button
                          type="button"
                          onClick={() => openNotification(notification.id, notification.actionPath)}
                          className="inline-flex min-h-10 items-center gap-1 rounded-lg text-sm font-medium text-indigo-800 transition-colors hover:text-indigo-600 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 motion-reduce:transition-none"
                        >
                          {notification.actionLabel}
                          <ChevronRight className="h-4 w-4" aria-hidden="true" />
                        </button>
                      )}
                      {!notification.read && (
                        <button
                          type="button"
                          aria-label={`标记「${notification.title}」为已读`}
                          onClick={() => markRead(notification.id)}
                          className="min-h-10 rounded-lg text-sm text-ink-400 transition-colors hover:text-indigo-800 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 motion-reduce:transition-none"
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

      <p className="flex items-center gap-2 text-xs text-ink-400">
        <Bell className="h-3.5 w-3.5" aria-hidden="true" />
        当前消息保存在此浏览器中；接入教师端后端后可同步真实业务通知。
      </p>
    </div>
  );
}
