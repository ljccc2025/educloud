# 直播观看页主题与聊天区布局优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复学生端直播观看页固定深色背景和聊天输入区被裁切的问题，让外围页面跟随系统主题，同时保持播放器深色聚焦面。

**架构：** 使用 Tailwind 的系统 `dark:` 变体，不引入主题状态或切换按钮。LiveRoom 负责页面外壳和桌面视口高度，ChatBox 负责面板内部的主题色与 flex 收缩边界；消息列表滚动，输入区固定在面板底部。

**技术栈：** React 18、TypeScript、Tailwind CSS 3、Vite、Playwright（独立临时验证脚本）。

---

### 任务 1：建立当前行为基线和失败断言

**文件：**
- 读取：`educloud-frontend/student-portal/src/pages/LiveRoom.tsx`
- 读取：`educloud-frontend/student-portal/src/components/ChatBox.tsx`
- 临时验证：系统临时目录中的 Playwright 脚本，不写入应用源码或 package.json

- [ ] **步骤 1：启动学生端开发服务器**

运行：
```powershell
pnpm dev --host 127.0.0.1
```

工作目录：`educloud-frontend/student-portal`。记录实际 Vite URL。

- [ ] **步骤 2：用 Playwright 复现浅色系统和小视口布局**

验证脚本打开 `/live/1`，设置 `colorScheme: 'light'`、桌面视口 `1280x720`，读取 `body`、聊天根节点和输入框的 `getBoundingClientRect()` 以及 computed `backgroundColor`。

预期基线失败：页面根背景为固定深色；聊天输入框的底部超出视口或面板可视区域。

- [ ] **步骤 3：用 Playwright 复现深色系统基线**

相同脚本设置 `colorScheme: 'dark'`，记录页面根、课程信息卡和聊天根节点的背景色，作为修复后的回归对照。

### 任务 2：实现系统主题与视口内聊天布局

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/LiveRoom.tsx`
- 修改：`educloud-frontend/student-portal/src/components/ChatBox.tsx`
- 修改（仅必要时）：`educloud-frontend/student-portal/src/index.css`

- [ ] **步骤 1：修改 LiveRoom 页面外壳和聊天高度**

将固定的 `bg-ink-900` 替换为浅色默认背景和 `dark:bg-ink-900`；为面包屑、课程信息卡和标题元信息补齐 `dark:` 文字/边框/背景变体。桌面聊天面板使用 `height: min(42rem, calc(100dvh - 8.5rem))` 的局部样式或等价 Tailwind 高度约束，并保留合理 `min-height`，不改变移动端流式布局。

- [ ] **步骤 2：修改 ChatBox flex 收缩和主题色**

聊天根容器使用 `min-h-0`；消息列表使用 `min-h-0 flex-1 overflow-y-auto`；输入区使用 `shrink-0`。为面板、标题、普通/教师气泡、输入框和发送按钮补充浅色默认与 `dark:` 变体，保留现有消息内容和交互。

- [ ] **步骤 3：运行类型检查，确认最小实现可编译**

运行：
```powershell
pnpm typecheck
```

预期：退出码为 0，无 TypeScript 错误。

### 任务 3：Playwright 回归验证

**文件：**
- 临时验证：系统临时目录中的 Playwright 脚本和截图，不提交到项目

- [ ] **步骤 1：验证浅色系统**

在 `1280x720` 和 `1440x900` 视口打开 `/live/1`，断言：页面外壳不是播放器深色；播放器保持深色；聊天输入框 `bottom <= viewport.height`；消息列表 `scrollHeight >= clientHeight` 时仅列表滚动。

- [ ] **步骤 2：验证深色系统**

设置 `colorScheme: 'dark'`，断言页面外壳、课程信息卡和聊天区使用深色背景，播放器仍可见且无横向溢出。

- [ ] **步骤 3：验证移动视口和控制台**

在 `390x844` 打开 `/live/1`，断言 `document.documentElement.scrollWidth <= window.innerWidth`，聊天输入框可见；收集页面 `console` error 和 `pageerror`，预期为空。

### 任务 4：构建、差异检查和代码 review

**文件：**
- 检查：`educloud-frontend/student-portal/dist/`
- 检查：`docs/superpowers/specs/2026-08-17-live-room-theme-layout-design.md`

- [ ] **步骤 1：运行生产构建**

运行：
```powershell
pnpm build
```

预期：TypeScript 和 Vite 构建均成功。

- [ ] **步骤 2：运行差异空白检查**

运行：
```powershell
git diff --check
```

若当前目录不是 Git 工作树，则记录为环境限制，不修改或清理无关文件。

- [ ] **步骤 3：使用 chinese-code-review 完成最终中文 review**

逐项检查主题范围、视口边界、移动端行为、可访问性和是否引入未请求细节；发现必须修复项先修复，再重复类型检查、Playwright 和构建。
