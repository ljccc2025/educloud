import { useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/utils/cn';

export interface CourseSortOption<T extends string> {
  value: T;
  label: string;
}

interface CourseSortSelectProps<T extends string> {
  value: T;
  options: readonly CourseSortOption<T>[];
  onChange: (value: T) => void;
  placeholder?: string;
}

export default function CourseSortSelect<T extends string>({
  value,
  options,
  onChange,
  placeholder = '请选择排序',
}: CourseSortSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();

  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined;
  const label = selected?.label ?? options[0]?.label ?? placeholder;

  useEffect(() => {
    if (!open) return;

    const handleOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [open]);

  useEffect(() => {
    if (!open || !listRef.current) return;
    const option = listRef.current.children[highlightIndex] as HTMLElement | undefined;
    option?.scrollIntoView({ block: 'nearest' });
  }, [highlightIndex, open]);

  const choose = (option: CourseSortOption<T>) => {
    onChange(option.value);
    setOpen(false);
  };

  const openListbox = () => {
    setHighlightIndex(selectedIndex >= 0 ? selectedIndex : 0);
    setOpen(true);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (!open) {
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
        event.preventDefault();
        openListbox();
      }
      return;
    }

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (options.length > 0) {
          setHighlightIndex((index) => Math.min(index + 1, options.length - 1));
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (options.length > 0) {
          setHighlightIndex((index) => Math.max(index - 1, 0));
        }
        break;
      case 'Enter':
        event.preventDefault();
        if (options[highlightIndex]) choose(options[highlightIndex]);
        break;
      case 'Escape':
        event.preventDefault();
        setOpen(false);
        break;
    }
  };

  return (
    <div
      ref={containerRef}
      className="relative w-full shrink-0 sm:w-52"
      onKeyDown={handleKeyDown}
    >
      <button
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        onClick={() => {
          if (open) setOpen(false);
          else openListbox();
        }}
        className={cn(
          'flex min-h-[50px] w-full items-center justify-between gap-3 bg-white px-4 py-3 text-left',
          'rounded-xl border text-sm font-medium text-ink-700 transition-all duration-200',
          'focus:outline-none focus:ring-1 focus:ring-indigo-800',
          open
            ? 'border-indigo-800 ring-1 ring-indigo-800 shadow-sm'
            : 'border-ink-200 hover:border-ink-300',
        )}
      >
        <span className="truncate">{label}</span>
        <ChevronDown
          className={cn(
            'h-4 w-4 shrink-0 text-ink-400 transition-transform duration-200',
            open && 'rotate-180',
          )}
        />
      </button>

      {open && (
        <div
          className={cn(
            'absolute right-0 z-40 mt-2 w-full min-w-[13rem] overflow-hidden',
            'rounded-2xl border border-ink-100 bg-white p-2',
            'shadow-2xl shadow-ink-900/10 animate-fade-in',
          )}
        >
          <div ref={listRef} id={listboxId} role="listbox">
            {options.map((option, index) => {
              const isSelected = option.value === value;
              const isHighlighted = index === highlightIndex;

              return (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => choose(option)}
                  onMouseEnter={() => setHighlightIndex(index)}
                  className={cn(
                    'flex min-h-10 w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5',
                    'text-left text-sm transition-colors',
                    isHighlighted
                      ? 'bg-indigo-50/60 text-indigo-800'
                      : 'text-ink-600 hover:bg-ink-50',
                    isSelected && 'bg-indigo-50 font-semibold text-indigo-800',
                  )}
                >
                  <span>{option.label}</span>
                  {isSelected ? (
                    <Check className="h-4 w-4 shrink-0 text-indigo-800" aria-hidden="true" />
                  ) : (
                    <span className="h-4 w-4 shrink-0" aria-hidden="true" />
                  )}
                </button>
              );
            })}

            {options.length === 0 && (
              <p className="px-3 py-5 text-center text-sm text-ink-400">
                暂无排序选项
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
