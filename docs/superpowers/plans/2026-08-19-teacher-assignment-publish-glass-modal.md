# 教师端“发布作业”液态玻璃弹窗实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在教师端“作业批改”页面增加液态玻璃“发布作业”弹窗，完成保存草稿、编辑草稿、草稿发布、直接发布及新作业空状态的 Mock 闭环。

**架构：** 扩展教师端作业领域类型和 Mock API，在独立 `AssignmentForm` 中实现动作级校验，在独立 `AssignmentPublishModal` 中实现 Portal、玻璃视觉和焦点行为，由 `AssignmentGrade` 负责列表合并、选中状态和批改视图分支。所有数据仅保存在当前教师端 Mock API 内存中，不改学生端或真实后端。

**技术栈：** React 18、TypeScript 5.5、Vite 5、Tailwind CSS 风格类、Lucide React、Day.js、现有 Mock API。

---

## 实施边界

- 设计依据：`docs/superpowers/specs/2026-08-19-teacher-assignment-publish-glass-modal-design.md`。
- 不增加依赖，不引入新的表单库、弹窗库或测试框架。
- 教师端 `package.json` 当前只有 `typecheck` 和 `build`，因此本计划使用类型检查、生产构建和真实浏览器交互作为验证证据。
- 当前工作区存在用户未提交修改，尤其 `AssignmentGrade.tsx` 已有改动。实施前后必须逐文件核对差异，不得还原、覆盖、暂存或提交不属于本功能的修改。
- 源码是否提交由执行时用户授权决定；默认保留为未提交差异。本文档自身可以独立提交。

## 文件结构

### 创建

- `educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts`：草稿与发布动作的纯校验函数和错误类型。
- `educloud-frontend/teacher-portal/src/components/AssignmentForm.tsx`：作业字段、高级提交规则、字段错误和三个底部动作。
- `educloud-frontend/teacher-portal/src/components/AssignmentPublishModal.tsx`：液态玻璃 Portal、异步动作、焦点循环、Escape 和滚动锁定。

### 修改

- `educloud-frontend/teacher-portal/src/types/index.ts:62`：补充作业状态、草稿输入和发布规则字段。
- `educloud-frontend/teacher-portal/src/services/api.ts:293`：补齐现有 Mock 作业状态和规则。
- `educloud-frontend/teacher-portal/src/services/api.ts:647`：增加草稿创建、草稿更新和发布方法，并让作业读取返回副本。
- `educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx:1`：接入课程数据、弹窗、草稿详情、发布后空状态和列表状态标签。

### 明确不修改

- `educloud-frontend/student-portal/**`：本期不做学生端同步。
- `educloud-backend/**`：本期不实现真实接口。
- `educloud-frontend/teacher-portal/src/components/GradeSheet.tsx`：继续复用现有批改组件，不改变评分逻辑。
- `educloud-frontend/teacher-portal/package.json`：不增加测试或 UI 依赖。

## 任务 1：定义作业状态、表单输入和纯校验

**文件：**

- 修改：`educloud-frontend/teacher-portal/src/types/index.ts:62-74`
- 创建：`educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts`

- [ ] **步骤 1：扩展作业领域类型**

在 `Assignment` 之前增加状态和输入类型，并扩展实体：

```ts
export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

export interface AssignmentDraftInput {
  courseId: string;
  title: string;
  description: string;
  dueDate: string;
  totalScore: number;
  allowLateSubmission: boolean;
  maxAttempts: number;
}

export interface Assignment {
  id: string;
  title: string;
  courseId: string;
  courseName: string;
  description: string;
  dueDate: string;
  totalScore: number;
  status: AssignmentStatus;
  allowLateSubmission: boolean;
  maxAttempts: number;
  publishedAt?: string;
  submissionCount: number;
  gradedCount: number;
  submissions: Submission[];
}
```

- [ ] **步骤 2：创建动作级校验函数**

创建 `assignmentValidation.ts`：

```ts
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
```

- [ ] **步骤 3：运行类型检查**

运行：

```powershell
npm run typecheck
```

工作目录：`educloud-frontend/teacher-portal`

预期：FAIL。现有三个 Mock `Assignment` 缺少 `status`、`allowLateSubmission` 和 `maxAttempts`，证明所有旧数据都必须迁移到新契约。

- [ ] **步骤 4：记录任务边界差异**

运行：

```powershell
git diff -- educloud-frontend/teacher-portal/src/types/index.ts educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts
```

预期：只有新增作业类型和纯校验文件；不出现学生端或批改组件变化。

## 任务 2：实现 Mock 作业生命周期

**文件：**

- 修改：`educloud-frontend/teacher-portal/src/services/api.ts:293-470`
- 修改：`educloud-frontend/teacher-portal/src/services/api.ts:647-661`

- [ ] **步骤 1：补充 API 所需类型导入**

在 `api.ts` 顶部现有 `import type` 中加入：

```ts
AssignmentDraftInput,
```

`Course` 和 `Assignment` 已存在于该导入块，继续复用，不能重新声明重复类型。

- [ ] **步骤 2：补齐现有 Mock 作业状态**

分别在三个根作业对象加入以下字段：

```ts
// a-001
status: 'PUBLISHED',
allowLateSubmission: false,
maxAttempts: 1,
publishedAt: '2026-08-10T09:00:00',

// a-002
status: 'PUBLISHED',
allowLateSubmission: true,
maxAttempts: 2,
publishedAt: '2026-08-11T09:00:00',

// a-003
status: 'PUBLISHED',
allowLateSubmission: false,
maxAttempts: 1,
publishedAt: '2026-08-12T09:00:00',
```

- [ ] **步骤 3：增加安全复制和领域保护函数**

在 API 导出对象前增加：

```ts
function cloneAssignment(assignment: Assignment): Assignment {
  return {
    ...assignment,
    submissions: assignment.submissions.map((submission) => ({ ...submission })),
  };
}

function requireDraftAssignment(id: string): Assignment {
  const assignment = mockAssignments.find((item) => item.id === id);
  if (!assignment) throw new Error('作业不存在');
  if (assignment.status !== 'DRAFT') throw new Error('只有草稿作业可以修改');
  return assignment;
}

function assertDraftInput(data: AssignmentDraftInput): void {
  if (!data.title.trim()) throw new Error('请输入作业标题');
  if (!Number.isFinite(data.totalScore) || data.totalScore <= 0) {
    throw new Error('满分必须大于 0');
  }
  if (!Number.isInteger(data.maxAttempts) || data.maxAttempts < 1) {
    throw new Error('最大提交次数必须是至少为 1 的整数');
  }
  if (data.dueDate && Number.isNaN(new Date(data.dueDate).getTime())) {
    throw new Error('截止时间格式无效');
  }
}

function courseForAssignment(courseId: string): Course {
  const course = mockCourses.find((item) => item.id === courseId);
  if (!course) throw new Error('所属课程不存在');
  return course;
}
```

- [ ] **步骤 4：实现草稿创建**

在 `api` 的 Assignments 区域增加：

```ts
createAssignmentDraft: (data: AssignmentDraftInput) => {
  assertDraftInput(data);
  const course = courseForAssignment(data.courseId);

  const assignment: Assignment = {
    id: `a-${Date.now()}`,
    courseId: course.id,
    courseName: course.title,
    title: data.title.trim(),
    description: data.description.trim(),
    dueDate: data.dueDate ? new Date(data.dueDate).toISOString() : '',
    totalScore: data.totalScore,
    status: 'DRAFT',
    allowLateSubmission: data.allowLateSubmission,
    maxAttempts: data.maxAttempts,
    submissionCount: 0,
    gradedCount: 0,
    submissions: [],
  };

  mockAssignments.unshift(assignment);
  return delay(cloneAssignment(assignment));
},
```

- [ ] **步骤 5：实现草稿更新**

```ts
updateAssignmentDraft: (id: string, data: AssignmentDraftInput) => {
  assertDraftInput(data);
  const assignment = requireDraftAssignment(id);
  const course = courseForAssignment(data.courseId);

  Object.assign(assignment, {
    courseId: course.id,
    courseName: course.title,
    title: data.title.trim(),
    description: data.description.trim(),
    dueDate: data.dueDate ? new Date(data.dueDate).toISOString() : '',
    totalScore: data.totalScore,
    allowLateSubmission: data.allowLateSubmission,
    maxAttempts: data.maxAttempts,
  });

  return delay(cloneAssignment(assignment));
},
```

- [ ] **步骤 6：实现发布状态迁移**

```ts
publishAssignment: (id: string) => {
  const assignment = requireDraftAssignment(id);
  const course = courseForAssignment(assignment.courseId);
  const dueAt = new Date(assignment.dueDate);

  if (course.status !== 'PUBLISHED') throw new Error('当前课程尚未发布，作业只能保存为草稿');
  if (!assignment.description.trim()) throw new Error('请输入作业说明');
  if (Number.isNaN(dueAt.getTime()) || dueAt.getTime() <= Date.now()) {
    throw new Error('截止时间必须晚于当前时间');
  }
  if (assignment.totalScore <= 0) throw new Error('满分必须大于 0');
  if (!Number.isInteger(assignment.maxAttempts) || assignment.maxAttempts < 1) {
    throw new Error('最大提交次数必须是至少为 1 的整数');
  }

  assignment.status = 'PUBLISHED';
  assignment.publishedAt = new Date().toISOString();
  return delay(cloneAssignment(assignment));
},
```

- [ ] **步骤 7：让读取方法返回副本**

将读取方法替换为：

```ts
getAssignments: () => delay(mockAssignments.map(cloneAssignment)),
getAssignment: (id: string) => {
  const assignment = mockAssignments.find((item) => item.id === id);
  return delay(assignment ? cloneAssignment(assignment) : null);
},
```

`gradeSubmission` 仍修改内部 Mock 对象，但返回值改为 `delay({ ...sub })`。

- [ ] **步骤 8：运行类型检查确认领域层通过**

运行：`npm run typecheck`

工作目录：`educloud-frontend/teacher-portal`

预期：PASS，无 TypeScript 错误。

- [ ] **步骤 9：检查 Mock API 差异**

运行：

```powershell
git diff --check -- educloud-frontend/teacher-portal/src/types/index.ts educloud-frontend/teacher-portal/src/services/api.ts educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts
```

预期：退出码 `0`，无空白错误。

## 任务 3：实现作业表单

**文件：**

- 创建：`educloud-frontend/teacher-portal/src/components/AssignmentForm.tsx`

- [ ] **步骤 1：写入表单依赖导入**

```ts
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
```

- [ ] **步骤 2：定义表单接口与初始值**

组件接口使用明确动作回调：

```ts
interface AssignmentFormProps {
  courses: Course[];
  initialAssignment?: Assignment | null;
  disabled?: boolean;
  submitError?: string | null;
  onCancel: () => void;
  onSaveDraft: (values: AssignmentDraftInput) => void;
  onPublish: (values: AssignmentDraftInput) => void;
}

const emptyValues: AssignmentDraftInput = {
  courseId: '',
  title: '',
  description: '',
  dueDate: '',
  totalScore: 100,
  allowLateSubmission: false,
  maxAttempts: 1,
};
```

编辑草稿时用 `dayjs(initialAssignment.dueDate).format('YYYY-MM-DDTHH:mm')` 转换时间；没有截止时间时保留空字符串。

- [ ] **步骤 3：实现字段状态和按动作校验**

```ts
const submit = (action: AssignmentAction) => {
  const nextErrors = validateAssignment(values, courses, action);
  setErrors(nextErrors);
  if (hasAssignmentErrors(nextErrors)) return;

  const normalized = {
    ...values,
    title: values.title.trim(),
    description: values.description.trim(),
  };
  if (action === 'draft') onSaveDraft(normalized);
  else onPublish(normalized);
};
```

字段改变时清除该字段已有错误，但保留其他错误。

- [ ] **步骤 4：实现核心字段布局**

表单顺序固定为：

1. 所属课程原生 `select`，使用 `input-field`，选项文字包含课程标题与状态；
2. 作业标题，带 `data-autofocus="true"`；
3. 作业说明，多行文本；
4. 双栏截止时间和满分；
5. 高级规则折叠区。

每个错误使用稳定 ID，例如 `assignment-title-error`，输入通过 `aria-invalid` 和 `aria-describedby` 关联。

- [ ] **步骤 5：实现高级提交规则**

折叠按钮摘要必须实时反映当前值：

```tsx
<button
  type="button"
  aria-expanded={showAdvanced}
  onClick={() => setShowAdvanced((current) => !current)}
>
  <span>提交规则 · 最多 {values.maxAttempts} 次 · {values.allowLateSubmission ? '允许迟交' : '不允许迟交'}</span>
  <ChevronDown className={cn('h-4 w-4 transition-transform', showAdvanced && 'rotate-180')} />
</button>
```

展开内容包含整数输入和带文本的迟交开关；不能只用颜色表达开关状态。

- [ ] **步骤 6：实现底部动作与错误区**

底部提供：

```tsx
<button type="button" onClick={onCancel} disabled={disabled} className="btn-outline">
  取消
</button>
<button type="button" onClick={() => submit('draft')} disabled={disabled} className="btn-outline">
  保存草稿
</button>
<button type="button" onClick={() => submit('publish')} disabled={disabled} className="btn-primary">
  立即发布
</button>
```

`submitError` 使用 `role="alert"` 的红色半透明提示区显示，不能清空字段状态。

- [ ] **步骤 7：运行类型检查**

运行：`npm run typecheck`

预期：PASS。

## 任务 4：实现液态玻璃发布弹窗

**文件：**

- 创建：`educloud-frontend/teacher-portal/src/components/AssignmentPublishModal.tsx`
- 参考：`educloud-frontend/teacher-portal/src/components/CourseCreateModal.tsx`

- [ ] **步骤 1：写入弹窗依赖导入**

```ts
import { useEffect, useRef, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import type { Assignment, AssignmentDraftInput, Course } from '../types';
import AssignmentForm from './AssignmentForm';
```

- [ ] **步骤 2：定义弹窗接口和异步状态**

```ts
interface AssignmentPublishModalProps {
  courses: Course[];
  initialAssignment?: Assignment | null;
  returnFocusRef: RefObject<HTMLElement>;
  fallbackFocusRef: RefObject<HTMLElement>;
  onClose: () => void;
  onSaveDraft: (values: AssignmentDraftInput) => Promise<void>;
  onPublish: (values: AssignmentDraftInput) => Promise<void>;
}

type SavingAction = 'draft' | 'publish' | null;
```

组件维护 `savingAction`、`errorMessage`、`dialogRef` 和 `mountedRef`。

- [ ] **步骤 3：实现 Portal、滚动锁和焦点恢复**

挂载时：

- 保存并设置 `document.body.style.overflow = 'hidden'`；
- 下一动画帧聚焦 `[data-autofocus="true"]`；
- 卸载时恢复原滚动值；若 `returnFocusRef.current` 仍连接在文档中则聚焦它，否则聚焦始终存在的 `fallbackFocusRef.current`；
- 异步完成后只在仍挂载时更新本地状态。

- [ ] **步骤 4：实现 Escape 和焦点循环**

Escape 监听挂在 `window`，仅 `savingAction === null` 时关闭。Tab/Shift+Tab 在弹窗首尾可聚焦元素之间循环，复用课程创建弹窗的可聚焦选择器：

```ts
const focusableSelector = [
  'button:not([disabled])',
  'input:not([disabled])',
  'textarea:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');
```

- [ ] **步骤 5：实现统一异步动作**

```ts
const runAction = async (
  action: Exclude<SavingAction, null>,
  values: AssignmentDraftInput
) => {
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
```

- [ ] **步骤 6：实现液态玻璃视觉**

外层与面板使用以下稳定类：

```tsx
<div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
  <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
      <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
    </div>
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="assignment-publish-title"
      className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-2xl overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
    >
      {/* 标题、关闭按钮、AssignmentForm */}
    </div>
  </div>
</div>
```

标题根据是否有 `initialAssignment` 显示“发布作业”或“编辑作业草稿”。

向 `AssignmentForm` 传入 `savingAction !== null`。草稿按钮在运行时显示“保存中…”，发布按钮在运行时显示“发布中…”，其他动作保持禁用但不改变标签。

- [ ] **步骤 7：运行类型检查和生产构建**

运行：

```powershell
npm run typecheck
npm run build
```

工作目录：`educloud-frontend/teacher-portal`

预期：两条命令均退出码 `0`，Vite 完成生产构建。

## 任务 5：接入作业批改页面

**文件：**

- 修改：`educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx:1-183`

- [ ] **步骤 1：保护现有用户差异**

运行：

```powershell
git diff -- educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx
```

保存输出作为实施前基线。后续只追加发布作业逻辑，保留现有圆角、学生选择记忆和批改布局改动。

- [ ] **步骤 2：加载课程并增加弹窗状态**

将导入更新为包含 `useRef`、`Plus`、`Inbox`、`AssignmentPublishModal` 以及 `Course`、`AssignmentDraftInput`、`AssignmentStatus`，再增加：

```ts
const [courses, setCourses] = useState<Course[]>([]);
const [editingAssignment, setEditingAssignment] = useState<Assignment | null>(null);
const [showPublishModal, setShowPublishModal] = useState(false);
const returnFocusRef = useRef<HTMLElement | null>(null);
const publishButtonRef = useRef<HTMLButtonElement>(null);
```

初次加载使用 `Promise.all([api.getAssignments(), api.getCourses()])`，失败时结束 loading 并保留空状态。

- [ ] **步骤 3：实现打开、关闭和列表合并函数**

```ts
const openCreate = (event: React.MouseEvent<HTMLButtonElement>) => {
  returnFocusRef.current = event.currentTarget;
  setEditingAssignment(null);
  setShowPublishModal(true);
};

const openEdit = (event: React.MouseEvent<HTMLButtonElement>, assignment: Assignment) => {
  returnFocusRef.current = event.currentTarget;
  setEditingAssignment(assignment);
  setShowPublishModal(true);
};

const upsertAssignment = (assignment: Assignment) => {
  setAssignments((current) => [
    assignment,
    ...current.filter((item) => item.id !== assignment.id),
  ]);
  setSelectedId(assignment.id);
};
```

关闭函数同时清空 `editingAssignment`。

- [ ] **步骤 4：实现保存草稿和发布处理**

```ts
const handleSaveDraft = async (values: AssignmentDraftInput) => {
  const saved = editingAssignment
    ? await api.updateAssignmentDraft(editingAssignment.id, values)
    : await api.createAssignmentDraft(values);
  upsertAssignment(saved);
  closePublishModal();
};

const handlePublish = async (values: AssignmentDraftInput) => {
  const draft = editingAssignment
    ? await api.updateAssignmentDraft(editingAssignment.id, values)
    : await api.createAssignmentDraft(values);
  const published = await api.publishAssignment(draft.id);
  upsertAssignment(published);
  closePublishModal();
};
```

只有 API 完整成功后才调用 `upsertAssignment`。

- [ ] **步骤 5：增加页面主按钮和弹窗**

标题区域改为响应式左右布局，右侧增加：

```tsx
<button ref={publishButtonRef} onClick={openCreate} className="btn-primary w-full sm:w-auto">
  <Plus className="h-4 w-4" />
  发布作业
</button>
```

页面末尾挂载：

```tsx
{showPublishModal && (
  <AssignmentPublishModal
    courses={courses}
    initialAssignment={editingAssignment}
    returnFocusRef={returnFocusRef}
    fallbackFocusRef={publishButtonRef}
    onClose={closePublishModal}
    onSaveDraft={handleSaveDraft}
    onPublish={handlePublish}
  />
)}
```

- [ ] **步骤 6：为作业列表增加状态分支**

增加状态映射：

```ts
const assignmentStatusConfig = {
  DRAFT: { label: '草稿', className: 'badge-amber' },
  PUBLISHED: { label: '已发布', className: 'badge-green' },
  CLOSED: { label: '已关闭', className: 'badge-indigo' },
} satisfies Record<AssignmentStatus, { label: string; className: string }>;
```

草稿卡显示状态和“尚未发布”，不显示“已批完”；没有截止时间时显示“未设置”。已发布和已关闭作业继续显示提交、待批和截止日期。

- [ ] **步骤 7：实现草稿详情和发布空状态**

右侧按以下顺序渲染：

1. `selected.status === 'DRAFT'`：详情卡、规则摘要、“编辑草稿”和“立即发布”按钮；
2. 已发布/关闭且 `selected.submissions.length === 0`：带 `Inbox` 图标的“等待学生提交作业”空状态；
3. 有提交：原样渲染 `GradeSheet`。

草稿详情中的“立即发布”打开同一编辑弹窗，让完整校验在表单中执行，不直接绕过表单发布。

- [ ] **步骤 8：验证原有选择记忆不回退**

切换现有 `a-001` 与 `a-002`，分别选择不同学生，再往返切换。

预期：每份作业仍恢复各自最后选中的学生；新增草稿或空作业不会改写 `selectedSubmissionIds`。

- [ ] **步骤 9：运行类型检查与构建**

运行：

```powershell
npm run typecheck
npm run build
```

预期：均通过。

## 任务 6：真实浏览器验收与最终边界检查

**文件：**

- 验证：`educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx`
- 验证：`educloud-frontend/teacher-portal/src/components/AssignmentForm.tsx`
- 验证：`educloud-frontend/teacher-portal/src/components/AssignmentPublishModal.tsx`
- 验证：`educloud-frontend/teacher-portal/src/services/api.ts`
- 验证：`educloud-frontend/teacher-portal/src/types/index.ts`
- 验证：`educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts`

- [ ] **步骤 1：启动教师端开发服务器**

运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 4175
```

工作目录：`educloud-frontend/teacher-portal`

预期：Vite 在 `http://127.0.0.1:4175/` 提供页面。

- [ ] **步骤 2：验收保存草稿**

在 `/assignments`：

1. 点击“发布作业”；
2. 只选择课程并填写标题；
3. 点击“保存草稿”。

预期：弹窗关闭，草稿位于列表顶部并自动选中，详情显示“编辑草稿”和“立即发布”。

- [ ] **步骤 3：验收草稿编辑与发布**

1. 点击“编辑草稿”；
2. 补全说明、未来截止时间、满分；
3. 展开提交规则，设置最大次数 `2` 并开启迟交；
4. 点击“立即发布”。

预期：同一 ID 状态变为“已发布”，列表中只有一条，显示 `0` 提交，右侧显示“等待学生提交作业”。

- [ ] **步骤 4：验收发布校验**

分别验证：

- 选择 `DRAFT` 课程后发布；
- 截止时间早于当前时间；
- 满分为 `0`；
- 最大提交次数为 `0` 或小数。

预期：对应字段显示明确错误，弹窗不关闭，列表不新增或更新；改正后错误消失。

- [ ] **步骤 5：验收弹窗行为**

验证：

- 打开后焦点在标题；
- Shift+Tab/Tab 在弹窗首尾循环；
- Escape、关闭按钮和取消恢复 body 滚动；
- 关闭后焦点返回触发按钮；
- 提交中按钮和关闭入口禁用；
- 390px 宽度下弹窗保留安全边距，内容在面板内部滚动。

- [ ] **步骤 6：检查浏览器控制台**

预期：本功能不产生错误。React Router 既有 future flag 警告可以记录为项目既有警告，不能写成新增错误。

- [ ] **步骤 7：运行最终静态验证**

运行：

```powershell
npm run typecheck
npm run build
git diff --check -- educloud-frontend/teacher-portal/src/types/index.ts educloud-frontend/teacher-portal/src/services/api.ts educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts educloud-frontend/teacher-portal/src/components/AssignmentForm.tsx educloud-frontend/teacher-portal/src/components/AssignmentPublishModal.tsx educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx
```

前两条命令在 `educloud-frontend/teacher-portal` 执行，Git 命令在仓库根执行。

由于 Git 不检查未跟踪新文件，再从仓库根运行：

```powershell
$newFiles = @(
  'educloud-frontend/teacher-portal/src/utils/assignmentValidation.ts',
  'educloud-frontend/teacher-portal/src/components/AssignmentForm.tsx',
  'educloud-frontend/teacher-portal/src/components/AssignmentPublishModal.tsx'
)
$badWhitespace = $newFiles | ForEach-Object {
  Select-String -LiteralPath $_ -Pattern '[ \t]+$'
}
if ($badWhitespace) {
  $badWhitespace | ForEach-Object { "{0}:{1}" -f $_.Path, $_.LineNumber }
  exit 1
}
```

预期：类型检查、构建和两个空白检查均退出码 `0`。

- [ ] **步骤 8：核对最终变更边界**

运行：

```powershell
git status --short
git diff -- educloud-frontend/teacher-portal/src/types/index.ts educloud-frontend/teacher-portal/src/services/api.ts educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx
```

确认：

- 没有学生端或后端文件变化；
- `AssignmentGrade.tsx` 的原有用户修改仍存在；
- 没有暂存或提交无关文件；
- 最终交付明确说明数据只在教师端 Mock 会话内有效。

## 完成标准

- “作业批改”页存在“发布作业”主按钮和液态玻璃弹窗；
- 草稿保存、编辑、发布和直接发布均形成可见闭环；
- 草稿课程与无效规则无法发布；
- 新发布作业不会重复，且显示等待提交空状态；
- 现有批改和学生选择记忆不回退；
- 类型检查、生产构建、差异检查和桌面/移动浏览器验收均有通过证据；
- 未引入新依赖，未修改学生端或后端，未夹带工作区其他改动。
