# 首页暖白轻量 CTA 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将首页底部 CTA 从突兀的深靛蓝广告横幅改为暖白低饱和、双列紧凑的行动引导卡片，同时保持文案和 `/courses` 跳转不变。

**架构：** 仅在 `Home.tsx` 内替换 CTA 区域的 Tailwind 布局和装饰节点，不新增组件、依赖或全局样式。使用语义数据属性支持浏览器验收；桌面端采用文案与按钮双列，移动端自动堆叠，背景装饰不参与交互和辅助技术顺序。

**技术栈：** React 18、TypeScript、React Router 6、Lucide React、Tailwind CSS、Vite、Playwright。

**工作区边界：** 当前工作区包含大量其他未提交改动。本计划只修改 `educloud-frontend/student-portal/src/pages/Home.tsx` 的 CTA 区域；不暂存、不提交、不清理其他生产文件。设计与计划文档可独立提交，生产代码保持未提交、可逆。

---

## 文件结构

- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx:269-296` — CTA 容器、装饰、布局、按钮交互。
- 验证：不新增测试文件；使用工作区内置 Playwright 运行一次性浏览器红绿验收。

### 任务 1：建立 CTA 视觉问题红灯

**文件：**
- 诊断：`educloud-frontend/student-portal/src/pages/Home.tsx:269-296`

- [ ] **步骤 1：启动学生端开发服务**

在 `educloud-frontend/student-portal` 运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 4179
```

预期：Vite 在 `http://127.0.0.1:4179/` 启动。

- [ ] **步骤 2：运行当前 CTA 的失败验收**

使用 Playwright 在 1440×900 视口访问首页，通过标题“准备好开始学习了吗？”定位最近的 `section`，读取容器和装饰：

```js
const heading = page.getByRole('heading', { name: '准备好开始学习了吗？' });
const section = heading.locator('xpath=ancestor::section[1]');
const panel = section.locator('div').first();
const result = await panel.evaluate((node) => ({
  hasDarkPanel: node.classList.contains('bg-indigo-800'),
  hasGridTexture: Boolean(node.querySelector('[style*="background-image"]')),
  hasHugeArrow: Array.from(node.querySelectorAll('div')).some(
    (element) => element.textContent?.trim() === '>' && element.className.includes('section-number')
  ),
  hasSemanticPanel: node.hasAttribute('data-home-cta-panel'),
}));

if (
  result.hasDarkPanel ||
  result.hasGridTexture ||
  result.hasHugeArrow ||
  !result.hasSemanticPanel
) {
  throw new Error(`RED: CTA 仍是旧深色横幅: ${JSON.stringify(result)}`);
}
```

预期：FAIL，并显示 `hasDarkPanel=true`、`hasGridTexture=true`、`hasHugeArrow=true` 或语义面板缺失。这证明测试捕获的是当前视觉根因。

### 任务 2：实现暖白双列 CTA

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx:269-296`

- [ ] **步骤 1：替换 CTA 容器和装饰**

将旧 `bg-indigo-800`、网格纹理和巨大 `>` 删除，替换为：

```tsx
<section
  data-home-cta-section
  className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20"
>
  <div
    data-home-cta-panel
    className="relative overflow-hidden rounded-3xl border border-amber-100 bg-gradient-to-br from-amber-50/90 via-white to-indigo-50/80 p-8 shadow-[0_20px_60px_rgba(30,27,75,0.06)] md:p-10 lg:p-12"
  >
    <div
      aria-hidden="true"
      data-home-cta-decoration
      className="pointer-events-none absolute -right-20 -top-24 h-64 w-64 rounded-full bg-amber-200/25 blur-3xl"
    />
    <div
      aria-hidden="true"
      className="pointer-events-none absolute -bottom-28 right-32 h-64 w-64 rounded-full bg-indigo-200/25 blur-3xl"
    />
    {/* CTA content */}
  </div>
</section>
```

不得保留内联网格 `backgroundImage` 或 `section-number` 巨大箭头；装饰节点必须不可点击并从辅助技术顺序隐藏。

- [ ] **步骤 2：实现桌面双列和移动堆叠**

在暖白面板内加入：

```tsx
<div
  data-home-cta-content
  className="relative grid gap-8 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-12"
>
  <div className="max-w-2xl">
    <h2 className="font-display text-3xl font-bold text-ink-900 md:text-4xl">
      准备好开始学习了吗？
    </h2>
    <p className="mt-4 leading-relaxed text-ink-500">
      加入 50,000+ 名学员，在 EduCloud 开启你的技术成长之路。首单立减 50 元，限时优惠中。
    </p>
  </div>
  {/* CTA link */}
</div>
```

不得增加第二按钮、插图、统计数字、徽章或新文案。

- [ ] **步骤 3：实现品牌靛蓝按钮和无障碍动效**

使用：

```tsx
<Link
  data-home-cta-link
  to="/courses"
  className="group inline-flex min-h-11 items-center justify-center gap-2 self-start rounded-xl bg-indigo-800 px-7 py-3.5 text-sm font-medium text-white transition-all duration-200 hover:bg-indigo-900 hover:shadow-lg hover:shadow-indigo-800/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-2 focus-visible:ring-offset-white motion-reduce:transition-none md:self-auto"
>
  立即开始
  <ArrowRight
    size={16}
    aria-hidden="true"
    className="transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transition-none motion-reduce:!transform-none"
  />
</Link>
```

按钮点击区域至少 44px；`prefers-reduced-motion: reduce` 下不得位移或过渡。

- [ ] **步骤 4：运行类型检查**

运行：

```powershell
npm run typecheck
```

预期：退出码 0。

### 任务 3：运行浏览器绿灯和视觉验收

**文件：**
- 验证：`educloud-frontend/student-portal/src/pages/Home.tsx`

- [ ] **步骤 1：验证桌面端结构和颜色层级**

在 1440×900 下断言：

```js
const section = page.locator('[data-home-cta-section]');
const panel = page.locator('[data-home-cta-panel]');
const content = page.locator('[data-home-cta-content]');
const link = page.locator('[data-home-cta-link]');
const result = await panel.evaluate((node) => ({
  hasDarkPanel: node.classList.contains('bg-indigo-800'),
  hasGridTexture: Boolean(node.querySelector('[style*="background-image"]')),
  hasHugeArrow: Array.from(node.querySelectorAll('.section-number')).some(
    (element) => element.textContent?.trim() === '>'
  ),
  backgroundImage: getComputedStyle(node).backgroundImage,
}));
const columns = await content.evaluate(
  (node) => getComputedStyle(node).gridTemplateColumns.split(' ').length
);

if (result.hasDarkPanel || result.hasGridTexture || result.hasHugeArrow) {
  throw new Error(`旧 CTA 视觉仍存在: ${JSON.stringify(result)}`);
}
if (!result.backgroundImage.includes('linear-gradient') || columns !== 2) {
  throw new Error(`暖白渐变或双列布局缺失: ${JSON.stringify({ result, columns })}`);
}
if ((await link.getAttribute('href')) !== '/courses') {
  throw new Error('CTA 路由发生变化');
}
```

预期：暖白渐变存在、无旧深色/网格/巨大箭头、双列布局、链接仍为 `/courses`。

- [ ] **步骤 2：验证移动端布局和溢出**

在 390×844 下断言：

```js
const columns = await content.evaluate(
  (node) => getComputedStyle(node).gridTemplateColumns.split(' ').length
);
const minHeight = await link.evaluate((node) => Number.parseFloat(getComputedStyle(node).minHeight));
const overflow = await page.evaluate(
  () => document.documentElement.scrollWidth - document.documentElement.clientWidth
);

if (columns !== 1 || minHeight < 44 || overflow !== 0) {
  throw new Error(`移动端 CTA 异常: ${JSON.stringify({ columns, minHeight, overflow })}`);
}
```

预期：单列、按钮高度至少 44px、无横向溢出。

- [ ] **步骤 3：验证 hover、键盘 focus 和 reduced-motion**

普通动效页面：记录按钮背景和箭头 transform，hover 后等待 220ms，断言背景发生变化且箭头 transform 不为 `none`；聚焦按钮后断言 `boxShadow` 或 outline 可见。

新建 reduced-motion 页面并执行：

```js
await reducedPage.emulateMedia({ reducedMotion: 'reduce' });
await reducedPage.goto('http://127.0.0.1:4179/', { waitUntil: 'networkidle' });
const reducedLink = reducedPage.locator('[data-home-cta-link]');
const reducedArrow = reducedLink.locator('svg');
await reducedLink.hover();
const reduced = await reducedArrow.evaluate((node) => ({
  transform: getComputedStyle(node).transform,
  transitionProperty: getComputedStyle(node).transitionProperty,
}));
if (reduced.transform !== 'none' || reduced.transitionProperty !== 'none') {
  throw new Error(`reduced-motion 未关闭动效: ${JSON.stringify(reduced)}`);
}
```

预期：普通模式保留轻量反馈，键盘 focus 可见，reduced-motion 为 `transform=none`、`transitionProperty=none`。

- [ ] **步骤 4：验证真实点击和截图**

点击 `[data-home-cta-link]` 后等待 URL `**/courses`，断言 pathname 为 `/courses`。分别保存 1440px CTA 区域截图和 390px CTA 区域截图，人工检查暖白色阶、文字换行和按钮位置。

### 任务 4：工程验收与独立审查

**文件：**
- 验证：`educloud-frontend/student-portal/src/pages/Home.tsx`

- [ ] **步骤 1：执行生产构建**

运行：

```powershell
npm run build
```

预期：TypeScript 编译和 Vite 构建成功，退出码 0。

- [ ] **步骤 2：执行限定差异检查**

运行：

```powershell
git diff --check -- educloud-frontend/student-portal/src/pages/Home.tsx
git status --short -- educloud-frontend/student-portal/src/pages/Home.tsx
```

预期：`diff --check` 退出码 0；`Home.tsx` 保持未提交，不暂存或清理工作区其他文件。

- [ ] **步骤 3：请求独立代码审查**

审查范围仅限 `Home.tsx` CTA 区域，对照 `docs/superpowers/specs/2026-08-20-home-cta-lightweight-design.md` 检查颜色层级、响应式布局、路由、focus 和 reduced-motion。忽略同一文件中 SideRays、分类区等既有差异；Critical/Important 必须修复并重新运行相关验收。

- [ ] **步骤 4：停止开发服务并交付**

停止 4179 端口的 Vite 服务，报告浏览器、类型检查、构建、差异检查和代码审查的实际结果。不要创建生产提交，保持视觉修改可逆。
