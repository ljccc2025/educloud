import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Check, type LucideIcon } from 'lucide-react';
import { cn } from '../utils/cn';

export interface SelectOption {
  value: string;
  label: string;
  badge?: string;
  icon?: LucideIcon;
  image?: string;
  subtitle?: string;
}

interface CustomSelectProps {
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  prefixIcon?: LucideIcon;
  minWidth?: string;
  disabled?: boolean;
}

export default function CustomSelect({
  options,
  value,
  onChange,
  placeholder = '请选择',
  className,
  prefixIcon: PrefixIcon,
  minWidth = 'min-w-[140px]',
  disabled = false,
}: CustomSelectProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.value === value);

  // Close on click outside
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div ref={containerRef} className={cn('relative inline-block text-left', minWidth, className)}>
      {/* Trigger */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => !disabled && setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={cn(
          'w-full flex items-center justify-between gap-2 px-3.5 py-2.5 bg-white',
          'border text-xs md:text-sm font-medium text-ink-800',
          'rounded-xl transition-all duration-200 cursor-pointer shadow-sm',
          'focus:outline-none focus:ring-2 focus:ring-indigo-600/20 focus:border-indigo-600',
          disabled && 'opacity-60 cursor-not-allowed bg-ink-50',
          open
            ? 'border-indigo-600 ring-2 ring-indigo-600/20 shadow-md'
            : 'border-ink-200 hover:border-ink-300',
        )}
      >
        <div className="flex items-center gap-2 truncate">
          {PrefixIcon && <PrefixIcon size={16} className="text-ink-400 shrink-0" />}
          {selected?.image && (
            <img src={selected.image} alt="" className="w-5 h-5 rounded object-cover shrink-0" />
          )}
          <span className={cn('truncate', !selected && 'text-ink-400')}>
            {selected ? selected.label : placeholder}
          </span>
        </div>
        <ChevronDown
          size={14}
          className={cn(
            'text-ink-400 shrink-0 transition-transform duration-200',
            open && 'rotate-180 text-indigo-600',
          )}
        />
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          className={cn(
            'absolute z-50 mt-1.5 w-full min-w-[200px] bg-white rounded-xl',
            'border border-ink-100 shadow-xl shadow-ink-900/10 overflow-hidden',
            'dropdown-animate animate-fade-in',
          )}
        >
          <div ref={listRef} className="max-h-72 overflow-y-auto py-1.5" role="listbox">
            {options.map((opt) => {
              const isSelected = opt.value === value;
              const OptIcon = opt.icon;
              return (
                <button
                  key={opt.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => {
                    onChange(opt.value);
                    setOpen(false);
                  }}
                  className={cn(
                    'w-full flex items-center justify-between px-3.5 py-2.5 text-xs md:text-sm text-left transition-colors',
                    'hover:bg-indigo-50/70',
                    isSelected
                      ? 'bg-indigo-50 font-semibold text-indigo-700'
                      : 'font-medium text-ink-700',
                  )}
                >
                  <div className="flex items-center gap-2.5 truncate">
                    {OptIcon && <OptIcon size={14} className="shrink-0 text-ink-400" />}
                    {opt.image && (
                      <img src={opt.image} alt="" className="w-6 h-6 rounded object-cover shrink-0 bg-ink-100" />
                    )}
                    <div className="truncate">
                      <div className="truncate">{opt.label}</div>
                      {opt.subtitle && <div className="text-[11px] text-ink-400 font-normal truncate">{opt.subtitle}</div>}
                    </div>
                    {opt.badge && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-ink-100 text-ink-500 shrink-0">
                        {opt.badge}
                      </span>
                    )}
                  </div>
                  {isSelected && (
                    <Check size={14} className="text-indigo-600 shrink-0 ml-2" />
                  )}
                </button>
              );
            })}
            {options.length === 0 && (
              <div className="px-3.5 py-3 text-center text-xs text-ink-400">
                暂无选项
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
