import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiAssistant from './AiAssistant';
import { assistantClient } from '../features/engagement/assistantClient';

vi.mock('../features/engagement/assistantClient', () => ({
  assistantClient: {
    chat: vi.fn(),
    listConversations: vi.fn(),
    listMessages: vi.fn(),
    deleteConversation: vi.fn(),
  },
}));

const mockedClient = vi.mocked(assistantClient);

function httpError(status: number, code: string): Error & { code: string } {
  const error = new Error(`HTTP ${status}`) as Error & { code: string };
  error.code = code;
  return error;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedClient.listConversations.mockResolvedValue({
    items: [], page: 1, pageSize: 50, total: 0, totalPages: 0,
  });
});

describe('AiAssistant 错误态（规格 §6：绝不回退假文案）', () => {
  it('429 配额超限展示当日次数用完提示且无假回复', async () => {
    mockedClient.chat.mockRejectedValue(httpError(429, 'AI_QUOTA_EXCEEDED'));
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '如何制定复习计划');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    expect(await screen.findByText('今日提问次数已用完，明天再来')).toBeInTheDocument();
    expect(screen.queryByText(/复习高等数学可以按/)).not.toBeInTheDocument();
    expect(screen.queryByText(/建议先把问题缩小到一个可以运行的最小示例/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试/ })).not.toBeInTheDocument();
  });

  it('503 展示可操作重试且重试会再次调用', async () => {
    mockedClient.chat.mockRejectedValueOnce(httpError(503, 'AI_PROVIDER_UNAVAILABLE'));
    mockedClient.chat.mockResolvedValueOnce({
      conversationId: '111', messageId: '222', content: '好的', finishReason: 'stop',
      usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
    });
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '解释导数');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    const retry = await screen.findByRole('button', { name: /重试/ });
    await user.click(retry);

    await waitFor(() => expect(mockedClient.chat).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('好的')).toBeInTheDocument();
  });

  it('请求期间输入与发送禁用', async () => {
    let resolveChat: (value: Awaited<ReturnType<typeof assistantClient.chat>>) => void = () => {};
    mockedClient.chat.mockImplementation(() => new Promise((resolve) => { resolveChat = resolve; }));
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '问');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    expect(screen.getByPlaceholderText('AI 助教正在思考…')).toBeDisabled();
    expect(screen.getByRole('button', { name: '发送问题' })).toBeDisabled();

    resolveChat({
      conversationId: '111', messageId: '222', content: '回答', finishReason: 'stop',
      usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
    });
    await waitFor(() => expect(screen.getByText('回答')).toBeInTheDocument());
    expect(screen.getByPlaceholderText('输入你的学习问题...')).toBeEnabled();
  });

  it('加粗渲染：**关键词** 输出 strong 元素且不显示星号', async () => {
    mockedClient.chat.mockResolvedValue({
      conversationId: '111', messageId: '222',
      content: '第一步，**明确极限的定义**，再逐步计算。',
      finishReason: 'stop',
      usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
    });
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '解释极限');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    const strong = await screen.findByText('明确极限的定义');
    // findByText 返回文本所在的最内层元素，即 <strong> 本身
    expect(strong.tagName).toBe('STRONG');
    expect(screen.queryByText(/\*\*/)).not.toBeInTheDocument();
  });

  it('TRUNCATED 回答显示截断提示', async () => {
    mockedClient.chat.mockResolvedValue({
      conversationId: '111', messageId: '222', content: '部分答案', finishReason: 'length',
      usage: { promptTokens: 1, completionTokens: 64, totalTokens: 65 }, degraded: false,
    });
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '长问题');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    expect(await screen.findByText('回答被截断，可追问"继续"获取剩余内容')).toBeInTheDocument();
  });
});
