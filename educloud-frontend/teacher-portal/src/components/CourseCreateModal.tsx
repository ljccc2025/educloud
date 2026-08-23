import { useEffect, useRef, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import type { Category, CourseDraftInput } from '../types';
import CourseForm from './CourseForm';
import { teacherCourseApi } from '../services/teacherCourseApi';
import { apiErrorText } from '../services/http';

interface CourseCreateModalProps {
  onClose: () => void;
  onSubmit: (data: CourseDraftInput) => Promise<void>;
  returnFocusRef: RefObject<HTMLButtonElement>;
}

const focusableSelector = [
  'button:not([disabled])',
  'input:not([disabled])',
  'textarea:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

export default function CourseCreateModal({
  onClose,
  onSubmit,
  returnFocusRef,
}: CourseCreateModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(true);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesError, setCategoriesError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    teacherCourseApi
      .getCategories()
      .then((data) => {
        if (alive) setCategories(data);
      })
      .catch((e) => {
        if (alive) setCategoriesError(apiErrorText(e));
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const focusFrame = requestAnimationFrame(() => {
      dialogRef.current
        ?.querySelector<HTMLElement>('[data-autofocus="true"]')
        ?.focus();
    });

    mountedRef.current = true;
    document.body.style.overflow = 'hidden';

    return () => {
      mountedRef.current = false;
      cancelAnimationFrame(focusFrame);
      document.body.style.overflow = previousOverflow;
      returnFocusRef.current?.focus();
    };
  }, [returnFocusRef]);

  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) {
        event.preventDefault();
        onClose();
      }
    };

    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [onClose, saving]);

  const handleSubmit = async (data: CourseDraftInput) => {
    if (saving) return;

    setSaving(true);
    setErrorMessage(null);

    try {
      await onSubmit(data);
    } catch (error) {
      if (mountedRef.current) {
        setErrorMessage(apiErrorText(error));
      }
    } finally {
      if (mountedRef.current) {
        setSaving(false);
      }
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'Tab' || !dialogRef.current) return;

    const focusable = Array.from(
      dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector)
    );
    if (focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (!dialogRef.current.contains(document.activeElement)) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    } else if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
      <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
          <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
        </div>

        <div
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby="create-course-title"
          onKeyDown={handleKeyDown}
          className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-3xl overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6 lg:p-7"
        >
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <p className="section-label mb-1">课程编辑</p>
              <h2
                id="create-course-title"
                className="font-display text-2xl font-semibold text-ink-900"
              >
                新建课程
              </h2>
            </div>
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              aria-label="关闭新建课程弹层"
              className="rounded-xl p-2 text-ink-400 transition-colors hover:bg-white/70 hover:text-ink-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {categoriesError ? (
            <div role="alert" className="rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-700">
              分类加载失败：{categoriesError}
            </div>
          ) : categories.length === 0 ? (
            <div className="text-center py-12 text-ink-400 text-sm">正在加载课程分类…</div>
          ) : (
            <CourseForm
              variant="modal"
              categories={categories}
              onSubmit={handleSubmit}
              onCancel={onClose}
              loading={saving}
              errorMessage={errorMessage}
              submitLabel="创建课程"
            />
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
