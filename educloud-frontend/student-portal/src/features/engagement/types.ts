export type NotificationKind = 'COURSE' | 'ASSIGNMENT' | 'EXAM' | 'LIVE' | 'SYSTEM';

export interface StudentNotification {
  id: number;
  kind: NotificationKind;
  title: string;
  content: string;
  createdAt: string;
  read: boolean;
  actionLabel?: string;
  actionPath?: string;
}
