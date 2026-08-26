export type NotificationKind = 'COURSE' | 'ASSIGNMENT' | 'EXAM' | 'LIVE' | 'SYSTEM' | 'PAYMENT';

export interface StudentNotification {
  /** Snowflake ID（字符串，避免 JS Number 精度丢失） */
  id: string;
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

export interface CommunityReply {
  id: number;
  author: string;
  avatar: string;
  content: string;
  createdAt: string;
}

export interface CommunityPost {
  id: number;
  title: string;
  content: string;
  author: string;
  avatar: string;
  courseName: string;
  tags: string[];
  createdAt: string;
  likes: number;
  liked: boolean;
  bookmarked: boolean;
  replies: CommunityReply[];
}
