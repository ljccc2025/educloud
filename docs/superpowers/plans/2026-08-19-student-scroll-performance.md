# 学生端滚动性能优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 保留学生端首页 Galaxy 动态星空，同时消除高分屏滚动掉帧和离屏持续 WebGL 绘制。

**架构：** 在 Galaxy 组件内部统一管理受控 DPR、30 FPS 绘制节流和可见性生命周期；首页只传递视觉参数并关闭当前不可达的鼠标交互。使用真实 Chromium 的 DPR 2、4 倍 CPU 降速场景完成同一套红绿性能回归，不引入新的项目依赖或测试框架。

**技术栈：** React 18、TypeScript、OGL、Vite、Chromium/Playwright 浏览器性能 API

---

## 文件结构

- 修改：`educloud-frontend/student-portal/src/components/Galaxy/Galaxy.tsx`
  - 修正 Renderer DPR 与 Canvas 尺寸；实现限帧、离屏/后台暂停、减少动态效果、ResizeObserver 和完整清理。
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx`
  - 保留现有星空参数，明确关闭不可达的鼠标交互，并传入性能上限。
- 验证：真实浏览器一次性性能回归
  - 通过现有 Playwright 浏览器能力注入 WebGL draw 计数、DPR 2、CPU 4 倍降速和滚动采样；不向项目增加 Playwright 依赖。

当前 `Galaxy` 目录为用户工作区中的未跟踪视觉改动，`Home.tsx` 也包含既有用户修改。执行时只修改上述性能相关代码，不提交源码，避免把用户改动归入新的提交；设计与计划文档可以独立提交。

### 任务 1：建立 Galaxy 性能红灯

**文件：**
- 测试：真实浏览器运行时断言，不创建仓库文件

- [ ] **步骤 1：记录学生端静态基线**

运行：

```powershell
cd educloud-frontend/student-portal
npm run typecheck
npm run build
```

预期：两条命令均退出码 `0`。如果基线失败，停止并记录现有失败，不进入生产代码修改。

- [ ] **步骤 2：启动不修改依赖的本地服务**

运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 4176
```

说明：不要运行会触发当前 pnpm `approve-builds` 策略的安装命令；直接复用现有 `node_modules`。

- [ ] **步骤 3：注入 WebGL draw 计数并运行失败断言**

在 Playwright 新页面初始化前包装 WebGL draw 方法：

```js
await page.addInitScript(() => {
  window.__galaxyDrawCount = 0;
  for (const ctor of [window.WebGLRenderingContext, window.WebGL2RenderingContext]) {
    if (!ctor) continue;
    const original = ctor.prototype.drawArrays;
    ctor.prototype.drawArrays = function (...args) {
      window.__galaxyDrawCount += 1;
      return original.apply(this, args);
    };
  }
});
```

使用 CDP 设置：

```js
await client.send('Emulation.setDeviceMetricsOverride', {
  width: 1440,
  height: 900,
  deviceScaleFactor: 2,
  mobile: false,
});
await client.send('Emulation.setCPUThrottlingRate', { rate: 4 });
```

采集并断言：

```js
const visibleDraws = await countDrawsFor(1000);
const size = await readCanvasAndContainerSize();
const scroll = await measureThreeSecondScroll();

expect(visibleDraws).toBeLessThanOrEqual(35);
expect(size.canvasCssWidth).toBeLessThanOrEqual(size.containerCssWidth + 1);
expect(size.canvasWidth).toBeLessThanOrEqual(size.containerCssWidth * 1.5 + 1);
expect(scroll.fps).toBeGreaterThanOrEqual(110);
expect(scroll.p95Ms).toBeLessThanOrEqual(12);
```

预期：当前实现正确失败。已知现状为约 `120` 次/秒绘制、Canvas `2864 × 1378`、滚动约 `84.3–84.8 FPS`。

- [ ] **步骤 4：验证离屏与减少动态效果红灯**

```js
window.scrollTo(0, document.documentElement.scrollHeight);
await waitForAnimationFrames(2);
expect(await countDrawsFor(1000)).toBeLessThanOrEqual(1);

await page.emulateMedia({ reducedMotion: 'reduce' });
await page.reload();
expect(await countDrawsFor(1000)).toBeLessThanOrEqual(1);
```

预期：当前实现两项均失败，Galaxy 在离屏和 reduced-motion 下仍持续绘制。

### 任务 2：实现自适应 Galaxy 渲染

**文件：**
- 修改：`educloud-frontend/student-portal/src/components/Galaxy/Galaxy.tsx:173-357`

- [ ] **步骤 1：稳定默认属性并增加性能参数**

在组件外定义稳定常量和默认预算：

```ts
const DEFAULT_FOCAL: [number, number] = [0.5, 0.5];
const DEFAULT_ROTATION: [number, number] = [1, 0];
const DEFAULT_MAX_DPR = 1.5;
const DEFAULT_MAX_FPS = 30;
```

在 `GalaxyProps` 增加：

```ts
maxDpr?: number;
maxFps?: number;
```

组件参数使用模块级默认值，不在每次渲染时创建新的数组。

- [ ] **步骤 2：修正 Renderer DPR 和尺寸**

使用受控 DPR 创建 Renderer：

```ts
const dpr = Math.min(Math.max(window.devicePixelRatio || 1, 1), maxDpr);
const renderer = new Renderer({
  alpha: transparent,
  premultipliedAlpha: false,
  dpr,
});
```

尺寸更新只传 CSS 尺寸：

```ts
function resize() {
  const width = Math.max(1, container.clientWidth);
  const height = Math.max(1, container.clientHeight);
  renderer.setSize(width, height);
  program.uniforms.uResolution.value = new Color(
    gl.canvas.width,
    gl.canvas.height,
    gl.canvas.width / gl.canvas.height,
  );
}
```

删除现有 `container.offsetWidth * scale` 和 `container.offsetHeight * scale` 逻辑。

- [ ] **步骤 3：分离单帧绘制和受限调度**

建立实际绘制函数：

```ts
const frameInterval = 1000 / Math.max(1, maxFps);

function renderFrame(time: number, animate: boolean) {
  if (animate && !disableAnimation) {
    program.uniforms.uTime.value = time * 0.001;
    program.uniforms.uStarSpeed.value = (time * 0.001 * starSpeed) / 10;
  }
  // 仅在 mouseInteraction 为 true 时更新鼠标平滑 uniform。
  renderer.render({ scene: mesh });
}
```

建立限帧 RAF：

```ts
function tick(time: number) {
  animateId = 0;
  if (!isActive()) return;
  if (time - lastRenderAt >= frameInterval) {
    lastRenderAt = time;
    renderFrame(time, true);
  }
  animateId = requestAnimationFrame(tick);
}
```

`schedule()` 必须保证最多存在一个 RAF；inactive 时取消 RAF，不允许继续调用 `renderer.render`。

- [ ] **步骤 4：接入可见性、标签页和 reduced-motion**

维护三个运行条件：

```ts
let isIntersecting = true;
let isPageVisible = document.visibilityState === 'visible';
const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
let reduceMotion = motionQuery.matches;

const isActive = () => isIntersecting && isPageVisible && !reduceMotion;
```

使用 `IntersectionObserver` 更新 `isIntersecting`；监听 `visibilitychange` 与媒体查询 `change`。任一条件变化后调用统一 `schedule()`；进入 reduced-motion 时取消循环并渲染一个静态帧。

- [ ] **步骤 5：使用 ResizeObserver 并完成清理**

使用 `ResizeObserver` 观察容器，尺寸回调通过一个独立 RAF 合并；更新尺寸后渲染静态帧或恢复调度。

清理顺序必须包括：

```ts
cancelAnimationFrame(animateId);
cancelAnimationFrame(resizeFrameId);
intersectionObserver.disconnect();
resizeObserver.disconnect();
document.removeEventListener('visibilitychange', handleVisibilityChange);
motionQuery.removeEventListener('change', handleMotionPreferenceChange);
// 移除 mouse 监听、Canvas，并 loseContext。
```

WebGL 初始化用局部 `try/catch` 保护；初始化失败时不抛出到 React 页面。

- [ ] **步骤 6：运行类型检查**

运行：

```powershell
cd educloud-frontend/student-portal
npm run typecheck
```

预期：退出码 `0`，没有 TypeScript 错误。

### 任务 3：首页接入性能预算

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx:40-53`

- [ ] **步骤 1：关闭无效鼠标交互并显式传入预算**

保留现有视觉参数，只修改：

```tsx
<Galaxy
  // 现有 density/hue/glow/speed 参数保持不变
  mouseInteraction={false}
  mouseRepulsion={false}
  maxDpr={1.5}
  maxFps={30}
  transparent
/>
```

不修改 Hero 高度、渐变、文案、卡片或导航栏。

- [ ] **步骤 2：运行红灯同源的绿色浏览器断言**

重新运行任务 1 的完整脚本。预期：

- 可见状态 1 秒 WebGL draw 次数处于 `25–35`；
- Canvas CSS 尺寸不超过容器；
- backing store 不超过容器 CSS 尺寸的 `1.5` 倍；
- Galaxy 离屏 1 秒 draw 增量不超过 `1`；
- reduced-motion 1 秒 draw 增量不超过 `1`；
- 首页滚动平均帧率不低于 `110 FPS`；
- P95 帧间隔不高于 `12 ms`；
- 没有超过 `50 ms` 的 Long Task。

- [ ] **步骤 3：验证恢复和资源释放**

```js
// 从离屏位置回到顶部。
window.scrollTo(0, 0);
expect(await countDrawsFor(1000)).toBeGreaterThanOrEqual(25);

// 导航离开首页。
await page.goto('/courses');
expect(await page.locator('canvas').count()).toBe(0);
```

预期：回到顶部后动画恢复；离开首页后 Canvas 被移除，控制台没有 WebGL 错误。

- [ ] **步骤 4：验证桌面和移动端视觉**

桌面使用 `1440 × 900`，移动端使用 `390 × 844`：

- 首页星空仍可见且不遮挡文案；
- 导航栏滚动变形正常；
- 页面能滚动至底部，无跳动或横向滚动条；
- reduced-motion 下仍保留静态背景；
- 控制台没有新增错误。

### 任务 4：最终工程验证与交付

**文件：**
- 验证：`educloud-frontend/student-portal`

- [ ] **步骤 1：重新运行完整静态验证**

```powershell
cd educloud-frontend/student-portal
npm run typecheck
npm run build
```

预期：两条命令均退出码 `0`；生产构建输出成功。

- [ ] **步骤 2：检查本次文件边界和空白错误**

```powershell
git diff --check -- `
  educloud-frontend/student-portal/src/components/Galaxy/Galaxy.tsx `
  educloud-frontend/student-portal/src/pages/Home.tsx

git status --short -- `
  educloud-frontend/student-portal/src/components/Galaxy/Galaxy.tsx `
  educloud-frontend/student-portal/src/pages/Home.tsx
```

预期：没有空白错误；只报告两个目标文件既有/新增状态。

- [ ] **步骤 3：记录前后数据和诚实边界**

交付必须列出：

- 首页优化前后的平均 FPS、P95、最大帧间隔和 draw 次数；
- 课程页对照数据；
- DPR 及 Canvas 尺寸变化；
- 离屏、reduced-motion、恢复和卸载结果；
- 类型检查、构建、控制台结果；
- 当前源码未提交的原因：目标文件包含用户尚未提交的视觉改动，避免混入归属不明的变更。

- [ ] **步骤 4：停止本地服务并清理浏览器产物**

关闭 Playwright 页面，停止端口 `4176` 的本地 Vite 会话；仅删除本次生成且已确认位于工作区 `.playwright-mcp` 内的日志和快照，不删除用户文件。
