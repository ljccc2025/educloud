import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Check, BookOpen } from 'lucide-react';
import type { Course } from '../types';
import { cn } from '../utils/cn';

interface CourseSelectProps {
  courses: Course[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
}

export default function CourseSelect({
  courses,
  value,
  onChange,
  placeholder = '请选择课程',
}: CourseSelectProps) {
  const [open, setOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const selected = courses.find((c) => c.id === value);

  // Close on outside click
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

  // Reset highlight when opening
  useEffect(() => {
    if (open) {
      const idx = courses.findIndex((c) => c.id === value);
      setHighlightIndex(idx >= 0 ? idx : 0);
    }
  }, [open, courses, value]);

  // Scroll highlighted option into view
  useEffect(() => {
    if (!open || !listRef.current) return;
    const el = listRef.current.children[highlightIndex] as HTMLElement | undefined;
    el?.scrollIntoView({ block: 'nearest' });
  }, [highlightIndex, open]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
        e.preventDefault();
        setOpen(true);
      }
      return;
    }
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setHighlightIndex((i) => Math.min(i + 1, courses.length - 1));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightIndex((i) => Math.max(i - 1, 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (courses[highlightIndex]) {
          onChange(courses[highlightIndex].id);
          setOpen(false);
        }
        break;
      case 'Escape':
        e.preventDefault();
        setOpen(false);
        break;
    }
  };

  const getSubtitle = (c: Course) => {
    const cwCount = c.chapters.reduce((acc, ch) => acc + ch.coursewares.length, 0);
    return `${c.chapters.length} 章节 · ${cwCount} 课件`;
  };

  return (
    <div ref={containerRef} className="relative" onKeyDown={handleKeyDown}>
      {/* Trigger */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={cn(
          'w-full flex items-center gap-3 px-4 py-3 bg-white border text-left',
          'rounded-xl transition-all duration-200 cursor-pointer',
          'focus:outline-none focus:ring-1 focus:ring-indigo-800',
          open
            ? 'border-indigo-800 ring-1 ring-indigo-800 shadow-sm'
            : 'border-ink-200 hover:border-ink-300',
        )}
      >
        {selected ? (
          <>
            <img
              src={selected.cover}
              alt=""
              className="w-10 h-10 rounded-lg object-cover bg-ink-100 shrink-0"
            />
            <div className="flex-1 min-w-0">
              <p className="font-display text-base font-semibold text-ink-900 truncate">
                {selected.title}
              </p>
              <p className="text-xs text-ink-400 mt-0.5">{getSubtitle(selected)}</p>
            </div>
          </>
        ) : (
          <div className="flex items-center gap-3 flex-1">
            <div className="w-10 h-10 rounded-lg bg-ink-50 flex items-center justify-center shrink-0">
              <BookOpen className="w-5 h-5 text-ink-300" />
            </div>
            <span className="text-ink-400 text-sm">{placeholder}</span>
          </div>
        )}
        <ChevronDown
          className={cn(
            'w-5 h-5 text-ink-400 shrink-0 transition-transform duration-200',
            open && 'rotate-180',
          )}
        />
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          className={cn(
            'absolute z-50 mt-2 w-full bg-white rounded-2xl border border-ink-100',
            'shadow-2xl shadow-ink-900/10 overflow-hidden dropdown-animate',
          )}
        >
          <div ref={listRef} className="max-h-80 overflow-y-auto py-2" role="listbox">
            {courses.map((c, idx) => {
              const isSelected = c.id === value;
              const isHighlighted = idx === highlightIndex;
              return (
                <button
                  key={c.id}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => {
                    onChange(c.id);
                    setOpen(false);
                  }}
                  onMouseEnter={() => setHighlightIndex(idx)}
                  className={cn(
                    'w-full flex items-center gap-3 px-4 py-3 text-left transition-colors',
                    isHighlighted && 'bg-indigo-50/60',
                    isSelected && 'bg-indigo-50',
                  )}
                >
                  <img
                    src={c.cover}
                    alt=""
                    className="w-10 h-10 rounded-lg object-cover bg-ink-100 shrink-0"
                  />
                  <div className="flex-1 min-w-0">
                    <p
                      className={cn(
                        'text-sm truncate',
                        isSelected
                          ? 'font-semibold text-indigo-800'
                          : 'font-medium text-ink-800',
                      )}
                    >
                      {c.title}
                    </p>
                    <p className="text-xs text-ink-400 mt-0.5">{getSubtitle(c)}</p>
                  </div>
                  <div className="text-right shrink-0 mr-1">
                    <p className="text-sm font-display font-bold text-indigo-800">
                      {c.studentCount.toLocaleString()}
                    </p>
                    <p className="text-[10px] text-ink-400 uppercase tracking-wider">学员</p>
                  </div>
                  {isSelected && (
                    <Check className="w-4 h-4 text-indigo-800 shrink-0" />
                  )}
                </button>
              );
            })}
            {courses.length === 0 && (
              <div className="px-4 py-8 text-center text-sm text-ink-400">
                暂无课程
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
