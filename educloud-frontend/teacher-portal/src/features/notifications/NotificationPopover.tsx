import { useEffect, useMemo, useRef, useState } from 'react';
import { Bell, CheckCheck, ChevronRight } from 'lucide-react';
import dayjs from 'dayjs';
import { useLocation, useNavigate } from 'react-router-dom';
import { cn } from '../../utils/cn';
import { teacherNotificationKindConfig } from './notificationConfig';
import {
  selectUnreadCount,
  useTeacherNotificationStore,
} from './useTeacherNotificationStore';

const PANEL_ID = 'teacher-notification-popover';

export default function NotificationPopover() {
  const navigate = useNavigate();
  const location = useLocation();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const [open, setOpen] = useState(false);
  const notifications = useTeacherNotificationStore((state) => state.notifications);
  const markRead = useTeacherNotificationStore((state) => state.markRead);
  const markAllRead = useTeacherNotificationStore((state) => state.markAllRead);
  const unreadCount = useMemo(
    () => selectUnreadCount({ notifications }),
    [notifications],
  );
  const recentNotifications = notifications.slice(0, 5);

  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!open) return undefined;

    panelRef.current?.focus();

    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setOpen(false);
        triggerRef.current?.focus();
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const openNotification = (id: string, actionPath?: string) => {
    markRead(id);
    setOpen(false);
    if (actionPath) navigate(actionPath);
  };

  const openAllNotifications = () => {
    setOpen(false);
    navigate('/notifications');
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-label={unreadCount > 0
          ? `通知中心，${unreadCount} 条未读消息`
          : '通知中心，没有未读消息'}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={PANEL_ID}
        onClick={() => setOpen((value) => !value)}
        className="relative rounded-lg p-2 text-ink-500 transition-colors hover:bg-indigo-50 hover:text-indigo-800 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 motion-reduce:transition-none"
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {unreadCount > 0 && (
          <span
            aria-hidden="true"
            className="absolute -right-1 -top-1 min-w-4 rounded-full bg-amber-600 px-1 text-center text-[10px] font-semibold leading-4 text-white"
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <section
          ref={panelRef}
          id={PANEL_ID}
          role="dialog"
          aria-label="最近通知"
          tabIndex={-1}
          className="fixed left-4 right-4 top-[4.25rem] z-50 overflow-hidden rounded-2xl border border-ink-100 bg-white shadow-2xl shadow-ink-900/15 outline-none animate-fade-in motion-reduce:animate-none sm:absolute sm:left-auto sm:right-0 sm:top-full sm:mt-3 sm:w-[calc(100vw-2rem)] sm:max-w-sm"
        >
          <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
            <div>
              <h2 className="font-display text-lg font-bold text-ink-900">消息通知</h2>
              <p className="mt-0.5 text-xs text-ink-400">
                {unreadCount > 0 ? `${unreadCount} 条消息尚未查看` : '所有消息均已查看'}
              </p>
            </div>
            <button
              type="button"
              onClick={markAllRead}
              disabled={unreadCount === 0}
              aria-label="全部标记为已读"
              className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-xs font-medium text-indigo-800 transition-colors hover:bg-indigo-50 focus:outline-none focus:ring-2 focus:ring-amber-500 disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
            >
              <CheckCheck className="h-4 w-4" aria-hidden="true" />
              全部已读
            </button>
          </div>

          <div className="max-h-[min(28rem,calc(100vh-10rem))] overflow-y-auto">
            {recentNotifications.map((notification) => {
              const config = teacherNotificationKindConfig[notification.kind];
              const Icon = config.icon;
              return (
                <button
                  key={notification.id}
                  type="button"
                  onClick={() => openNotification(notification.id, notification.actionPath)}
                  className={cn(
                    'group flex w-full gap-3 border-b border-ink-100 px-5 py-4 text-left transition-colors last:border-b-0 hover:bg-ink-50 focus:outline-none focus-visible:bg-indigo-50 motion-reduce:transition-none',
                    !notification.read && 'bg-amber-50/40',
                  )}
                >
                  <span className={cn(
                    'flex h-9 w-9 shrink-0 items-center justify-center rounded-xl',
                    config.className,
                  )}>
                    <Icon className="h-4 w-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-start gap-2">
                      <span className={cn(
                        'min-w-0 flex-1 truncate text-sm text-ink-800',
                        !notification.read && 'font-semibold text-ink-900',
                      )}>
                        {notification.title}
                      </span>
                      {!notification.read && (
                        <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-600" aria-label="未读" />
                      )}
                    </span>
                    <span className="mt-1 block line-clamp-2 text-xs leading-5 text-ink-500">
                      {notification.content}
                    </span>
                    <span className="mt-2 flex items-center justify-between gap-3">
                      <time className="text-[11px] text-ink-400" dateTime={notification.createdAt}>
                        {dayjs(notification.createdAt).format('MM月DD日 HH:mm')}
                      </time>
                      {notification.actionLabel && (
                        <span className="inline-flex items-center text-[11px] font-medium text-indigo-800">
                          {notification.actionLabel}
                          <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
                        </span>
                      )}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>

          <div className="border-t border-ink-100 bg-ink-50/50 p-3">
            <button
              type="button"
              onClick={openAllNotifications}
              className="flex w-full items-center justify-center gap-1 rounded-lg py-2 text-sm font-medium text-indigo-800 transition-colors hover:bg-white focus:outline-none focus:ring-2 focus:ring-amber-500 motion-reduce:transition-none"
            >
              查看全部通知
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </section>
      )}
    </div>
  );
}
