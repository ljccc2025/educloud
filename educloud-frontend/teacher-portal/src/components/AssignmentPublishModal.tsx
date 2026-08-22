import { useEffect, useRef, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import type { Assignment, AssignmentDraftInput, Course } from '../types';
import type { AssignmentAction } from '../utils/assignmentValidation';
import AssignmentForm from './AssignmentForm';

interface AssignmentPublishModalProps {
  courses: Course[];
  initialAssignment?: Assignment | null;
  returnFocusRef: RefObject<HTMLElement>;
  fallbackFocusRef: RefObject<HTMLElement>;
  onClose: () => void;
  onSaveDraft: (values: AssignmentDraftInput) => Promise<void>;
  onPublish: (values: AssignmentDraftInput) => Promise<void>;
}

const focusableSelector = [
  'button:not([disabled])',
  'input:not([disabled])',
  'textarea:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

export default function AssignmentPublishModal({
  courses,
  initialAssignment,
  returnFocusRef,
  fallbackFocusRef,
  onClose,
  onSaveDraft,
  onPublish,
}: AssignmentPublishModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(true);
  const [savingAction, setSavingAction] = useState<AssignmentAction | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const isEditing = Boolean(initialAssignment);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    mountedRef.current = true;
    document.body.style.overflow = 'hidden';

    const focusFrame = requestAnimationFrame(() => {
      dialogRef.current?.querySelector<HTMLElement>('[data-autofocus="true"]')?.focus();
    });

    return () => {
      mountedRef.current = false;
      cancelAnimationFrame(focusFrame);
      document.body.style.overflow = previousOverflow;

      const returnTarget = returnFocusRef.current;
      requestAnimationFrame(() => {
        if (returnTarget?.isConnected) returnTarget.focus();
        else fallbackFocusRef.current?.focus();
      });
    };
  }, [fallbackFocusRef, returnFocusRef]);

  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !savingAction) {
        event.preventDefault();
        onClose();
      }
    };

    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [onClose, savingAction]);

  const runAction = async (action: AssignmentAction, values: AssignmentDraftInput) => {
    if (savingAction) return;

    setSavingAction(action);
    setErrorMessage(null);
    try {
      await (action === 'draft' ? onSaveDraft(values) : onPublish(values));
    } catch (error) {
      if (mountedRef.current) {
        setErrorMessage(error instanceof Error ? error.message : '作业保存失败，请稍后重试');
      }
    } finally {
      if (mountedRef.current) setSavingAction(null);
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
          aria-labelledby="assignment-publish-title"
          onKeyDown={handleKeyDown}
          className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-2xl overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
        >
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <p className="section-label mb-1">作业管理</p>
              <h2
                id="assignment-publish-title"
                className="font-display text-2xl font-semibold text-ink-900"
              >
                {isEditing ? '编辑作业草稿' : '发布作业'}
              </h2>
            </div>
            <button
              type="button"
              onClick={onClose}
              disabled={savingAction !== null}
              aria-label="关闭发布作业弹层"
              className="rounded-xl p-2 text-ink-400 transition-colors hover:bg-white/70 hover:text-ink-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <AssignmentForm
            courses={courses}
            initialAssignment={initialAssignment}
            disabled={savingAction !== null}
            savingAction={savingAction}
            submitError={errorMessage}
            onCancel={onClose}
            onSaveDraft={(values) => void runAction('draft', values)}
            onPublish={(values) => void runAction('publish', values)}
          />
        </div>
      </div>
    </div>,
    document.body
  );
}
