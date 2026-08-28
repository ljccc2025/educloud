import { beforeEach, describe, expect, it, vi } from 'vitest';
import { assistantClient } from './assistantClient';
import { http } from '../../services/http';

vi.mock('../../services/http', () => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockedHttp = vi.mocked(http, true);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('assistantClient（规格 §6）', () => {
  it('chat 调用网关 /ai/chat 且逐请求放宽超时到 40s', async () => {
    mockedHttp.post.mockResolvedValue({
      data: { code: 'SUCCESS', data: {
        conversationId: '111', messageId: '222', content: '答', finishReason: 'stop',
        usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
      } },
    } as never);

    const reply = await assistantClient.chat({ conversationId: null, question: '问' });

    expect(mockedHttp.post).toHaveBeenCalledWith(
      '/ai/chat',
      { conversationId: undefined, question: '问' },
      { timeout: 40_000 },
    );
    expect(reply.content).toBe('答');
  });

  it('空响应体直接抛错，绝不构造本地假回复', async () => {
    mockedHttp.post.mockResolvedValue({ data: { code: 'SUCCESS', data: null } } as never);

    await expect(assistantClient.chat({ question: '问' }))
      .rejects.toThrow('AI 助教返回了无法识别的数据');
  });

  it('listMessages 与 deleteConversation 命中会话资源路径', async () => {
    mockedHttp.get.mockResolvedValue({ data: { code: 'SUCCESS', data: [] } } as never);
    mockedHttp.delete.mockResolvedValue({} as never);

    await assistantClient.listMessages('123');
    await assistantClient.deleteConversation('123');

    expect(mockedHttp.get).toHaveBeenCalledWith('/ai/conversations/123/messages');
    expect(mockedHttp.delete).toHaveBeenCalledWith('/ai/conversations/123');
  });
});
