export type TeacherNotificationKind =
  | 'ASSIGNMENT'
  | 'LIVE'
  | 'STUDENT'
  | 'EXAM'
  | 'COURSE'
  | 'SYSTEM';

export interface TeacherNotification {
  id: string;
  kind: TeacherNotificationKind;
  title: string;
  content: string;
  createdAt: string;
  read: boolean;
  actionLabel?: string;
  actionPath?: string;
}
