# 教师端作业批改选择状态实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 消除教师端切换作业时右侧详情的瞬移，并按作业恢复老师上次查看或批改的学生。

**架构：** 将学生选择状态提升到 `AssignmentGrade`，用 `Record<assignmentId, submissionId>` 保存当前页面会话内每份作业的最后选择。`GradeSheet` 改为受控组件；父页面保证传入的学生 ID 对当前作业有效，否则同步回退到第一份提交。

**技术栈：** React 18、TypeScript、Playwright Core、Vite。

---

## 文件结构

- 修改：`educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx`——维护按作业隔离的学生选择映射并计算安全回退值。
- 修改：`educloud-frontend/teacher-portal/src/components/GradeSheet.tsx`——接收受控学生 ID 和选择回调，移除跨作业泄漏的内部状态。
- 临时测试：`_teacher-assignment-grade-regression.mjs`——Playwright 回归脚本，全部验证后删除，不提交。
- 不修改：教师端 API、作业与提交类型、评分保存逻辑、现有页面视觉样式。

### 任务 1：建立修复前失败回归

**文件：**
- 创建：`_teacher-assignment-grade-regression.mjs`
- 读取：`educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx`
- 读取：`educloud-frontend/teacher-portal/src/components/GradeSheet.tsx`

- [ ] **步骤 1：编写 Playwright 回归脚本**

脚本通过教师身份进入 `/assignments`，验证切换第二份作业后详情直接显示第一位学生，再为前两份作业各选择不同学生并往返切换，验证每份作业恢复自身最后选择：

```js
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/LEIJIA~1/AppData/Local/Temp/educloud-live-playwright/node_modules/playwright-core');
const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
});
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addInitScript(() => localStorage.setItem('teacher_token', 'playwright-teacher-token'));
const page = await context.newPage();
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(message.text());
});

await page.goto('http://127.0.0.1:5174/assignments', { waitUntil: 'networkidle' });
const assignmentTwo = page.getByRole('button', { name: /作业二：Pandas/ });
await assignmentTwo.click();
assert.equal(await page.getByText('孙明轩', { exact: true }).count(), 2, '切换作业后未直接显示默认学生详情');
assert.equal(await page.getByText('请从左侧选择一份提交进行批改').count(), 0, '切换作业时出现了空详情');

await page.getByRole('button', { name: /实验一：实现一个 RESTful/ }).click();
await page.getByRole('button', { name: /王梓涵/ }).click();
assert.equal(await page.getByRole('heading', { name: '王梓涵' }).count(), 1);

await assignmentTwo.click();
await page.getByRole('button', { name: /周雅婷/ }).click();
assert.equal(await page.getByRole('heading', { name: '周雅婷' }).count(), 1);

await page.getByRole('button', { name: /实验一：实现一个 RESTful/ }).click();
assert.equal(await page.getByRole('heading', { name: '王梓涵' }).count(), 1, '未恢复第一份作业上次选择的学生');
await assignmentTwo.click();
assert.equal(await page.getByRole('heading', { name: '周雅婷' }).count(), 1, '未恢复第二份作业上次选择的学生');
assert.deepEqual(errors, [], '页面产生控制台错误');

await browser.close();
```

- [ ] **步骤 2：运行脚本并确认当前实现失败**

运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 5174
node _teacher-assignment-grade-regression.mjs
```

预期：第一次切换到“作业二”后失败，因为旧学生 ID 不属于新作业，右侧显示空详情。

### 任务 2：用受控状态修复跨作业选择泄漏

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx`
- 修改：`educloud-frontend/teacher-portal/src/components/GradeSheet.tsx`

- [ ] **步骤 1：在父页面保存每份作业的最后选择**

在 `AssignmentGrade` 增加映射状态：

```tsx
const [selectedSubmissionIds, setSelectedSubmissionIds] = useState<Record<string, string>>({});
```

为当前作业计算有效选择；记录存在且仍属于当前作业时使用记录，否则回退到第一份提交：

```tsx
const rememberedSubmissionId = selected ? selectedSubmissionIds[selected.id] : undefined;
const selectedSubmissionId = selected
  ? selected.submissions.some((submission) => submission.id === rememberedSubmissionId)
    ? rememberedSubmissionId ?? ''
    : selected.submissions[0]?.id ?? ''
  : '';
```

把选择值和回调传入 `GradeSheet`：

```tsx
<GradeSheet
  submissions={selected.submissions}
  totalScore={selected.totalScore}
  selectedSubmissionId={selectedSubmissionId}
  onSelectSubmission={(submissionId) =>
    setSelectedSubmissionIds((current) => ({
      ...current,
      [selected.id]: submissionId,
    }))
  }
  onGrade={handleGrade}
/>
```

- [ ] **步骤 2：把 `GradeSheet` 改为受控选择组件**

扩展属性并删除内部 `selectedId` 状态：

```tsx
interface GradeSheetProps {
  submissions: Submission[];
  totalScore: number;
  selectedSubmissionId: string;
  onSelectSubmission: (submissionId: string) => void;
  onGrade: (submissionId: string, score: number, feedback: string) => void;
}
```

使用受控值查找和切换学生：

```tsx
const selected = submissions.find((submission) => submission.id === selectedSubmissionId);
```

```tsx
onClick={() => onSelectSubmission(sub.id)}
```

列表激活判断统一改为 `selectedSubmissionId === sub.id`。保留评分草稿、反馈草稿和保存成功提示的现有本地状态。

- [ ] **步骤 3：运行类型检查**

运行：`npm run typecheck`

预期：退出码为 0，无 TypeScript 错误。

### 任务 3：验证行为、构建并审查

**文件：**
- 使用并删除：`_teacher-assignment-grade-regression.mjs`
- 检查：`educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx`
- 检查：`educloud-frontend/teacher-portal/src/components/GradeSheet.tsx`

- [ ] **步骤 1：运行 Playwright 回归**

运行：`node _teacher-assignment-grade-regression.mjs`

预期：切换作业后始终显示有效学生详情；两份作业均恢复各自最后选择；空详情断言和控制台错误断言通过。

- [ ] **步骤 2：运行生产构建**

运行：`npm run build`

预期：`tsc && vite build` 退出码为 0。

- [ ] **步骤 3：删除临时回归脚本并检查差异**

运行：

```powershell
git diff --check
git status --short
```

预期：仅保留两个生产文件以及本设计/计划文档，不保留临时脚本、构建目录或截图。

- [ ] **步骤 4：使用中文代码审查规范复核**

重点检查：选择 ID 是否始终属于当前作业；无提交时是否安全显示空详情；评分刷新后是否保留选择；是否未引入本地存储、接口或视觉改动。按 `[必须修复]`、`[建议修改]`、`[仅供参考]` 分级记录，所有必须修复项清零后才提交。

### 任务 4：提交与集成

- [ ] **步骤 1：提交隔离分支**

运行：

```powershell
git add docs/superpowers/specs/2026-08-18-teacher-assignment-grade-selection-design.md docs/superpowers/plans/2026-08-18-teacher-assignment-grade-selection.md educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx educloud-frontend/teacher-portal/src/components/GradeSheet.tsx
git commit -m "fix(教师端): 修复作业批改选择状态"
```

- [ ] **步骤 2：快进合并到主分支并推送**

在主工作区运行：

```powershell
git merge --ff-only codex/fix-teacher-assignment-grade
git push origin main
```

合并前后都检查管理端未提交文件仍保持原状；不暂存、不提交、不清理这些文件。

- [ ] **步骤 3：核对远程提交**

运行：

```powershell
git log -1 --oneline
git ls-remote origin refs/heads/main
git status --short --branch
```

预期：本地 `main` 与远程 `main` 指向同一提交，原有管理端未提交修改仍完整保留。
