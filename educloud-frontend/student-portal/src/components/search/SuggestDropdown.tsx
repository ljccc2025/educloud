import React from 'react';
import { Search, BookOpen, Sparkles, ArrowRight, Loader2 } from 'lucide-react';
import type { SuggestItem } from '@/services/searchApi';
import { cn } from '@/utils/cn';

interface SuggestDropdownProps {
  visible: boolean;
  loading?: boolean;
  suggestions: SuggestItem[];
  activeIndex?: number;
  onSelect: (item: SuggestItem) => void;
  className?: string;
}

export default function SuggestDropdown({
  visible,
  loading = false,
  suggestions,
  activeIndex = -1,
  onSelect,
  className,
}: SuggestDropdownProps) {
  if (!visible) {
    return null;
  }

  return (
    <div
      className={cn(
        'absolute left-0 right-0 top-full mt-2 z-50 overflow-hidden',
        'bg-white/95 dark:bg-ink-900/95 backdrop-blur-xl',
        'border border-ink-200/80 dark:border-ink-700/70',
        'rounded-2xl shadow-xl shadow-ink-900/10 dark:shadow-black/30',
        'animate-fade-in transition-all',
        className,
      )}
      style={{ maxHeight: '380px' }}
      onMouseDown={(e) => {
        // 防止点下拉框时触发 input blur
        e.preventDefault();
      }}
    >
      {loading ? (
        <div className="flex items-center justify-center py-6 gap-2.5 text-xs text-ink-500 dark:text-ink-400">
          <Loader2 size={16} className="animate-spin text-indigo-800 dark:text-indigo-400" />
          <span>正在智能联想...</span>
        </div>
      ) : suggestions.length === 0 ? (
        <div className="py-5 text-center text-xs text-ink-400 dark:text-ink-500">
          暂无匹配的课程或推荐词
        </div>
      ) : (
        <div className="py-2 overflow-y-auto max-h-[360px]">
          <div className="px-3.5 py-1.5 text-[11px] font-semibold tracking-wider text-ink-400 dark:text-ink-500 uppercase flex items-center justify-between">
            <span>智能联想推荐</span>
            <span className="text-[10px] font-normal lowercase opacity-75">↑↓ 键切换 · 回车选择</span>
          </div>

          <div className="space-y-0.5 px-1.5">
            {suggestions.map((item, idx) => {
              const isSelected = activeIndex === idx;
              const isCourse = item.type === 'COURSE';

              return (
                <button
                  key={`${item.type}-${item.targetId ?? item.text}-${idx}`}
                  type="button"
                  onClick={() => onSelect(item)}
                  className={cn(
                    'w-full flex items-center justify-between gap-3 px-3 py-2.5 text-left rounded-xl text-xs transition-colors group',
                    isSelected
                      ? 'bg-indigo-50 dark:bg-indigo-950/60 text-indigo-900 dark:text-indigo-200 font-medium'
                      : 'text-ink-700 dark:text-ink-200 hover:bg-ink-100/70 dark:hover:bg-ink-800/60',
                  )}
                >
                  <div className="flex items-center gap-2.5 min-w-0 flex-1">
                    {/* Type Icon */}
                    <div
                      className={cn(
                        'flex items-center justify-center w-6 h-6 rounded-lg shrink-0 text-xs transition-colors',
                        isCourse
                          ? 'bg-indigo-100/80 dark:bg-indigo-900/50 text-indigo-700 dark:text-indigo-300'
                          : 'bg-amber-100/80 dark:bg-amber-900/50 text-amber-700 dark:text-amber-300',
                      )}
                    >
                      {isCourse ? <BookOpen size={13} /> : <Search size={13} />}
                    </div>

                    {/* Text with Highlight */}
                    <div className="truncate flex-1">
                      {item.highlight ? (
                        <span
                          className="truncate inline-block max-w-full"
                          dangerouslySetInnerHTML={{ __html: item.highlight }}
                        />
                      ) : (
                        <span className="truncate">{item.text}</span>
                      )}
                    </div>
                  </div>

                  {/* Right Meta (Category badge & Type Tag) */}
                  <div className="flex items-center gap-1.5 shrink-0">
                    {item.category && (
                      <span className="text-[10px] font-normal text-ink-500 dark:text-ink-400 bg-ink-100 dark:bg-ink-800 px-1.5 py-0.5 rounded">
                        {item.category}
                      </span>
                    )}

                    <span
                      className={cn(
                        'text-[10px] font-medium px-1.5 py-0.5 rounded flex items-center gap-1',
                        isCourse
                          ? 'bg-indigo-50 dark:bg-indigo-950/80 text-indigo-700 dark:text-indigo-300 border border-indigo-200/50 dark:border-indigo-800/50'
                          : 'bg-ink-50 dark:bg-ink-800 text-ink-500 dark:text-ink-400',
                      )}
                    >
                      {isCourse ? '直达课程' : '搜索词'}
                      <ArrowRight size={10} className="opacity-0 group-hover:opacity-100 transition-opacity" />
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
