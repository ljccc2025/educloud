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

export interface AssistantMessage {
  id: string;
  role: 'student' | 'assistant';
  content: string;
  createdAt: string;
}

export interface AssistantReply {
  content: string;
  mode: 'mock' | 'remote';
}
