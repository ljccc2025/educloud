import type { AssignmentDraftInput, Course } from '../types';

export type AssignmentAction = 'draft' | 'publish';
export type AssignmentField = keyof AssignmentDraftInput;
export type AssignmentFormErrors = Partial<Record<AssignmentField, string>>;

export function validateAssignment(
  values: AssignmentDraftInput,
  courses: Course[],
  action: AssignmentAction,
  now = new Date()
): AssignmentFormErrors {
  const errors: AssignmentFormErrors = {};
  const course = courses.find((item) => item.id === values.courseId);

  if (!course) errors.courseId = '请选择所属课程';
  if (!values.title.trim()) errors.title = '请输入作业标题';

  if (!Number.isFinite(values.totalScore) || values.totalScore <= 0) {
    errors.totalScore = '满分必须大于 0';
  }

  if (!Number.isInteger(values.maxAttempts) || values.maxAttempts < 1) {
    errors.maxAttempts = '最大提交次数必须是至少为 1 的整数';
  }

  const dueAt = values.dueDate ? new Date(values.dueDate) : null;
  if (dueAt && Number.isNaN(dueAt.getTime())) {
    errors.dueDate = '截止时间格式无效';
  }

  if (action === 'publish') {
    if (!values.description.trim()) errors.description = '请输入作业说明';

    if (!dueAt || Number.isNaN(dueAt.getTime())) {
      errors.dueDate = '请选择截止时间';
    } else if (dueAt.getTime() <= now.getTime()) {
      errors.dueDate = '截止时间必须晚于当前时间';
    }

    if (course && course.status !== 'PUBLISHED') {
      errors.courseId = '当前课程尚未发布，作业只能保存为草稿';
    }
  }

  return errors;
}

export function hasAssignmentErrors(errors: AssignmentFormErrors): boolean {
  return Object.keys(errors).length > 0;
}
