import { http, type ApiEnvelope } from '../../services/http';
import type {
  AiChatResponse,
  AiConversationMessage,
  AiConversationSummary,
  AiPage,
} from './types';

/**
 * AI 助教真实客户端（规格 §6）：走网关 /api/v1/ai/**，JWT 由 http 拦截器注入。
 * chat 读超时 25s、网关该路由 35s，axios 默认 15s 不够用，逐请求放宽到 40s。
 * 不做任何本地降级/假回复：失败就把带 code 的错误抛给页面渲染错误态。
 */
const CHAT_TIMEOUT_MS = 40_000;

function requireData<T>(data: T | undefined | null, errorMessage: string): T {
  if (data === undefined || data === null) {
    throw new Error(errorMessage);
  }
  return data;
}

export const assistantClient = {
  async chat(payload: { conversationId?: string | null; question: string }): Promise<AiChatResponse> {
    const resp = await http.post<ApiEnvelope<AiChatResponse>>(
      '/ai/chat',
      { conversationId: payload.conversationId ?? undefined, question: payload.question },
      { timeout: CHAT_TIMEOUT_MS },
    );
    return requireData(resp.data?.data, 'AI 助教返回了无法识别的数据');
  },

  async listConversations(page = 1, size = 50): Promise<AiPage<AiConversationSummary>> {
    const resp = await http.get<ApiEnvelope<AiPage<AiConversationSummary>>>('/ai/conversations', {
      params: { page, size },
    });
    return requireData(resp.data?.data, '会话列表返回了无法识别的数据');
  },

  async listMessages(conversationId: string): Promise<AiConversationMessage[]> {
    const resp = await http.get<ApiEnvelope<AiConversationMessage[]>>(
      `/ai/conversations/${conversationId}/messages`,
    );
    return requireData(resp.data?.data, '会话消息返回了无法识别的数据');
  },

  async deleteConversation(conversationId: string): Promise<void> {
    await http.delete(`/ai/conversations/${conversationId}`);
  },
};
