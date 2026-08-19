# 顶部导航栏滚动过渡实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让学生端顶部药丸导航栏在滚动时以可插值的宽度和圆角平滑展开，并在回到顶部时平滑恢复。

**架构：** 保留 `Navbar` 现有滚动状态和 RAF 节流，仅将视觉状态从不可插值的 Tailwind `max-w-none`/`rounded-none` 切换改为显式 CSS 数值过渡。内层表面保持居中布局，宽度约束从 1280px 连续过渡到 100vw，圆角从 28px 连续过渡到 0。

**技术栈：** React 18、TypeScript、Tailwind CSS、Vite、Playwright MCP 浏览器验收。

---

### 任务 1：建立导航栏动画中间态红灯

**文件：**
- 诊断对象：`educloud-frontend/student-portal/src/components/Navbar.tsx`
- 测试方式：全新 Playwright 页面中的浏览器脚本，不新增生产测试依赖

- [ ] **步骤 1：运行当前实现的动画采样检查**

```ts
const samples = await page.evaluate(async () => {
  const surface = document.querySelector('[data-navbar-surface]');
  const sample = () => {
    const style = getComputedStyle(surface);
    return {
      width: surface.getBoundingClientRect().width,
      maxWidth: style.maxWidth,
      radius: parseFloat(style.borderTopLeftRadius),
    };
  };
  window.scrollTo(0, 160);
  await new Promise(resolve => setTimeout(resolve, 160));
  return sample();
});
if (samples.maxWidth === 'none' || samples.radius > 28) {
  throw new Error(`动画中间态不可插值: ${JSON.stringify(samples)}`);
}
```

- [ ] **步骤 2：确认红灯因当前 CSS 状态缺陷失败**

预期：FAIL，采样结果包含 `maxWidth: "none"` 或圆角大于实际药丸半径；这证明测试捕获的是当前瞬变根因，而不是选择器错误。

### 任务 2：实现可插值的导航栏状态

**文件：**
- 修改：`educloud-frontend/student-portal/src/components/Navbar.tsx:78-105`

- [ ] **步骤 1：把内层表面改为显式数值状态**

保留现有 `data-navbar-surface` 和内容结构，将状态类改为：顶部状态使用 `max-w-[80rem]`、`rounded-[28px]`，滚动态使用 `max-w-[100vw]`、`rounded-[0px]`，两种状态都保留 `mx-auto`。

- [ ] **步骤 2：声明可插值过渡属性**

将过渡属性限定为 `max-width, border-radius, height, background-color, box-shadow, padding`，时长 520ms，曲线 `cubic-bezier(0.4,0,0.2,1)`；不要再依赖 `max-w-none` 或 `rounded-full` 这种不可控的离散值。

- [ ] **步骤 3：保持 reduced-motion 和现有交互不变**

不新增滚动监听；保留当前 RAF 清理逻辑、移动菜单、搜索、通知、购物车和个人中心行为。

### 任务 3：运行红绿回归检查

**文件：**
- 验证：`educloud-frontend/student-portal/src/components/Navbar.tsx`

- [ ] **步骤 1：重新运行动画采样检查**

分别在滚动后约 100ms、300ms、520ms 采样，断言宽度单调增加、圆角单调下降且所有中间圆角均不大于 28px；结束时断言表面宽度等于视口宽度、圆角为 0。

- [ ] **步骤 2：验证反向过渡**

滚回 `scrollY=0`，等待 520ms，断言恢复 `max-width: 1280px`（桌面端）和 `border-radius: 28px`。

### 任务 4：完成跨端和构建验收

**文件：**
- 验证：`educloud-frontend/student-portal/src/components/Navbar.tsx`

- [ ] **步骤 1：桌面端和移动端浏览器验收**

桌面端使用 1440×900，移动端使用 390×844；检查无横向溢出、移动菜单可打开、导航项可点击、控制台无新增错误。

- [ ] **步骤 2：执行静态检查**

运行：`npm run typecheck`（工作目录 `educloud-frontend/student-portal`）

预期：退出码 0。

- [ ] **步骤 3：执行生产构建**

运行：`npm run build`（工作目录 `educloud-frontend/student-portal`）

预期：Vite 构建完成且退出码 0。

- [ ] **步骤 4：执行差异格式检查**

运行：`git diff --check -- educloud-frontend/student-portal/src/components/Navbar.tsx`

预期：无空白错误；保留工作区中与本任务无关的既有改动，不进行清理或重置。
