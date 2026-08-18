# 教师端创建考试液态弹窗实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将教师端“创建考试”弹窗改为与“创建直播”完全一致的全视口液态玻璃弹窗，同时保持现有考试创建业务逻辑不变。

**架构：** 使用 React `createPortal` 将弹窗挂载到 `document.body`，避免页面入场动画的 `transform` 改变固定定位的包含块。通过 `useEffect` 在弹窗显示期间锁定 `body` 滚动，并为玻璃面板设置动态视口最大高度和内部滚动。

**技术栈：** React 18、TypeScript、ReactDOM Portal、Tailwind CSS、Playwright Core、Vite。

---

## 文件结构

- 修改：`educloud-frontend/teacher-portal/src/pages/ExamManage.tsx`——Portal 渲染、背景滚动锁定、液态玻璃弹窗布局。
- 临时创建并删除：`_teacher-exam-modal-regression.mjs`——Playwright 红绿回归，不提交到仓库。
- 不修改：考试 API、类型、列表、统计卡片、现有表单字段和创建逻辑。

### 任务 1：建立修复前失败回归

**文件：**
- 创建：`_teacher-exam-modal-regression.mjs`
- 读取：`educloud-frontend/teacher-portal/src/pages/ExamManage.tsx`

- [ ] **步骤 1：编写 Playwright 回归脚本**

脚本使用现有临时 Playwright Core 和本机 Chrome，验证桌面与低高度视口；登录态通过教师端现有 localStorage token 注入：

```js
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/leijianchu/AppData/Local/Temp/educloud-live-playwright/node_modules/playwright-core');
const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
});

for (const viewport of [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'short', width: 1024, height: 520 },
]) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => localStorage.setItem('teacher_token', 'playwright-teacher-token'));
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });

  await page.goto('http://127.0.0.1:5174/exams', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '创建考试' }).first().click();

  const state = await page.evaluate(() => {
    const dialog = document.querySelector('[role="dialog"][aria-modal="true"]');
    const overlay = dialog?.parentElement?.parentElement;
    const rect = (element) => element?.getBoundingClientRect().toJSON() ?? null;
    return {
      dialog: rect(dialog),
      overlay: rect(overlay),
      bodyOverflow: getComputedStyle(document.body).overflow,
      scrollWidth: document.documentElement.scrollWidth,
      dialogClientHeight: dialog?.clientHeight ?? null,
      dialogScrollHeight: dialog?.scrollHeight ?? null,
      dialogOverflowY: dialog ? getComputedStyle(dialog).overflowY : null,
    };
  });

  assert.deepEqual(errors, [], `${viewport.name} 出现控制台错误`);
  assert.ok(state.dialog, `${viewport.name} 缺少可访问弹窗节点`);
  assert.deepEqual(state.overlay, {
    x: 0,
    y: 0,
    width: viewport.width,
    height: viewport.height,
    top: 0,
    right: viewport.width,
    bottom: viewport.height,
    left: 0,
  }, `${viewport.name} 遮罩未覆盖完整视口`);
  assert.equal(state.bodyOverflow, 'hidden', `${viewport.name} 未锁定背景滚动`);
  assert.ok(state.scrollWidth <= viewport.width, `${viewport.name} 存在横向溢出`);

  if (viewport.name === 'desktop') {
    assert.ok(state.dialog.bottom <= viewport.height, '桌面弹窗底部超出视口');
  } else {
    assert.ok(state.dialogScrollHeight >= state.dialogClientHeight, '低高度弹窗尺寸异常');
    assert.ok(['auto', 'scroll'].includes(state.dialogOverflowY), '低高度弹窗未启用内部滚动');
  }

  await page.getByRole('button', { name: '取消' }).click();
  assert.equal(await page.evaluate(() => document.body.style.overflow), '', `${viewport.name} 关闭后未恢复滚动`);
  await context.close();
}

await browser.close();
```

- [ ] **步骤 2：启动教师端并确认测试失败**

运行：

```powershell
pnpm dev --host 127.0.0.1 --port 5174
node _teacher-exam-modal-regression.mjs
```

预期：脚本非零退出；旧弹窗没有 `role="dialog"`，且 `body` 未锁定滚动。

### 任务 2：实现与创建直播统一的弹窗

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/pages/ExamManage.tsx`

- [ ] **步骤 1：增加 Portal 导入和滚动锁定**

在 React hooks 导入后增加：

```tsx
import { createPortal } from 'react-dom';
```

在考试列表加载副作用后增加：

```tsx
useEffect(() => {
  if (!showCreate) return undefined;

  const previousOverflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';

  return () => {
    document.body.style.overflow = previousOverflow;
  };
}, [showCreate]);
```

- [ ] **步骤 2：把弹窗移动到 Portal 并应用液态玻璃布局**

在 `return` 前声明 `createModal`，字段绑定和事件处理保持原样，外层结构使用：

```tsx
const createModal = showCreate
  ? createPortal(
      <div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
        <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
          <div className="pointer-events-none absolute inset-0 overflow-hidden">
            <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
            <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
          </div>
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-exam-title"
            className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-lg overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
          >
            <h2 id="create-exam-title" className="font-display text-xl font-semibold text-ink-900">创建考试</h2>
            <div className="mt-4 space-y-4">
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">考试标题</label>
                <input
                  type="text"
                  value={newTitle}
                  onChange={(event) => setNewTitle(event.target.value)}
                  className="input-field"
                  placeholder="例如：期末考试"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">关联课程</label>
                <select
                  value={newCourseId}
                  onChange={(event) => setNewCourseId(event.target.value)}
                  className="input-field cursor-pointer appearance-none"
                >
                  <option value="">请选择课程</option>
                  <option value="c-001">Spring Boot 3 实战</option>
                  <option value="c-002">Python 数据分析与可视化</option>
                  <option value="c-003">React 18 + TypeScript</option>
                  <option value="c-005">机器学习入门</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">题目数量</label>
                  <input
                    type="number"
                    value={newQuestionCount}
                    onChange={(event) => setNewQuestionCount(event.target.value)}
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">时长（分钟）</label>
                  <input
                    type="number"
                    value={newDuration}
                    onChange={(event) => setNewDuration(event.target.value)}
                    className="input-field"
                  />
                </div>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">开考时间</label>
                <input
                  type="datetime-local"
                  value={newScheduledAt}
                  onChange={(event) => setNewScheduledAt(event.target.value)}
                  className="input-field"
                />
              </div>
              <div className="flex gap-3 pt-1">
                <button onClick={handleCreate} className="btn-primary flex-1">创建</button>
                <button onClick={() => setShowCreate(false)} className="btn-outline flex-1">取消</button>
              </div>
            </div>
          </div>
        </div>
      </div>,
      document.body,
    )
  : null;
```

返回 Fragment，使原考试页面与 `{createModal}` 同级；删除页面内容容器内部的旧弹窗 JSX。弹窗标题设置 `id="create-exam-title"`，表单主体使用 `mt-4 space-y-4`，数字字段继续保持两列。

- [ ] **步骤 3：运行类型检查**

运行：`pnpm typecheck`

预期：退出码为 0，无 TypeScript 错误。

### 任务 3：验证回归、构建和变更边界

**文件：**
- 使用并删除：`_teacher-exam-modal-regression.mjs`
- 检查：`educloud-frontend/teacher-portal/src/pages/ExamManage.tsx`

- [ ] **步骤 1：运行 Playwright 绿灯回归**

运行：`node _teacher-exam-modal-regression.mjs`

预期：桌面与低高度视口均通过；遮罩覆盖视口、背景锁定、关闭恢复滚动、页面没有横向溢出或控制台错误。

- [ ] **步骤 2：运行教师端生产构建**

运行：`pnpm build`

预期：`tsc && vite build` 退出码为 0；若只有 Vite chunk 大小提示，记录为非阻断警告。

- [ ] **步骤 3：删除临时脚本并检查差异**

删除 `_teacher-exam-modal-regression.mjs`，然后运行：

```powershell
git diff --check
git status --short
```

预期：临时脚本不再存在；本任务生产代码仅修改 `ExamManage.tsx`，不包含当前管理端已有改动。

- [ ] **步骤 4：执行中文代码审查**

按 `[必须修复]`、`[建议修改]`、`[仅供参考]` 检查 Portal 生命周期、滚动恢复、原业务逻辑、响应式布局和变更范围；所有必须修复项在提交前清零。

- [ ] **步骤 5：提交实现**

```powershell
git add -- educloud-frontend/teacher-portal/src/pages/ExamManage.tsx docs/superpowers/plans/2026-08-18-teacher-exam-create-modal.md
git commit -m "fix(教师端): 优化创建考试弹窗"
```

预期：提交只包含计划和教师端考试弹窗；不纳入管理端现有改动。
