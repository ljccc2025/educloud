import { useEffect, useRef, useState } from 'react';
import {
  Bot,
  BookOpenCheck,
  BrainCircuit,
  Code2,
  Eraser,
  Send,
  Sparkles,
  UserRound,
} from 'lucide-react';
import { assistantClient } from '../features/engagement/assistantClient';
import type { AssistantMessage } from '../features/engagement/types';

const welcomeMessage: AssistantMessage = {
  id: 'assistant-welcome',
  role: 'assistant',
  content: '你好，我是 EduCloud AI 助教。你可以向我咨询课程知识、编程问题或复习计划，我会尽量把问题拆解成清晰的学习步骤。',
  createdAt: '现在',
};

const quickQuestions = [
  { icon: BookOpenCheck, title: '制定复习计划', prompt: '请帮我制定一份一周的期末复习计划' },
  { icon: BrainCircuit, title: '解释知识点', prompt: '请用简单的例子解释导数的几何意义' },
  { icon: Code2, title: '分析编程问题', prompt: '遇到 React 状态没有及时更新时应该如何排查' },
];

let messageSequence = 0;
const createMessage = (role: AssistantMessage['role'], content: string): AssistantMessage => ({
  id: `${role}-${Date.now()}-${messageSequence += 1}`,
  role,
  content,
  createdAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
});

export default function AiAssistant() {
  const [messages, setMessages] = useState<AssistantMessage[]>([welcomeMessage]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const messageListRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const messageList = messageListRef.current;
    if (messageList) messageList.scrollTop = messageList.scrollHeight;
  }, [loading, messages]);

  const sendQuestion = async (preset?: string) => {
    const question = (preset ?? input).trim();
    if (!question || loading) return;

    setMessages((current) => [...current, createMessage('student', question)]);
    setInput('');
    setError(null);
    setLoading(true);

    try {
      const reply = await assistantClient.ask(question);
      setMessages((current) => [...current, createMessage('assistant', reply.content)]);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'AI 助教暂时无法回答，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const clearConversation = () => {
    if (loading) return;
    setMessages([welcomeMessage]);
    setError(null);
    setInput('');
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-10 pt-6 md:px-8 animate-fade-up">
      <div className="flex flex-col gap-4 border-b border-ink-100 pb-8 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="section-label">智能学习支持</p>
          <div className="flex flex-wrap items-center gap-3 pt-6">
            <h1 className="display-heading text-4xl md:text-5xl">AI 助教</h1>
            <span className="badge-indigo normal-case tracking-normal">
              {assistantClient.mode === 'remote' ? '已连接服务' : '演示模式'}
            </span>
          </div>
          <p className="mt-3 text-sm text-ink-500">随时提问，把复杂知识拆成可执行的学习步骤</p>
        </div>
        <button type="button" onClick={clearConversation} disabled={loading} className="btn-outline self-start disabled:opacity-45 md:self-auto">
          <Eraser size={17} /> 清空会话
        </button>
      </div>

      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_19rem]">
        <section className="card-editorial flex h-[36rem] max-h-[calc(100dvh-8rem)] min-h-[28rem] min-w-0 flex-col overflow-hidden lg:h-[calc(100dvh-18rem)] lg:max-h-[42rem] lg:min-h-[24rem]">
          <div className="flex shrink-0 items-center gap-3 border-b border-ink-100 px-5 py-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-800 text-white">
              <Bot size={18} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-ink-900">学习对话</h2>
              <p className="text-xs text-ink-400">回答仅用于辅助学习，请结合课程资料判断</p>
            </div>
          </div>

          <div ref={messageListRef} className="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-6 sm:px-6" aria-live="polite">
            {messages.map((message) => {
              const assistant = message.role === 'assistant';
              return (
                <div key={message.id} className={`flex gap-3 ${assistant ? '' : 'flex-row-reverse'}`} data-message-role={message.role}>
                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${assistant ? 'bg-indigo-800 text-white' : 'bg-amber-100 text-amber-800'}`}>
                    {assistant ? <Sparkles size={17} /> : <UserRound size={17} />}
                  </div>
                  <div className={`max-w-[85%] ${assistant ? '' : 'text-right'}`}>
                    <div className={`inline-block rounded-2xl px-4 py-3 text-left text-sm leading-6 shadow-sm ${assistant ? 'bg-ink-50 text-ink-700' : 'bg-indigo-800 text-white'}`}>
                      {message.content}
                    </div>
                    <p className="mt-1 text-xs text-ink-300">{message.createdAt}</p>
                  </div>
                </div>
              );
            })}
            {loading && (
              <div className="flex gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-800 text-white"><Sparkles size={17} /></div>
                <div className="flex items-center gap-1 rounded-2xl bg-ink-50 px-4 py-4 shadow-sm" aria-label="AI 助教正在思考">
                  {[0, 1, 2].map((dot) => <span key={dot} className="h-1.5 w-1.5 animate-pulse rounded-full bg-indigo-500" style={{ animationDelay: `${dot * 120}ms` }} />)}
                </div>
              </div>
            )}
          </div>

          <div className="shrink-0 border-t border-ink-100 bg-white p-4 sm:p-5">
            {error && <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
            <div className="flex items-end gap-3">
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void sendQuestion();
                  }
                }}
                rows={2}
                maxLength={1000}
                placeholder="输入你的学习问题..."
                className="input-field min-h-[3.25rem] resize-none rounded-xl"
              />
              <button
                type="button"
                onClick={() => void sendQuestion()}
                disabled={!input.trim() || loading}
                aria-label="发送问题"
                className="btn-primary h-[3.25rem] shrink-0 px-4 disabled:cursor-not-allowed disabled:opacity-45 sm:px-6"
              >
                <Send size={17} /> <span className="hidden sm:inline">发送</span>
              </button>
            </div>
          </div>
        </section>

        <aside className="space-y-4">
          <div className="card-editorial p-5">
            <h2 className="font-display text-lg font-semibold text-ink-900">常用提问</h2>
            <p className="mt-1 text-xs leading-5 text-ink-400">选择一个场景快速开始</p>
            <div className="mt-4 space-y-2">
              {quickQuestions.map((question) => (
                <button
                  key={question.title}
                  type="button"
                  onClick={() => void sendQuestion(question.prompt)}
                  disabled={loading}
                  className="flex w-full items-start gap-3 rounded-xl border border-ink-100 p-3 text-left transition-colors hover:border-indigo-200 hover:bg-indigo-50/60 disabled:opacity-45"
                >
                  <question.icon className="mt-0.5 shrink-0 text-indigo-700" size={17} />
                  <span>
                    <span className="block text-sm font-medium text-ink-800">{question.title}</span>
                    <span className="mt-1 block text-xs leading-5 text-ink-400">{question.prompt}</span>
                  </span>
                </button>
              ))}
            </div>
          </div>
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5">
            <h3 className="text-sm font-semibold text-amber-900">提问建议</h3>
            <p className="mt-2 text-xs leading-5 text-amber-800/80">说明课程、当前进度和具体困难，助教给出的步骤会更有针对性。</p>
          </div>
        </aside>
      </div>
    </div>
  );
}
