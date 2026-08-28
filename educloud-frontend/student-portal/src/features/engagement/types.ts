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
  /** assistant 消息的落库状态；TRUNCATED 时前端提示"回答被截断，可追问继续" */
  status?: 'OK' | 'TRUNCATED';
}

/** /api/v1/ai/chat 响应（雪花 ID 已字符串化） */
export interface AiChatResponse {
  conversationId: string;
  messageId: string;
  content: string;
  finishReason: 'stop' | 'length' | string;
  usage: { promptTokens: number; completionTokens: number; totalTokens: number };
  degraded: boolean;
}

export interface AiConversationSummary {
  id: string;
  title: string;
  messageCount: number;
  lastMessageAt: string;
  createdAt: string;
}

export interface AiConversationMessage {
  id: string;
  role: 'student' | 'assistant';
  content: string;
  status: 'OK' | 'TRUNCATED';
  createdAt: string;
}

export interface AiPage<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
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
