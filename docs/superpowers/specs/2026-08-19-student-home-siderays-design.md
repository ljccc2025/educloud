# 学生端首页 SideRays 背景与导航融合设计

## 背景

学生端首页 Hero 当前挂载 `Galaxy` WebGL 粒子背景。粒子数量较多，视觉噪点明显；同时顶部导航位于 Hero 之前，导航外层区域使用独立页面底色，导致特效从导航下方才开始，形成明显的白色断层。

本次改动用用户提供的 React Bits `SideRays` 替换首页粒子背景，并让光束画布延伸到顶部导航背后。导航继续保持现有胶囊布局、菜单信息架构和滚动形态，仅调整顶部状态的背景融合方式。

## 目标

- 首页不再渲染 Galaxy 粒子画布。
- 使用黄蓝双色 SideRays 光束作为 Hero 装饰背景。
- 页面位于顶部时，SideRays 从右上方延伸到导航背后，不出现独立白色背景带。
- 导航在光束上保持液态玻璃质感和足够的文字对比度。
- 页面滚动后，导航恢复更高不透明度的固定背景，确保任何内容上方都清晰可读。
- 保持现有 Hero 文案、进度卡片、按钮、路由和响应式信息结构不变。
- 控制 WebGL 生命周期和渲染开销，不引入新的前端技术或依赖。

## 非目标

- 不删除现有 `Galaxy` 组件目录；该目录属于当前工作区已有的未提交内容，本次只解除首页引用。
- 不改造首页 Hero 以外的课程、分类、CTA 或页脚设计。
- 不改变导航菜单项、搜索、通知、购物车、头像和移动菜单行为。
- 不在其他页面启用 SideRays。
- 不增加新的主题系统、动画框架或状态管理方案。

## 已确认视觉方向

采用方案 A“统一画布 + 液态玻璃导航”。Hero 向上延伸到导航所在区域，SideRays 作为 Hero 内唯一的 WebGL 装饰层覆盖这块连续区域。导航保持更高的层叠级别，顶部状态使用半透明玻璃底色，因此光束可以从导航背后自然透出。

光束使用柔和参数，不完全照搬提示词中的高速度、高强度配置：

```tsx
<SideRays
  speed={1.1}
  rayColor1="#EAB308"
  rayColor2="#96c8ff"
  intensity={1.25}
  spread={1.8}
  origin="top-right"
  tilt={0}
  saturation={1.2}
  blend={0.72}
  falloff={1.7}
  opacity={0.62}
/>
```

这些数值保留黄色品牌强调色和蓝色学习氛围，同时降低快速运动与高亮区域对标题、导航文字的干扰。

## 组件设计

### SideRays

创建：

- `educloud-frontend/student-portal/src/components/SideRays/SideRays.tsx`
- `educloud-frontend/student-portal/src/components/SideRays/SideRays.css`

组件基于用户提供的 React Bits JavaScript + CSS 版本，按项目现有 TypeScript 体系进行类型化适配。公开属性保持提示词中的名称和语义：速度、双色、强度、扩散、起点、倾斜、饱和度、混合、衰减、透明度和额外类名。

组件职责：

- 创建和销毁 OGL `Renderer`、`Program`、`Triangle` 与 `Mesh`。
- 根据容器尺寸和设备像素比更新画布尺寸，DPR 上限为 `2`。
- 使用 `IntersectionObserver` 在背景离开视口时暂停动画循环。
- 使用 `ResizeObserver` 监听容器尺寸变化，避免仅依赖窗口 resize。
- 在属性变化时更新 uniform，不重复创建 WebGL 上下文。
- 在卸载、不可见或初始化竞态发生时取消动画、断开观察器、移除画布并释放 WebGL 上下文。
- 对 `prefers-reduced-motion: reduce` 渲染静态首帧，不持续申请动画帧。
- WebGL 初始化失败时保留 Hero 自身的 CSS 渐变背景，不影响内容与交互。

组件为纯装饰层：`aria-hidden="true"`、`pointer-events: none`，不进入键盘焦点顺序。

### Home

修改 `Home.tsx`：

- 删除 `Galaxy` 导入和 Hero 内的 Galaxy 实例。
- 在 Hero 的最底层挂载 `SideRays`。
- Hero 保留 CSS 渐变作为 WebGL 失败和低性能环境的视觉兜底。
- Hero 使用约 `68px` 的负顶部偏移和等量顶部补偿，使其背景区域覆盖导航顶部状态占用的空间，但不改变 Hero 内容的视觉起点。
- 光束层、可读性渐变层、正文层分别使用稳定层叠级别；画布和渐变均不得拦截点击。
- 保持标题、说明、按钮和学习进度卡的现有网格及响应式布局。

### Navbar

修改 `Navbar.tsx` 的视觉类，不改变状态或事件逻辑：

- 未滚动时保留胶囊形状，将实色纸张背景调整为较低不透明度的液态玻璃背景，并保持 `backdrop-blur`、浅色边框和柔和阴影。
- Header 外层不添加实色背景，让 Hero 的 SideRays 能覆盖导航周围区域。
- 滚动超过现有阈值后，继续使用全宽导航，并提高背景不透明度，防止下方课程卡片和文本影响菜单可读性。
- 移动端菜单继续使用独立不透明面板，不让光束穿透展开菜单内容。

## 层叠与几何约束

从低到高的层叠关系为：

1. Hero CSS 渐变兜底；
2. SideRays WebGL 画布；
3. 可读性渐变遮罩；
4. Hero 正文与学习进度卡；
5. Sticky Navbar；
6. 移动端展开菜单。

顶部状态下，Hero 背景顶边必须不低于 Header 顶边，且 Header 胶囊区域的后方能看到同一 SideRays 画布。Hero 正文的起点不得因背景上移而向上跳动。

## 性能与生命周期

- 继续使用项目已经存在的 `ogl` 依赖，不修改 `package.json` 或锁文件。
- SideRays 只在首页挂载，离开首页即销毁 WebGL 上下文。
- 画布离开视口时停止 `requestAnimationFrame`，重新进入时恢复。
- DPR 上限限制高分屏像素开销。
- 动画只更新 `iTime` 和绘制当前 Mesh；属性变化仅更新 uniform。
- 严格模式下重复挂载和异步初始化必须可清理，不允许遗留重复 canvas、resize 监听器或动画循环。

## 响应式与无障碍

- 桌面与移动端都使用同一光束画布，起点保持右上角。
- 画布始终裁剪在 Hero 连续背景区域内，不产生横向滚动条。
- 小屏幕保持当前单列 Hero 与卡片布局，不改变导航折叠菜单行为。
- 装饰画布隐藏于辅助技术，所有导航、按钮和输入框仍保持原有语义。
- 用户启用减少动态效果后，显示静态光束首帧，不运行持续动画。

## TDD 与验收设计

### 红灯

在生产代码改动前，用真实浏览器确认：

- 首页仍存在 `.galaxy-container`，不存在 `.side-rays-container`。
- Hero 顶边位于 Header 内容之后，光束或粒子背景没有覆盖导航区域。

红灯必须因当前实现仍使用 Galaxy 且导航背景断层而失败。

### 绿灯

实现后验证：

- 首页不存在 Galaxy canvas，存在且仅存在一个 SideRays canvas。
- SideRays 容器覆盖 Hero 全部背景区域。
- 页面顶部时 Hero 背景顶边覆盖 Header 区域，导航胶囊位于画布上方。
- 滚动后导航进入现有全宽状态并提高背景不透明度。
- 导航、搜索框和 Hero 按钮均可点击。
- `390 × 844` 与 `1440 × 900` 下无横向溢出，Hero 内容不被导航遮挡。
- 控制台无新增 error；路由切换后不残留重复 canvas。
- `prefers-reduced-motion` 下不持续执行动画循环。
- TypeScript 类型检查、生产构建和目标文件 `git diff --check` 全部通过。

## 工作区保护

当前 `Home.tsx`、`Navbar.tsx`、`MainLayout.tsx`、`index.css`、`package.json`、`pnpm-lock.yaml` 以及 `components/Galaxy/` 均存在用户未提交改动。本次实施必须：

- 在当前内容上应用最小补丁；
- 不执行 reset、checkout、clean、stash 或全仓格式化；
- 不删除 Galaxy 目录；
- 不修改 `package.json` 和 `pnpm-lock.yaml`，因为 `ogl` 已存在；
- 不把任务开始前的现有差异描述为本次产出；
- 若提交会混入用户改动，则源代码保持未提交，并在最终交付中明确边界。
