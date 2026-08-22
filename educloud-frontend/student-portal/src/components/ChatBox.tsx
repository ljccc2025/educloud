import { useState, useEffect, useRef } from 'react';
import { Send } from 'lucide-react';
import type { ChatMessage } from '@/types';
import { cn } from '@/utils/cn';

interface ChatBoxProps {
  messages: ChatMessage[];
  className?: string;
}

export default function ChatBox({ messages, className }: ChatBoxProps) {
  const [input, setInput] = useState('');
  const [localMessages, setLocalMessages] = useState<ChatMessage[]>(messages);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setLocalMessages(messages);
  }, [messages]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [localMessages]);

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;
    const newMsg: ChatMessage = {
      id: Date.now(),
      userName: '林晓',
      avatar: '',
      content: text,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    };
    setLocalMessages((prev) => [...prev, newMsg]);
    setInput('');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className={cn('flex flex-col h-full min-h-0 overflow-hidden rounded-2xl bg-white dark:bg-ink-800 border border-ink-100 dark:border-ink-700 transition-colors', className)}>
      <div className="px-4 py-3 border-b border-ink-100 dark:border-ink-700 flex items-center justify-between shrink-0">
        <h3 className="font-display text-lg font-semibold text-ink-900 dark:text-white">实时讨论</h3>
        <span className="text-xs text-ink-400 dark:text-ink-300">{localMessages.length} 条消息</span>
      </div>

      <div
        ref={scrollRef}
        className="flex-1 min-h-0 overflow-y-auto overscroll-contain p-4 space-y-4 [scrollbar-width:thin] [scrollbar-color:theme(colors.ink.300)_transparent] dark:[scrollbar-color:theme(colors.ink.600)_transparent]"
      >
        {localMessages.map((msg) => {
          const isTeacher = msg.isTeacher === true;
          const isSelf = msg.userName === '林晓';
          return (
            <div key={msg.id} className={cn('flex gap-3', isSelf && 'flex-row-reverse')}>
              <div
                className={cn(
                  'w-8 h-8 flex-shrink-0 flex items-center justify-center rounded-full text-xs font-semibold',
                  isTeacher
                    ? 'bg-amber-600 text-white'
                    : isSelf
                      ? 'bg-indigo-800 text-white'
                      : 'bg-ink-100 text-ink-600'
                )}
              >
                {msg.userName.charAt(0)}
              </div>
              <div className={cn('flex-1 min-w-0', isSelf && 'text-right')}>
                <div className={cn('flex items-center gap-2 mb-1', isSelf && 'justify-end')}>
                  <span
                    className={cn(
                      'text-xs font-medium',
                      isTeacher ? 'text-amber-700 dark:text-amber-300' : 'text-ink-700 dark:text-ink-200'
                    )}
                  >
                    {msg.userName}
                  </span>
                  {isTeacher && (
                    <span className="badge-amber !px-1.5 !py-0 text-[10px]">讲师</span>
                  )}
                  <span className="text-[10px] text-ink-300 dark:text-ink-400">{msg.time}</span>
                </div>
                <p
                  className={cn(
                    'text-sm leading-relaxed inline-block rounded-2xl px-3 py-2 shadow-sm',
                    isSelf
                      ? 'bg-indigo-800 text-white'
                      : isTeacher
                        ? 'bg-amber-50 dark:bg-amber-950/40 text-ink-800 dark:text-amber-100 border border-amber-100 dark:border-amber-800'
                        : 'bg-ink-50 dark:bg-ink-700 text-ink-700 dark:text-ink-100'
                  )}
                >
                  {msg.content}
                </p>
              </div>
            </div>
          );
        })}
      </div>

      <div className="border-t border-ink-100 dark:border-ink-700 p-3 flex gap-2 shrink-0">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入消息..."
          className="flex-1 rounded-xl px-3 py-2 bg-ink-50 dark:bg-ink-700 border border-ink-200 dark:border-ink-600 text-sm text-ink-800 dark:text-ink-100 placeholder:text-ink-400 focus:outline-none focus:border-indigo-800 dark:focus:border-indigo-300 transition-colors"
        />
        <button
          type="button"
          onClick={handleSend}
          className="rounded-xl px-4 bg-indigo-800 text-white hover:bg-indigo-900 transition-colors flex items-center justify-center"
        >
          <Send size={16} />
        </button>
      </div>
    </div>
  );
}
