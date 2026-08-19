# 首页课程分类轻量化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将学生端首页课程分类区改为浅色、平衡网格和语义图标组成的轻量分类导航，消除当前深色大卡片的突兀感。

**架构：** 继续在 `Home.tsx` 内渲染现有 `categories` 数据，不新增业务组件或数据接口。复用 `section-label`、`display-heading` 和 `card-editorial` 设计系统类，仅增加本地 Lucide 图标映射、分类区语义数据属性和响应式布局类；保持分类链接、首页其他区块与现有滚动背景不变。

**技术栈：** React 18、TypeScript、React Router、Lucide React、Tailwind CSS、Vite、Playwright MCP。

**工作区边界：** 当前工作区包含大量其他未提交改动。实施时只修改 `Home.tsx`，不暂存、不提交、不清理其他文件；每个检查点使用路径限定的 `git diff --check` 验证。规格文档和本计划文档独立提交，生产代码保持可逆的未提交状态。

---

### 任务 1：建立分类区视觉回归红灯

**文件：**
- 诊断对象：`educloud-frontend/student-portal/src/pages/Home.tsx`
- 测试方式：全新 Playwright 页面中的浏览器脚本，不新增生产测试依赖

- [ ] **步骤 1：启动学生端预览服务**

在工作目录 `educloud-frontend/student-portal` 运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 4179
```

预期：Vite 在 `http://127.0.0.1:4179/` 启动并可访问首页。

- [ ] **步骤 2：运行当前实现的基线检查**

使用全新 Playwright 页面运行：

```ts
async (page) => {
  const cleanPage = await page.context().newPage();
  try {
    await cleanPage.setViewportSize({ width: 1440, height: 900 });
    await cleanPage.goto('http://127.0.0.1:4179/', { waitUntil: 'networkidle' });
    const result = await cleanPage.evaluate(() => {
      const heading = Array.from(document.querySelectorAll('h2'))
        .find((node) => node.textContent?.trim() === '探索你的方向');
      const section = heading?.closest('section');
      const panel = section?.querySelector('.bg-indigo-800');
      const grid = section?.querySelector('.grid');
      const iconTexts = section
        ? Array.from(section.querySelectorAll('.font-display.text-3xl')).map((node) => node.textContent?.trim())
        : [];
      return {
        hasDarkPanel: Boolean(panel),
        gridColumns: grid ? getComputedStyle(grid).gridTemplateColumns.split(' ').length : 0,
        categoryCount: section?.querySelectorAll('a').length ?? 0,
        plusCount: iconTexts.filter((text) => text === '+').length,
      };
    });
    if (!result.hasDarkPanel || result.gridColumns !== 6 || result.plusCount === 0) {
      throw new Error(`基线没有捕获原始问题: ${JSON.stringify(result)}`);
    }
    throw new Error(`RED: 分类区仍是深色 6 列并含占位图标: ${JSON.stringify(result)}`);
  } finally {
    await cleanPage.close();
  }
}
```

预期：FAIL，结果显示仍存在深色面板、6 列网格和 `+` 图标；这证明红灯对应当前视觉根因。

### 任务 2：建立语义图标映射和轻量分类布局

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx:1-25`
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx:181-224`

- [ ] **步骤 1：替换字符串图标映射**

从 `lucide-react` 增加以下图标和类型导入：

```tsx
import type { LucideIcon } from 'lucide-react';
import {
  ArrowRight,
  ArrowUpRight,
  Award,
  BarChart3,
  BookOpen,
  Brain,
  Calculator,
  Languages,
  Lightbulb,
  Monitor,
  Music2,
  Palette,
  PenTool,
  Scale,
  TrendingUp,
  Users,
} from 'lucide-react';
```

将旧的字符串映射替换为：

```tsx
const categoryIcons: Record<string, LucideIcon> = {
  计算机: Monitor,
  数学: Calculator,
  语言学习: Languages,
  经济管理: BarChart3,
  文学艺术: Palette,
  设计: PenTool,
  心理学: Brain,
  法律: Scale,
  音乐: Music2,
  哲学: Lightbulb,
};
```

保留现有 `ArrowRight`、`BookOpen`、`Award`、`TrendingUp`、`Users` 导入；如果 `ArrowRight` 仍被其他区块使用，不删除它。

- [ ] **步骤 2：替换分类区外层容器**

保留外层 `<section>` 的页面宽度和垂直间距，加入 `data-home-category-section`，删除内部 `bg-indigo-800`、`rounded-3xl`、`p-10 md:p-16` 和网格纹理层，改为以下结构：

```tsx
<section
  data-home-category-section
  className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20"
>
  <div className="relative">
    <span aria-hidden="true" className="section-number absolute right-4 top-0">
      02
    </span>
    {/* heading and category grid */}
  </div>
</section>
```

分类区不得引入新的全宽深色背景，也不得修改相邻精选课程和 CTA 的外层结构。

- [ ] **步骤 3：实现左对齐标题与辅助说明**

使用现有设计系统类替换原居中白色标题：

```tsx
<div className="relative grid gap-5 mb-10 md:grid-cols-[minmax(0,1fr)_minmax(18rem,28rem)] md:items-end md:gap-12">
  <div>
    <span className="section-label mb-4">课程分类</span>
    <h2 className="display-heading text-4xl md:text-5xl mt-4">
      探索你的方向
    </h2>
  </div>
  <p className="max-w-md text-sm leading-7 text-ink-500">
    从感兴趣的领域开始，找到适合当前阶段的课程，把注意力留给真正想学习的内容。
  </p>
</div>
```

标题和说明必须保持真实文本，不能用图片或装饰字符替代。

- [ ] **步骤 4：实现 5×2 分类卡片**

将网格和卡片替换为：

```tsx
<div
  data-home-category-grid
  className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 md:gap-4"
>
  {categories.map((cat) => {
    const Icon = categoryIcons[cat.name] ?? BookOpen;
    return (
      <Link
        key={cat.name}
        data-home-category-card
        to="/courses"
        className="card-editorial group relative flex min-h-[150px] flex-col justify-between p-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-2 motion-reduce:transition-none"
      >
        <div className="flex items-start justify-between gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-800 transition-colors duration-200 group-hover:bg-amber-50 group-hover:text-amber-700">
            <Icon size={20} strokeWidth={1.7} aria-hidden="true" />
          </span>
          <ArrowUpRight
            size={17}
            strokeWidth={1.5}
            aria-hidden="true"
            className="text-ink-300 transition-all duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-amber-600"
          />
        </div>
        <div>
          <p className="font-display text-base font-bold text-ink-900">
            {cat.name}
          </p>
          <p className="mt-1 text-xs text-ink-400">{cat.courseCount} 门课程</p>
        </div>
      </Link>
    );
  })}
</div>
```

不要删除分类链接，不要把箭头改成单独的点击目标；整张卡片必须保持可点击。

- [ ] **步骤 5：运行类型检查确认实现可编译**

运行：

```powershell
npm run typecheck
```

预期：退出码 0；`LucideIcon` 映射和 `categories.map` 无 TypeScript 错误。

### 任务 3：运行分类区浏览器绿灯和可访问性验收

**文件：**
- 验证：`educloud-frontend/student-portal/src/pages/Home.tsx`
- 不新增测试文件，使用 Playwright 一次性浏览器脚本

- [ ] **步骤 1：验证桌面端结构与视觉状态**

使用全新页面、1440×900 视口运行：

```ts
async (page) => {
  const cleanPage = await page.context().newPage();
  try {
    await cleanPage.setViewportSize({ width: 1440, height: 900 });
    await cleanPage.goto('http://127.0.0.1:4179/', { waitUntil: 'networkidle' });
    const result = await cleanPage.evaluate(() => {
      const section = document.querySelector('[data-home-category-section]');
      const grid = document.querySelector('[data-home-category-grid]');
      const cards = Array.from(document.querySelectorAll('[data-home-category-card]'));
      if (!section || !grid) throw new Error('分类区语义节点缺失');
      const sectionStyle = getComputedStyle(section);
      const gridStyle = getComputedStyle(grid);
      return {
        cardCount: cards.length,
        columns: gridStyle.gridTemplateColumns.split(' ').length,
        sectionBackground: sectionStyle.backgroundColor,
        darkPanelCount: section.querySelectorAll('.bg-indigo-800').length,
        plusCount: cards.filter((card) => card.textContent?.trim().startsWith('+')).length,
        cardsHaveLinks: cards.every((card) => card.tagName === 'A' && card.getAttribute('href') === '/courses'),
        overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      };
    });
    if (result.cardCount !== 10 || result.columns !== 5) throw new Error(`桌面网格不符合 5×2: ${JSON.stringify(result)}`);
    if (result.darkPanelCount !== 0 || result.plusCount !== 0) throw new Error(`旧深色面板或占位图标仍存在: ${JSON.stringify(result)}`);
    if (!result.cardsHaveLinks || result.overflow !== 0) throw new Error(`分类入口或溢出异常: ${JSON.stringify(result)}`);
    return result;
  } finally {
    await cleanPage.close();
  }
}
```

预期：10 张卡片、5 列、无深色大面板、无 `+` 占位图标、所有入口保持 `/courses`、无横向溢出。

- [ ] **步骤 2：验证移动端、键盘 focus 和 reduced-motion**

使用 390×844 视口运行：

```ts
async (page) => {
  const cleanPage = await page.context().newPage();
  try {
    await cleanPage.setViewportSize({ width: 390, height: 844 });
    await cleanPage.emulateMedia({ reducedMotion: 'reduce' });
    await cleanPage.goto('http://127.0.0.1:4179/', { waitUntil: 'networkidle' });
    const result = await cleanPage.evaluate(() => {
      const grid = document.querySelector('[data-home-category-grid]');
      const firstCard = document.querySelector('[data-home-category-card]');
      if (!grid || !firstCard) throw new Error('移动端分类节点缺失');
      const gridStyle = getComputedStyle(grid);
      const firstStyle = getComputedStyle(firstCard);
      return {
        columns: gridStyle.gridTemplateColumns.split(' ').length,
        minHeight: Number.parseFloat(firstStyle.minHeight),
        overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        transitionProperty: firstStyle.transitionProperty,
      };
    });
    const firstCard = cleanPage.locator('[data-home-category-card]').first();
    await firstCard.focus();
    const focusVisible = await firstCard.evaluate((node) => {
      const style = getComputedStyle(node);
      return style.outlineStyle !== 'none' || style.boxShadow !== 'none';
    });
    if (result.columns !== 2 || result.minHeight < 44 || result.overflow !== 0) {
      throw new Error(`移动端布局异常: ${JSON.stringify(result)}`);
    }
    if (result.transitionProperty !== 'none' || !focusVisible) {
      throw new Error(`可访问性状态异常: ${JSON.stringify({ result, focusVisible })}`);
    }
    return { ...result, focusVisible };
  } finally {
    await cleanPage.close();
  }
}
```

预期：2 列、卡片高度至少 44px、无横向溢出、reduced-motion 关闭动画、键盘 focus 可见。

- [ ] **步骤 3：验证分类卡片 hover 反馈与控制台**

使用桌面端全新页面运行：

```ts
async (page) => {
  const cleanPage = await page.context().newPage();
  const errors: string[] = [];
  cleanPage.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  try {
    await cleanPage.setViewportSize({ width: 1440, height: 900 });
    await cleanPage.goto('http://127.0.0.1:4179/', { waitUntil: 'networkidle' });
    const card = cleanPage.locator('[data-home-category-card]').first();
    const icon = card.locator('span').first();
    const before = await icon.evaluate((node) => getComputedStyle(node).backgroundColor);
    await card.hover();
    await cleanPage.waitForTimeout(260);
    const after = await icon.evaluate((node) => getComputedStyle(node).backgroundColor);
    if (before === after) throw new Error(`分类卡片 hover 没有改变图标底色: ${before}`);
    if (errors.length > 0) throw new Error(`浏览器控制台存在错误: ${JSON.stringify(errors)}`);
    return { before, after, errors };
  } finally {
    await cleanPage.close();
  }
}
```

预期：图标底色从浅靛蓝变为浅琥珀，控制台 error 数组为空。

### 任务 4：完成工程验收并保留工作区边界

**文件：**
- 验证：`educloud-frontend/student-portal/src/pages/Home.tsx`

- [ ] **步骤 1：执行生产构建**

运行：

```powershell
npm run build
```

预期：TypeScript 编译成功，Vite 构建成功并退出码 0。

- [ ] **步骤 2：执行限定差异检查**

运行：

```powershell
git diff --check -- educloud-frontend/student-portal/src/pages/Home.tsx
```

预期：退出码 0；允许 Git 报告换行符提示，不得出现空白错误。

- [ ] **步骤 3：核对变更范围**

运行：

```powershell
git status --short -- educloud-frontend/student-portal/src/pages/Home.tsx
git diff --stat -- educloud-frontend/student-portal/src/pages/Home.tsx
```

预期：生产改动只集中在 `Home.tsx`；不暂存、不提交其他路径，不清理用户已有改动。

- [ ] **步骤 4：停止开发预览服务并交付检查结果**

停止 `4179` 开发服务器，报告浏览器、类型检查、构建和差异检查的实际输出；不创建生产提交，保持改动可逆。
