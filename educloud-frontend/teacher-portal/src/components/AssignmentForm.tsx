import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import dayjs from 'dayjs';
import type { Assignment, AssignmentDraftInput, Course } from '../types';
import {
  hasAssignmentErrors,
  validateAssignment,
  type AssignmentAction,
  type AssignmentFormErrors,
} from '../utils/assignmentValidation';
import { cn } from '../utils/cn';

interface AssignmentFormProps {
  courses: Course[];
  initialAssignment?: Assignment | null;
  disabled?: boolean;
  savingAction?: AssignmentAction | null;
  submitError?: string | null;
  onCancel: () => void;
  onSaveDraft: (values: AssignmentDraftInput) => void;
  onPublish: (values: AssignmentDraftInput) => void;
}

const courseStatusLabels: Record<Course['status'], string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
};

const emptyValues: AssignmentDraftInput = {
  courseId: '',
  title: '',
  description: '',
  dueDate: '',
  totalScore: 100,
  allowLateSubmission: false,
  maxAttempts: 1,
};

function initialValues(assignment?: Assignment | null): AssignmentDraftInput {
  if (!assignment) return emptyValues;

  const dueAt = assignment.dueDate ? dayjs(assignment.dueDate) : null;
  return {
    courseId: assignment.courseId,
    title: assignment.title,
    description: assignment.description,
    dueDate: dueAt?.isValid() ? dueAt.format('YYYY-MM-DDTHH:mm') : '',
    totalScore: assignment.totalScore,
    allowLateSubmission: assignment.allowLateSubmission,
    maxAttempts: assignment.maxAttempts,
  };
}

export default function AssignmentForm({
  courses,
  initialAssignment,
  disabled = false,
  savingAction = null,
  submitError,
  onCancel,
  onSaveDraft,
  onPublish,
}: AssignmentFormProps) {
  const [values, setValues] = useState<AssignmentDraftInput>(() => initialValues(initialAssignment));
  const [errors, setErrors] = useState<AssignmentFormErrors>({});
  const [showAdvanced, setShowAdvanced] = useState(false);
  const selectedCourse = courses.find((course) => course.id === values.courseId);
  const isDisabled = disabled || savingAction !== null;

  const updateValue = <K extends keyof AssignmentDraftInput>(
    key: K,
    value: AssignmentDraftInput[K]
  ) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => {
      if (!current[key]) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const submit = (action: AssignmentAction) => {
    const nextErrors = validateAssignment(values, courses, action);
    setErrors(nextErrors);
    if (nextErrors.maxAttempts) setShowAdvanced(true);
    if (hasAssignmentErrors(nextErrors)) return;

    const normalized: AssignmentDraftInput = {
      ...values,
      title: values.title.trim(),
      description: values.description.trim(),
    };
    if (action === 'draft') onSaveDraft(normalized);
    else onPublish(normalized);
  };

  return (
    <form className="space-y-5" onSubmit={(event) => event.preventDefault()}>
      <div>
        <label htmlFor="assignment-course" className="mb-2 block text-sm font-medium text-ink-700">
          所属课程 <span className="text-amber-600">*</span>
        </label>
        <select
          id="assignment-course"
          value={values.courseId}
          onChange={(event) => updateValue('courseId', event.target.value)}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.courseId)}
          aria-describedby={
            errors.courseId
              ? 'assignment-course-error'
              : selectedCourse && selectedCourse.status !== 'PUBLISHED'
                ? 'assignment-course-help'
                : undefined
          }
          className="input-field disabled:cursor-not-allowed disabled:opacity-60"
        >
          <option value="">请选择课程</option>
          {courses.map((course) => (
            <option key={course.id} value={course.id}>
              {course.title} · {courseStatusLabels[course.status]}
            </option>
          ))}
        </select>
        {errors.courseId ? (
          <p id="assignment-course-error" className="mt-1.5 text-xs text-red-600">
            {errors.courseId}
          </p>
        ) : selectedCourse && selectedCourse.status !== 'PUBLISHED' ? (
          <p id="assignment-course-help" className="mt-1.5 text-xs text-amber-700">
            当前课程尚未发布，作业可以保存为草稿，但不能立即发布。
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="assignment-title" className="mb-2 block text-sm font-medium text-ink-700">
          作业标题 <span className="text-amber-600">*</span>
        </label>
        <input
          id="assignment-title"
          data-autofocus="true"
          type="text"
          value={values.title}
          onChange={(event) => updateValue('title', event.target.value)}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.title)}
          aria-describedby={errors.title ? 'assignment-title-error' : undefined}
          className="input-field disabled:cursor-not-allowed disabled:opacity-60"
          placeholder="例如：实现一个 RESTful 博客 API"
        />
        {errors.title && (
          <p id="assignment-title-error" className="mt-1.5 text-xs text-red-600">
            {errors.title}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="assignment-description" className="mb-2 block text-sm font-medium text-ink-700">
          作业说明 <span className="text-xs font-normal text-ink-400">（发布时必填）</span>
        </label>
        <textarea
          id="assignment-description"
          rows={3}
          value={values.description}
          onChange={(event) => updateValue('description', event.target.value)}
          disabled={isDisabled}
          aria-invalid={Boolean(errors.description)}
          aria-describedby={errors.description ? 'assignment-description-error' : undefined}
          className="input-field resize-none disabled:cursor-not-allowed disabled:opacity-60"
          placeholder="填写任务目标、完成标准和提交内容……"
        />
        {errors.description && (
          <p id="assignment-description-error" className="mt-1.5 text-xs text-red-600">
            {errors.description}
          </p>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="assignment-due-date" className="mb-2 block text-sm font-medium text-ink-700">
            截止时间 <span className="text-xs font-normal text-ink-400">（发布时必填）</span>
          </label>
          <input
            id="assignment-due-date"
            type="datetime-local"
            value={values.dueDate}
            min={dayjs().format('YYYY-MM-DDTHH:mm')}
            onChange={(event) => updateValue('dueDate', event.target.value)}
            disabled={isDisabled}
            aria-invalid={Boolean(errors.dueDate)}
            aria-describedby={errors.dueDate ? 'assignment-due-date-error' : undefined}
            className="input-field disabled:cursor-not-allowed disabled:opacity-60"
          />
          {errors.dueDate && (
            <p id="assignment-due-date-error" className="mt-1.5 text-xs text-red-600">
              {errors.dueDate}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="assignment-total-score" className="mb-2 block text-sm font-medium text-ink-700">
            满分 <span className="text-amber-600">*</span>
          </label>
          <input
            id="assignment-total-score"
            type="number"
            min="1"
            step="1"
            value={values.totalScore}
            onChange={(event) => updateValue('totalScore', Number(event.target.value))}
            disabled={isDisabled}
            aria-invalid={Boolean(errors.totalScore)}
            aria-describedby={errors.totalScore ? 'assignment-total-score-error' : undefined}
            className="input-field disabled:cursor-not-allowed disabled:opacity-60"
          />
          {errors.totalScore && (
            <p id="assignment-total-score-error" className="mt-1.5 text-xs text-red-600">
              {errors.totalScore}
            </p>
          )}
        </div>
      </div>

      <div className="rounded-xl border border-indigo-100 bg-indigo-50/55">
        <button
          type="button"
          onClick={() => setShowAdvanced((current) => !current)}
          disabled={isDisabled}
          aria-expanded={showAdvanced}
          className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm font-medium text-indigo-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <span>
            提交规则 · 最多 {values.maxAttempts} 次 ·{' '}
            {values.allowLateSubmission ? '允许迟交' : '不允许迟交'}
          </span>
          <ChevronDown
            className={cn('h-4 w-4 shrink-0 transition-transform', showAdvanced && 'rotate-180')}
          />
        </button>

        {showAdvanced && (
          <div className="grid grid-cols-1 gap-4 border-t border-indigo-100 px-4 py-4 sm:grid-cols-2">
            <div>
              <label htmlFor="assignment-max-attempts" className="mb-2 block text-sm font-medium text-ink-700">
                最大提交次数
              </label>
              <input
                id="assignment-max-attempts"
                type="number"
                min="1"
                step="1"
                value={values.maxAttempts}
                onChange={(event) => updateValue('maxAttempts', Number(event.target.value))}
                disabled={isDisabled}
                aria-invalid={Boolean(errors.maxAttempts)}
                aria-describedby={errors.maxAttempts ? 'assignment-max-attempts-error' : undefined}
                className="input-field disabled:cursor-not-allowed disabled:opacity-60"
              />
              {errors.maxAttempts && (
                <p id="assignment-max-attempts-error" className="mt-1.5 text-xs text-red-600">
                  {errors.maxAttempts}
                </p>
              )}
            </div>
            <div>
              <span className="mb-2 block text-sm font-medium text-ink-700">迟交设置</span>
              <button
                type="button"
                role="switch"
                aria-checked={values.allowLateSubmission}
                onClick={() => updateValue('allowLateSubmission', !values.allowLateSubmission)}
                disabled={isDisabled}
                className="flex h-[46px] w-full items-center justify-between rounded-xl border border-ink-200 bg-white px-4 text-sm text-ink-700 transition-colors hover:border-indigo-300 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <span>{values.allowLateSubmission ? '允许迟交' : '不允许迟交'}</span>
                <span
                  aria-hidden="true"
                  className={cn(
                    'relative h-6 w-11 rounded-full transition-colors',
                    values.allowLateSubmission ? 'bg-indigo-800' : 'bg-ink-200'
                  )}
                >
                  <span
                    className={cn(
                      'absolute top-1 h-4 w-4 rounded-full bg-white shadow-sm transition-transform',
                      values.allowLateSubmission ? 'translate-x-6' : 'translate-x-1'
                    )}
                  />
                </span>
              </button>
            </div>
          </div>
        )}
      </div>

      {submitError && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-700">
          {submitError}
        </div>
      )}

      <div className="flex flex-col-reverse gap-3 border-t border-ink-100 pt-4 sm:flex-row sm:justify-end">
        <button type="button" onClick={onCancel} disabled={isDisabled} className="btn-outline sm:min-w-24">
          取消
        </button>
        <button
          type="button"
          onClick={() => submit('draft')}
          disabled={isDisabled}
          className="btn-outline sm:min-w-28"
        >
          {savingAction === 'draft' ? '保存中…' : '保存草稿'}
        </button>
        <button
          type="button"
          onClick={() => submit('publish')}
          disabled={isDisabled}
          className="btn-primary sm:min-w-28"
        >
          {savingAction === 'publish' ? '发布中…' : '立即发布'}
        </button>
      </div>
    </form>
  );
}
