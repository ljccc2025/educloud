# 教师端数据分析趋势图修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复教师端报名与收入趋势图的零高度布局，使现有 6 个月 MOCK 数据渲染为可见柱体。

**架构：** 保留 `Analytics.tsx` 现有数据请求、最大值归一化和纯 React/Tailwind 柱状图。只恢复“固定图表高度 → 全高数据列 → 弹性柱体轨道 → 百分比柱体”的完整高度链，不修改 MOCK 数据、类型、接口或依赖。

**技术栈：** React 18、TypeScript 5、Tailwind CSS 3、Lucide React、Vite 5、Playwright 浏览器 DOM 尺寸验证。

---

## 文件结构

- 修改：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx`
  - 为报名与收入图表的每月数据列建立确定高度，并让柱体轨道占据剩余空间。
- 不修改：`educloud-frontend/teacher-portal/src/services/api.ts`
  - 继续提供已有 `enrollmentTrend`、`revenueData` 和 `engagementData` MOCK 数据。
- 不修改：`educloud-frontend/teacher-portal/src/types/index.ts`
  - 现有 `EnrollmentTrend`、`RevenueData` 与 `EngagementData` 类型已经满足需求。

## 工作区保护

`Analytics.tsx` 当前包含用户尚未提交的圆角视觉调整，`api.ts` 还包含作业发布功能修改。执行时只对 `Analytics.tsx` 两组数据列和轨道类名做最小补丁，禁止覆盖、回退、暂存或提交其他用户改动。源代码保持未提交，除非用户明确授权处理混合工作区提交。

### 任务 1：建立零高度趋势图红灯

**文件：**
- 读取：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx:7-21`
- 读取：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx:58-125`
- 读取：`educloud-frontend/teacher-portal/src/services/api.ts:538-569`
- 测试：真实浏览器中的 `/analytics` 页面，不创建项目测试文件

- [ ] **步骤 1：启动教师端开发服务器**

在 `educloud-frontend/teacher-portal` 目录运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 4177
```

预期：Vite 输出 `Local: http://127.0.0.1:4177/`，进程保持运行。

- [ ] **步骤 2：登录并等待 MOCK 数据加载**

打开 `http://127.0.0.1:4177/login`，使用页面预填账号点击“登录工作台”，再进入 `http://127.0.0.1:4177/analytics`。等待“教学数据概览”出现后额外等待至少 `500ms`，覆盖 MOCK API 默认 `300ms` 延迟。

- [ ] **步骤 3：执行柱体尺寸红灯断言**

在浏览器页面上下文执行：

```javascript
const inspectChart = (title) => {
  const heading = [...document.querySelectorAll('h2')]
    .find((node) => node.textContent?.includes(title));
  const card = heading?.closest('.card-editorial');
  const bars = card ? [...card.querySelectorAll('[style*="height"]')] : [];

  return bars.map((bar) => ({
    inlineHeight: bar.style.height,
    renderedHeight: bar.getBoundingClientRect().height,
    parentHeight: bar.parentElement?.getBoundingClientRect().height ?? 0,
  }));
};

({
  enrollment: inspectChart('报名趋势'),
  revenue: inspectChart('收入趋势'),
});
```

预期红灯：两个数组长度均为 `6`；`inlineHeight` 均为非零百分比，但 12 项的 `renderedHeight` 与 `parentHeight` 全部为 `0`。失败原因必须是高度链断裂，而不是数组为空。

- [ ] **步骤 4：确认页面已收到准确 MOCK 值**

读取两张卡片内的文本，预期报名卡包含 `180、245、312、298、420、534`，收入卡包含 `¥18,600、¥24,800、¥31,200、¥28,900、¥42,300、¥48,620`。由此排除请求与数据映射问题。

### 任务 2：恢复报名与收入图表高度链

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx:70-89`
- 修改：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx:105-123`
- 测试：任务 3 的真实浏览器尺寸与数值断言

- [ ] **步骤 1：修复报名趋势数据列和柱体轨道**

将报名趋势循环中的数据列：

```tsx
<div key={d.month} className="flex-1 flex flex-col items-center gap-2 group">
```

改为：

```tsx
<div key={d.month} className="h-full flex-1 flex flex-col items-center gap-2 group">
```

将报名柱体轨道：

```tsx
<div className="w-full relative flex items-end h-full">
```

改为：

```tsx
<div className="w-full relative flex flex-1 min-h-0 items-end">
```

不得修改 `d.count / maxEnrollment`、颜色、悬浮顶线、月份或动画延迟。

- [ ] **步骤 2：修复收入趋势数据列和柱体轨道**

将收入趋势循环中的数据列：

```tsx
<div key={d.month} className="flex-1 flex flex-col items-center gap-2 group">
```

改为：

```tsx
<div key={d.month} className="h-full flex-1 flex flex-col items-center gap-2 group">
```

将收入柱体轨道：

```tsx
<div className="w-full relative flex items-end h-full">
```

改为：

```tsx
<div className="w-full relative flex flex-1 min-h-0 items-end">
```

不得修改 `d.amount / maxRevenue`、绿色渐变、月份或动画延迟。

- [ ] **步骤 3：运行 TypeScript 类型检查**

在 `educloud-frontend/teacher-portal` 目录运行：

```powershell
npm run typecheck
```

预期：退出码 `0`，没有 TypeScript 错误。

### 任务 3：执行趋势图绿灯与响应式回归

**文件：**
- 验证：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx`
- 验证：`educloud-frontend/teacher-portal/src/services/api.ts`

- [ ] **步骤 1：重新执行柱体尺寸断言**

刷新 `/analytics` 并等待 MOCK 数据加载，重复任务 1 的 `inspectChart` 脚本。

预期绿灯：报名和收入各有 6 根柱体；12 项 `parentHeight` 全部大于 `0`，12 项 `renderedHeight` 全部大于 `0`。

- [ ] **步骤 2：验证相对高度与 MOCK 数据一致**

使用以下断言：

```javascript
const enrollmentExpected = [180, 245, 312, 298, 420, 534];
const revenueExpected = [18600, 24800, 31200, 28900, 42300, 48620];

const assertRatios = (actualHeights, expectedValues) => {
  const maxHeight = Math.max(...actualHeights);
  const maxValue = Math.max(...expectedValues);
  return actualHeights.every((height, index) =>
    Math.abs(height / maxHeight - expectedValues[index] / maxValue) < 0.02
  );
};
```

预期：两组比例断言均为 `true`；两张图的 8 月柱体均最高，且 6 月柱体低于 5 月柱体。

- [ ] **步骤 3：验证悬浮数值**

依次悬浮报名 8 月柱体和收入 8 月柱体，等待过渡完成后检查顶部数值标签：

- 报名显示 `534`；
- 收入显示 `¥48,620`；
- 标签计算 `opacity` 为 `1`。

- [ ] **步骤 4：验证学员参与度未受影响**

确认页面仍显示五项参与度与数值：`视频完播 78%`、`作业提交 65%`、`讨论参与 42%`、`直播出勤 58%`、`测验通过 85%`。五条进度条宽度均大于 `0px`。

- [ ] **步骤 5：验证窄屏布局**

将视口设置为 `390 × 844` 并刷新分析页：

- 报名和收入卡片上下排列；
- 每张图仍有 6 根高度大于 `0px` 的柱体；
- 3 月至 8 月月份文字保持可见；
- `document.documentElement.scrollWidth <= window.innerWidth`，没有新增根级横向溢出。

- [ ] **步骤 6：检查浏览器控制台**

预期：本次页面加载、悬浮和响应式切换过程中 `0` 个新增 JavaScript 错误。React Router 现有未来版本提示不属于本次修改范围。

### 任务 4：执行工程验证与工作区复核

**文件：**
- 验证：`educloud-frontend/teacher-portal/src/pages/Analytics.tsx`
- 验证：`educloud-frontend/teacher-portal/package.json`

- [ ] **步骤 1：运行最终类型检查与生产构建**

在 `educloud-frontend/teacher-portal` 目录运行：

```powershell
npm run typecheck
npm run build
```

预期：两个命令退出码均为 `0`，Vite 成功输出教师端生产资源。

- [ ] **步骤 2：运行差异空白检查**

在仓库根目录运行：

```powershell
git diff --check -- 'educloud-frontend/teacher-portal/src/pages/Analytics.tsx'
```

预期：没有空白错误。CRLF 转换提示不属于差异错误。

- [ ] **步骤 3：确认没有修改 MOCK 数据和其他教师端模块**

```powershell
git status --short -- `
  'educloud-frontend/teacher-portal/src/pages/Analytics.tsx' `
  'educloud-frontend/teacher-portal/src/services/api.ts' `
  'educloud-frontend/teacher-portal/src/types/index.ts' `
  'educloud-frontend/teacher-portal/package.json'
```

预期：`Analytics.tsx` 保持修改状态；`api.ts` 只保留任务开始前已有的作业发布改动；类型文件和 `package.json` 不出现本任务新改动。不得暂存或提交混合源文件。
