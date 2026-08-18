# 学生端学习互动中心实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在学生端新增通知中心、AI 助教和学习社区三个可操作页面，并完成导航、Mock 状态和响应式验证。

**架构：** 三个功能共享 `features/engagement` 领域目录中的类型与适配器，但各自保持独立状态边界。通知和社区使用 Zustand 管理会话状态；AI 助教通过单一客户端适配器选择后端端点或本地 Mock，应答状态留在页面内。

**技术栈：** React 18、TypeScript、React Router 6、Zustand 5、Tailwind CSS、Playwright Core、Vite。

---

## 文件结构

- 创建：`educloud-frontend/student-portal/src/features/engagement/types.ts`——通知、AI 消息、社区帖子和回复类型。
- 创建：`educloud-frontend/student-portal/src/features/engagement/useNotificationStore.ts`——通知 Mock 数据与已读操作。
- 创建：`educloud-frontend/student-portal/src/features/engagement/assistantClient.ts`——后端端点与 Mock 应答适配器。
- 创建：`educloud-frontend/student-portal/src/features/engagement/useCommunityStore.ts`——帖子、点赞、收藏、发布和回复操作。
- 创建：`educloud-frontend/student-portal/src/pages/Notifications.tsx`——通知列表与筛选页面。
- 创建：`educloud-frontend/student-portal/src/pages/AiAssistant.tsx`——聊天和快捷提问页面。
- 创建：`educloud-frontend/student-portal/src/pages/Community.tsx`——社区帖子流和互动页面。
- 修改：`educloud-frontend/student-portal/src/App.tsx`——注册三个受保护路由。
- 修改：`educloud-frontend/student-portal/src/components/Navbar.tsx`——新增入口、通知数量和响应式收敛。
- 修改：`educloud-frontend/student-portal/src/components/Footer.tsx`——增加 AI 助教和学习社区链接。
- 临时创建并删除：`_student-engagement-regression.mjs`——Playwright 红绿回归脚本。

### 任务 1：建立失败回归和基线

**文件：**
- 临时创建：`_student-engagement-regression.mjs`

- [ ] **步骤 1：运行学生端基线类型检查**

运行：`pnpm typecheck`

预期：退出码为 0，证明实现前基线可用。

- [ ] **步骤 2：编写 Playwright 失败回归**

脚本注入 `student_token`，依次访问三个目标路由并断言：

```js
const routes = [
  ['/notifications', '通知中心'],
  ['/ai-assistant', 'AI 助教'],
  ['/community', '学习社区'],
];

for (const [path, heading] of routes) {
  await page.goto(`http://127.0.0.1:5173${path}`);
  await expectHeading(page, heading);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
}
```

在通知页验证全部已读；在 AI 页发送“如何复习高等数学”并等待助教回复；在社区页发布“高等数学复习方法交流”并执行点赞、收藏和回复。

- [ ] **步骤 3：运行脚本并确认红灯**

运行：`node _student-engagement-regression.mjs`

预期：非零退出，首个 `/notifications` 路由被旧通配路由重定向，页面找不到“通知中心”。

### 任务 2：实现通知中心

**文件：**
- 创建：`educloud-frontend/student-portal/src/features/engagement/types.ts`
- 创建：`educloud-frontend/student-portal/src/features/engagement/useNotificationStore.ts`
- 创建：`educloud-frontend/student-portal/src/pages/Notifications.tsx`
- 修改：`educloud-frontend/student-portal/src/App.tsx`
- 修改：`educloud-frontend/student-portal/src/components/Navbar.tsx`

- [ ] **步骤 1：定义通知类型和状态接口**

```ts
export type NotificationKind = 'COURSE' | 'ASSIGNMENT' | 'EXAM' | 'LIVE' | 'SYSTEM';

export interface StudentNotification {
  id: number;
  kind: NotificationKind;
  title: string;
  content: string;
  createdAt: string;
  read: boolean;
  actionLabel?: string;
  actionPath?: string;
}
```

- [ ] **步骤 2：实现通知 store**

```ts
interface NotificationState {
  notifications: StudentNotification[];
  markRead: (id: number) => void;
  markAllRead: () => void;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: initialNotifications,
  markRead: (id) => set((state) => ({
    notifications: state.notifications.map((item) => item.id === id ? { ...item, read: true } : item),
  })),
  markAllRead: () => set((state) => ({
    notifications: state.notifications.map((item) => item.read ? item : { ...item, read: true }),
  })),
}));
```

- [ ] **步骤 3：实现通知页面**

页面使用 `useMemo` 计算未读数量和筛选结果。业务按钮调用 `markRead(id)` 后 `navigate(actionPath)`；“全部已读”仅在存在未读数据时可用。通知卡片以左侧类型图标、标题、内容、相对时间和操作区组成。

- [ ] **步骤 4：接入路由和铃铛**

在 `App.tsx` 注册 `/notifications`；`Navbar` 的铃铛改为 `<Link>`，角标显示 `unreadCount > 9 ? '9+' : unreadCount`，没有未读时不渲染角标。移动菜单增加通知中心入口。

- [ ] **步骤 5：运行类型检查**

运行：`pnpm typecheck`

预期：退出码为 0。

### 任务 3：实现 AI 助教

**文件：**
- 修改：`educloud-frontend/student-portal/src/features/engagement/types.ts`
- 创建：`educloud-frontend/student-portal/src/features/engagement/assistantClient.ts`
- 创建：`educloud-frontend/student-portal/src/pages/AiAssistant.tsx`
- 修改：`educloud-frontend/student-portal/src/App.tsx`
- 修改：`educloud-frontend/student-portal/src/components/Navbar.tsx`

- [ ] **步骤 1：定义消息和客户端接口**

```ts
export interface AssistantMessage {
  id: string;
  role: 'student' | 'assistant';
  content: string;
  createdAt: string;
}

export interface AssistantReply {
  content: string;
  mode: 'mock' | 'remote';
}
```

- [ ] **步骤 2：实现客户端适配器**

`assistantClient.ask(question)` 对空内容抛出错误。配置 `VITE_AI_ASSISTANT_ENDPOINT` 时使用 `fetch` POST `{ question }` 并校验 `response.ok` 和返回的 `content`；未配置时按数学、编程、考试和通用关键词返回固定 Mock 回答，延迟 500ms 模拟请求。

- [ ] **步骤 3：实现聊天页面**

页面初始展示一条助教欢迎消息。发送时立即追加学生消息并进入加载状态，成功后追加助教消息，失败时显示错误条；`finally` 恢复发送按钮。快捷问题复用同一发送函数，清空按钮恢复欢迎消息。消息列表使用 `ref` 在消息或加载状态变化时滚动到底部。

- [ ] **步骤 4：接入导航与路由**

注册 `/ai-assistant`，桌面和移动导航增加“AI 助教”。页面以“演示模式”或“已连接服务”标签准确展示当前模式，不在浏览器中存储模型密钥。

- [ ] **步骤 5：运行类型检查**

运行：`pnpm typecheck`

预期：退出码为 0。

### 任务 4：实现学习社区

**文件：**
- 修改：`educloud-frontend/student-portal/src/features/engagement/types.ts`
- 创建：`educloud-frontend/student-portal/src/features/engagement/useCommunityStore.ts`
- 创建：`educloud-frontend/student-portal/src/pages/Community.tsx`
- 修改：`educloud-frontend/student-portal/src/App.tsx`
- 修改：`educloud-frontend/student-portal/src/components/Navbar.tsx`
- 修改：`educloud-frontend/student-portal/src/components/Footer.tsx`

- [ ] **步骤 1：定义帖子和回复类型**

```ts
export interface CommunityReply {
  id: number;
  author: string;
  avatar: string;
  content: string;
  createdAt: string;
}

export interface CommunityPost {
  id: number;
  title: string;
  content: string;
  author: string;
  avatar: string;
  courseName: string;
  tags: string[];
  createdAt: string;
  likes: number;
  liked: boolean;
  bookmarked: boolean;
  replies: CommunityReply[];
}
```

- [ ] **步骤 2：实现社区 store**

store 提供 `addPost`、`toggleLike`、`toggleBookmark` 和 `addReply`。新增帖子与回复使用当前最大 id 加一，空白标题、正文或回复直接返回 `false`；点赞切换同步加一或减一且不低于零。

- [ ] **步骤 3：实现社区页面**

页面状态包括 `filter`、`keyword`、`composerOpen`、`expandedPostId` 和表单字段。`useMemo` 按最新、热门或收藏处理固定 Mock 帖子，再按标题、正文、课程和标签搜索。发布成功后关闭编辑区并清空表单；回复成功后清空回复输入。所有按钮提供明确 `aria-label`。

- [ ] **步骤 4：接入导航、路由和页脚**

注册 `/community`；桌面和移动导航增加“学习社区”；页脚学习支持区增加 AI 助教和学习社区链接。桌面导航改为 `gap-5`，搜索框改为 `hidden xl:flex`，避免 1024px 导航挤压。

- [ ] **步骤 5：运行类型检查**

运行：`pnpm typecheck`

预期：退出码为 0。

### 任务 5：自动化验证、审查和提交

**文件：**
- 使用并删除：`_student-engagement-regression.mjs`
- 检查：本计划列出的所有学生端文件

- [ ] **步骤 1：运行 Playwright 绿灯回归**

运行：`node _student-engagement-regression.mjs`

预期：三个路由和核心交互全部通过；1440px 与 390px 均无横向溢出和控制台错误。

- [ ] **步骤 2：运行生产构建**

运行：`pnpm build`

预期：`tsc && vite build` 退出码为 0。

- [ ] **步骤 3：删除临时脚本并检查差异**

删除回归脚本，运行：

```powershell
git diff --check
git status --short
```

预期：学生端只包含设计范围内的文件；管理端已有未提交修改保持原样。

- [ ] **步骤 4：执行中文代码审查**

按 `[必须修复]`、`[建议修改]`、`[仅供参考]` 检查功能正确性、Mock/真实 AI 边界、空输入处理、状态不可变性、响应式布局和变更范围；提交前清零所有必须修复项。

- [ ] **步骤 5：提交实现**

```powershell
git add -- educloud-frontend/student-portal docs/superpowers/plans/2026-08-18-student-engagement-center.md
git commit -m "feat(学生端): 新增学习互动中心"
```

预期：提交不包含 `educloud-frontend/admin-portal` 的既有改动。
