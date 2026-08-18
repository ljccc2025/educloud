# 教师端新建课程液态玻璃弹层实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将课程管理页“新建课程”改为与创建考试、创建直播一致的液态玻璃弹层，同时保留课程创建和创建后编辑流程。

**架构：** `CourseManage` 只管理弹层开关与创建后的导航；新增 `CourseCreateModal` 负责 Portal、玻璃视觉、滚动锁定、焦点与错误状态；`CourseForm` 继续作为字段和提交数据的唯一来源，通过可选的 `modal` 展示变体复用。当前教师端没有自动化测试脚本，用户已批准本次不引入测试依赖，使用类型检查、生产构建和浏览器交互验收。

**技术栈：** React 18.3.1、TypeScript 5.5.4、React Router 6.27、Zustand 5、Tailwind CSS 3.4、Vite 5.4、Lucide React。

---

## 文件职责

- 创建：`educloud-frontend/teacher-portal/src/components/CourseCreateModal.tsx`——只负责新建课程 Dialog 的呈现、焦点、滚动锁定、保存状态和错误反馈。
- 修改：`educloud-frontend/teacher-portal/src/components/CourseForm.tsx`——增加可选弹层展示参数，保持课程字段与数据转换单一来源。
- 修改：`educloud-frontend/teacher-portal/src/pages/CourseManage.tsx`——打开弹层、调用 Store 创建课程、成功后导航。
- 不修改：`educloud-frontend/teacher-portal/src/pages/CourseEdit.tsx`——已有课程编辑和历史 `/courses/edit/new` 兼容路径保持现状。

## 工作区保护

- 开始和结束每个任务时运行 `git status --short`。
- 上述三个源文件当前包含用户未提交改动；编辑前先读取 `git diff -- <path>`，只在现有内容上增量修改。
- 禁止 `git add -A`、`git stash`、恢复或覆盖其他教师端、学生端和管理端改动。
- 本次实现默认不提交用户源代码改动；若需要提交，必须先由用户明确授权并精确暂存三个目标文件。

---

### 任务 1：让课程表单支持弹层展示

**文件：**

- 修改：`educloud-frontend/teacher-portal/src/components/CourseForm.tsx`

- [ ] **步骤 1：记录变更前证据**

运行：

```powershell
git diff -- educloud-frontend/teacher-portal/src/components/CourseForm.tsx
Get-Content educloud-frontend/teacher-portal/src/components/CourseForm.tsx
```

预期：确认现有圆角、封面预览和状态按钮属于用户改动，后续不得覆盖。

- [ ] **步骤 2：扩展最小 Props**

将 Props 扩展为：

```tsx
interface CourseFormProps {
  initialCourse?: Course | null;
  onSubmit: (data: Partial<Course>) => void | Promise<void>;
  onCancel?: () => void;
  loading?: boolean;
  variant?: 'page' | 'modal';
  errorMessage?: string | null;
}
```

组件参数使用默认值：

```tsx
export default function CourseForm({
  initialCourse,
  onSubmit,
  onCancel,
  loading,
  variant = 'page',
  errorMessage,
}: CourseFormProps) {
  const isModal = variant === 'modal';
```

- [ ] **步骤 3：只改变弹层密度，不改变字段含义**

使用现有 `cn` 控制间距：

```tsx
<form
  id="course-form"
  onSubmit={handleSubmit}
  className={cn(isModal ? 'space-y-5' : 'space-y-8')}
>
```

标题输入框仅在弹层模式自动聚焦：

```tsx
<input
  autoFocus={isModal}
  data-autofocus={isModal ? 'true' : undefined}
  type="text"
  value={title}
  onChange={(e) => setTitle(e.target.value)}
  placeholder="请输入课程标题，例如：Spring Boot 3 实战"
  className={cn('input-field', isModal ? 'text-base' : 'font-display text-lg')}
  required
/>
```

简介、双列区和封面区分别使用：

```tsx
rows={isModal ? 3 : 5}
className={cn('grid grid-cols-1 md:grid-cols-2', isModal ? 'gap-4' : 'gap-6')}
className={cn(
  'border-2 border-dashed border-ink-200 text-center hover:border-indigo-800 transition-colors rounded-2xl',
  isModal ? 'p-5' : 'p-8',
)}
```

封面预览高度使用 `isModal ? 'h-32' : 'h-48'`，不改变 URL、默认封面和状态数据逻辑。

- [ ] **步骤 4：增加错误和取消反馈**

在提交区之前渲染：

```tsx
{errorMessage && (
  <div role="alert" className="rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-700">
    {errorMessage}
  </div>
)}
```

按钮区保持现有按钮类，并使保存期间无法取消：

```tsx
<div className={cn(
  'flex items-center gap-4 border-t border-ink-100',
  isModal ? 'pt-4' : 'pt-4',
)}>
  <button type="submit" disabled={loading} className={cn('btn-primary', isModal && 'flex-1')}>
    <Save className="w-4 h-4" />
    {loading ? '保存中…' : '保存课程'}
  </button>
  <button
    type="button"
    onClick={onCancel}
    disabled={loading}
    className={cn('btn-outline', isModal && 'flex-1')}
  >
    取消
  </button>
</div>
```

- [ ] **步骤 5：运行第一轮类型检查**

运行：

```powershell
Set-Location educloud-frontend/teacher-portal
npm run typecheck
```

预期：退出码 0；已有 `CourseEdit` 不传新 Props 仍能编译。

---

### 任务 2：实现液态玻璃课程创建弹层

**文件：**

- 创建：`educloud-frontend/teacher-portal/src/components/CourseCreateModal.tsx`

- [ ] **步骤 1：创建职责单一的 Portal 组件**

实现以下接口和状态：

```tsx
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import type { Course } from '../types';
import CourseForm from './CourseForm';

interface CourseCreateModalProps {
  onClose: () => void;
  onSubmit: (data: Partial<Course>) => Promise<void>;
}

const focusableSelector = [
  'button:not([disabled])',
  'input:not([disabled])',
  'textarea:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');
```

组件保存 `saving`、`errorMessage` 和 `dialogRef`。提交函数必须捕获错误并保持弹层：

```tsx
const handleSubmit = async (data: Partial<Course>) => {
  if (saving) return;
  setSaving(true);
  setErrorMessage(null);
  try {
    await onSubmit(data);
  } catch (error) {
    setErrorMessage(error instanceof Error ? error.message : '课程创建失败，请稍后重试');
  } finally {
    setSaving(false);
  }
};
```

- [ ] **步骤 2：实现滚动锁定和焦点恢复**

使用单个 Effect 保存并恢复页面状态：

```tsx
useEffect(() => {
  const previousOverflow = document.body.style.overflow;
  const previousFocus = document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;

  document.body.style.overflow = 'hidden';
  requestAnimationFrame(() => {
    dialogRef.current?.querySelector<HTMLElement>('[data-autofocus="true"]')?.focus();
  });

  return () => {
    document.body.style.overflow = previousOverflow;
    previousFocus?.focus();
  };
}, []);
```

- [ ] **步骤 3：实现 Escape 与焦点环**

Dialog 的 `onKeyDown` 使用：

```tsx
const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
  if (event.key === 'Escape') {
    if (!saving) onClose();
    return;
  }
  if (event.key !== 'Tab' || !dialogRef.current) return;

  const focusable = Array.from(
    dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector),
  );
  if (focusable.length === 0) return;

  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
};
```

- [ ] **步骤 4：实现与考试/直播一致的玻璃结构**

组件返回以下 Portal 结构；不复制全局 CSS：

```tsx
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
            <h2 id="create-course-title" className="font-display text-2xl font-semibold text-ink-900">
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
        <CourseForm
          variant="modal"
          onSubmit={handleSubmit}
          onCancel={onClose}
          loading={saving}
          errorMessage={errorMessage}
        />
      </div>
    </div>
  </div>,
  document.body,
);
```

- [ ] **步骤 5：运行类型检查**

运行：

```powershell
Set-Location educloud-frontend/teacher-portal
npm run typecheck
```

预期：退出码 0，无未使用导入和 Props 类型错误。

---

### 任务 3：从课程管理页接入弹层

**文件：**

- 修改：`educloud-frontend/teacher-portal/src/pages/CourseManage.tsx`

- [ ] **步骤 1：记录课程管理页当前差异**

运行：

```powershell
git diff -- educloud-frontend/teacher-portal/src/pages/CourseManage.tsx
```

预期：确认课程封面和筛选按钮的现有圆角改动，后续保留。

- [ ] **步骤 2：接入组件和 Store 方法**

新增导入：

```tsx
import CourseCreateModal from '../components/CourseCreateModal';
```

Store 解构和本地状态修改为：

```tsx
const { courses, loading, fetchCourses, createCourse, deleteCourse } = useCourseStore();
const [showCreate, setShowCreate] = useState(false);
```

- [ ] **步骤 3：实现创建后导航**

增加：

```tsx
const handleCreate = async (data: Partial<Course>) => {
  const created = await createCourse(data);
  setShowCreate(false);
  navigate(`/courses/edit/${created.id}`);
};
```

将按钮改为：

```tsx
<button onClick={() => setShowCreate(true)} className="btn-primary">
  <Plus className="w-4 h-4" />
  新建课程
</button>
```

- [ ] **步骤 4：在页面末尾挂载弹层**

在课程管理页根容器闭合前添加：

```tsx
{showCreate && (
  <CourseCreateModal
    onClose={() => setShowCreate(false)}
    onSubmit={handleCreate}
  />
)}
```

弹层使用 Portal，因此不会改变表格布局；取消只设置 `showCreate=false`。

- [ ] **步骤 5：运行完整静态验证**

运行：

```powershell
Set-Location educloud-frontend/teacher-portal
npm run typecheck
npm run build
```

预期：两条命令退出码均为 0，Vite 生成 `dist`，没有 TypeScript 错误。

---

### 任务 4：浏览器交互和视觉验收

**文件：**

- 验证：`educloud-frontend/teacher-portal/src/pages/CourseManage.tsx`
- 验证：`educloud-frontend/teacher-portal/src/components/CourseCreateModal.tsx`
- 验证：`educloud-frontend/teacher-portal/src/components/CourseForm.tsx`

- [ ] **步骤 1：启动教师端开发服务器**

运行：

```powershell
Set-Location educloud-frontend/teacher-portal
npm run dev -- --host 127.0.0.1
```

预期：Vite 输出可访问的本地 URL。

- [ ] **步骤 2：验证打开与取消**

1. 打开课程管理页。
2. 设置一个搜索词和非默认状态筛选。
3. 点击“新建课程”，确认地址栏不离开课程列表，背景仍可辨认并被柔化。
4. 检查琥珀/靛蓝光晕、半透明白面板、白色描边、圆角与考试/直播一致。
5. 点击取消，确认搜索、筛选和滚动位置保持。
6. 再次打开并按 Escape，确认弹层关闭且焦点返回“新建课程”。

- [ ] **步骤 3：验证焦点与视口**

1. 打开弹层，确认课程标题输入框获得焦点。
2. 用 Tab 和 Shift+Tab 循环，确认焦点不离开 Dialog。
3. 在窄屏和较低视口检查表单内部滚动，页面背景不滚动。
4. 保存期间确认关闭、取消和重复保存均被禁用。

- [ ] **步骤 4：验证创建数据流**

1. 填写标题、简介、分类、价格、封面 URL 和状态。
2. 保存后确认只创建一门课程，并导航到 `/courses/edit/{createdId}`。
3. 返回课程列表，确认新课程存在。
4. 临时让 Mock 创建调用抛错时，确认弹层保持、输入不丢失且显示错误；验证后恢复该临时改动，不保留在工作区。

- [ ] **步骤 5：保存验收截图**

保存桌面宽屏和窄屏各一张截图，确认实际效果而非仅凭源码判断。若截图工具受阻，明确报告未生成截图，不把代码检查描述成视觉验证。

---

### 任务 5：最终差异和工作区边界验证

**文件：**

- 验证：上述三个目标源文件

- [ ] **步骤 1：运行完成前验证**

从仓库根运行：

```powershell
Set-Location educloud-frontend/teacher-portal
npm run typecheck
npm run build
Set-Location ../../
$targets = @(
  'educloud-frontend/teacher-portal/src/pages/CourseManage.tsx',
  'educloud-frontend/teacher-portal/src/components/CourseCreateModal.tsx',
  'educloud-frontend/teacher-portal/src/components/CourseForm.tsx'
)
git diff --check -- $targets
git status --short
```

预期：类型检查和构建退出码 0；三个目标文件无空白错误；其他已有工作区改动仍存在且未被暂存、恢复或覆盖。

- [ ] **步骤 2：逐项核对规格**

确认：

- [ ] 新建入口不离开课程列表。
- [ ] 玻璃遮罩、光晕和面板与考试/直播一致。
- [ ] 取消保留列表上下文。
- [ ] 保存成功进入已创建课程编辑页。
- [ ] 保存失败保留输入和弹层。
- [ ] Dialog 焦点、Escape、滚动锁定和响应式可用。
- [ ] 未改动 Store、API、课程类型和其他页面。

- [ ] **步骤 3：交付但不混入用户改动**

不自动提交三个源文件。最终回复列出精确修改文件、实际执行的验证命令、截图证据和仍未验证事项。
