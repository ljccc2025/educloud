# 学习社区标签筛选修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将学习社区热门标签改为独立、可见、可清除的筛选状态，并在清除后恢复当前选项卡的完整讨论列表。

**架构：** `Community.tsx` 继续拥有选项卡、文本搜索和标签筛选三个页面状态，并通过单个 `useMemo` 派生可见帖子。新增无状态 `SelectedTagFilter` 组件只负责显示 `#标签 ×` 和触发清除，不读取数据仓库；热门标签采用帖子 `tags` 精确匹配，文本搜索维持原有模糊匹配。

**技术栈：** React 18、TypeScript 5、React Hooks、Zustand 5、Tailwind CSS 3、Lucide React、Vite 5、Playwright 浏览器行为验证。

---

## 文件结构

- 创建：`educloud-frontend/student-portal/src/features/engagement/components/SelectedTagFilter.tsx`
  - 展示当前已选标签及独立关闭按钮；不持有筛选状态，不读取 Zustand。
- 修改：`educloud-frontend/student-portal/src/pages/Community.tsx`
  - 新增 `selectedTag` 状态，将选项卡、精确标签和模糊搜索组合为单一派生列表；接入筛选标签组件和热门标签选中态。
- 不修改：`educloud-frontend/student-portal/src/features/engagement/useCommunityStore.ts`
  - 帖子源数据、收藏、点赞、发帖和回复行为不需要数据层变更。

## 工作区保护

`Community.tsx` 当前包含用户尚未提交的圆角、头像和标签视觉调整。执行时必须在现有文件上做最小补丁，禁止覆盖、回退或整文件重写这些改动。除非用户另行授权，不把混有用户视觉改动的源文件整体加入 Git 提交；规格和计划文档可独立提交。

### 任务 1：建立修复前浏览器红灯

**文件：**
- 读取：`educloud-frontend/student-portal/src/pages/Community.tsx:32-53`
- 读取：`educloud-frontend/student-portal/src/pages/Community.tsx:134-153`
- 读取：`educloud-frontend/student-portal/src/pages/Community.tsx:246-253`
- 测试：真实浏览器中的 `/community` 页面，不创建项目测试文件

- [ ] **步骤 1：启动学生端开发服务器**

在 `educloud-frontend/student-portal` 目录运行：

```powershell
npm run dev -- --host 127.0.0.1 --port 4176
```

预期：Vite 输出 `Local: http://127.0.0.1:4176/`，进程保持运行。

- [ ] **步骤 2：记录完整讨论列表基线**

使用已有登录态打开 `http://127.0.0.1:4176/community`，等待“学习社区”标题出现，然后在浏览器中执行：

```javascript
const titles = [...document.querySelectorAll('main article h2')]
  .map((node) => node.textContent?.trim());
({ count: titles.length, titles });
```

预期：`count` 为 `4`，列表包含 React、高等数学、Python 和微服务四个初始话题。

- [ ] **步骤 3：运行标签筛选红灯断言**

点击右侧 `#React` 后执行以下行为断言：

```javascript
const search = document.querySelector('input[placeholder="搜索讨论..."]');
const clearButton = document.querySelector('button[aria-label="清除标签：React"]');
const titles = [...document.querySelectorAll('main article h2')]
  .map((node) => node.textContent?.trim());

({
  searchValue: search instanceof HTMLInputElement ? search.value : null,
  hasClearButton: Boolean(clearButton),
  titles,
});
```

预期红灯：`searchValue` 为 `React`、`hasClearButton` 为 `false`。这证明热门标签错误复用了文本搜索状态，并且不存在独立清除入口。

- [ ] **步骤 4：确认选项卡不是根因**

依次点击“热门讨论”和“我的收藏”，再次读取搜索框值。

预期：搜索框仍为 `React`。记录结论：选项卡只更新 `filter`，持久过滤来自没有清除入口的 `keyword`，因此应拆分状态而不是在选项卡点击事件中强制清空搜索。

### 任务 2：实现独立已选标签组件

**文件：**
- 创建：`educloud-frontend/student-portal/src/features/engagement/components/SelectedTagFilter.tsx`
- 测试：任务 3 的真实浏览器可访问性与点击断言

- [ ] **步骤 1：创建无状态筛选标签组件**

新增以下完整实现：

```tsx
import { X } from 'lucide-react';

interface SelectedTagFilterProps {
  tag: string;
  onClear: () => void;
}

export default function SelectedTagFilter({ tag, onClear }: SelectedTagFilterProps) {
  return (
    <div className="ml-2 flex shrink-0 items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-800">
      <span>#{tag}</span>
      <button
        type="button"
        onClick={onClear}
        className="rounded-full p-0.5 text-indigo-500 transition-colors hover:bg-indigo-100 hover:text-indigo-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-700 focus-visible:ring-offset-1"
        aria-label={`清除标签：${tag}`}
      >
        <X size={13} aria-hidden="true" />
      </button>
    </div>
  );
}
```

组件边界要求：`tag` 必须是非空字符串；父组件通过条件渲染保证未选标签时组件完全隐藏。关闭按钮只调用 `onClear`，不操作选项卡或文本搜索。

- [ ] **步骤 2：运行类型检查验证组件自身可编译**

在 `educloud-frontend/student-portal` 目录运行：

```powershell
npm run typecheck
```

预期：退出码 `0`，没有 TypeScript 错误。

### 任务 3：接入独立标签状态与组合筛选

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Community.tsx:1-73`
- 修改：`educloud-frontend/student-portal/src/pages/Community.tsx:134-153`
- 修改：`educloud-frontend/student-portal/src/pages/Community.tsx:246-253`
- 测试：真实浏览器中的标签、选项卡、搜索与清除组合行为

- [ ] **步骤 1：导入组件并声明独立状态**

在社区数据仓库导入之后增加：

```tsx
import SelectedTagFilter from '../features/engagement/components/SelectedTagFilter';
```

在 `keyword` 状态之后增加：

```tsx
const [selectedTag, setSelectedTag] = useState<string | null>(null);
```

- [ ] **步骤 2：按选项卡、标签、关键词顺序派生列表**

将 `visiblePosts` 替换为：

```tsx
const visiblePosts = useMemo(() => {
  const normalizedKeyword = keyword.trim().toLowerCase();
  let result = filter === 'BOOKMARKED' ? posts.filter((post) => post.bookmarked) : [...posts];
  result.sort((first, second) => filter === 'HOT'
    ? second.likes + second.replies.length - first.likes - first.replies.length
    : second.id - first.id);

  if (selectedTag) {
    result = result.filter((post) => post.tags.includes(selectedTag));
  }

  if (!normalizedKeyword) return result;
  return result.filter((post) => [post.title, post.content, post.courseName, ...post.tags]
    .some((value) => value.toLowerCase().includes(normalizedKeyword)));
}, [filter, keyword, posts, selectedTag]);
```

该实现不得把 `visiblePosts` 写回 Zustand，也不得修改原始 `posts` 数组；`[...posts]` 保持非收藏分支排序时的不可变性。

- [ ] **步骤 3：发布成功后清除标签筛选**

在 `publishPost` 成功分支现有 `setKeyword('')` 后增加：

```tsx
setSelectedTag(null);
```

失败分支不得清空筛选状态。

- [ ] **步骤 4：在“我的收藏”右侧渲染已选标签**

保留现有三个选项卡按钮，并在 `filters.map(...)` 结束之后、同一横向容器关闭之前增加：

```tsx
{selectedTag ? (
  <SelectedTagFilter tag={selectedTag} onClear={() => setSelectedTag(null)} />
) : null}
```

父容器继续使用横向滚动，保证窄屏下选项卡文字和胶囊都不被压缩。

- [ ] **步骤 5：让热门标签只更新 `selectedTag`**

将热门标签按钮改为：

```tsx
<button
  key={tag}
  type="button"
  onClick={() => setSelectedTag(tag)}
  aria-pressed={selectedTag === tag}
  className={cn(
    'rounded-full border px-3 py-1.5 text-xs transition-colors',
    selectedTag === tag
      ? 'border-indigo-300 bg-indigo-50 text-indigo-800'
      : 'border-ink-100 text-ink-500 hover:border-indigo-200 hover:bg-indigo-50/60 hover:text-indigo-800',
  )}
>
  #{tag}
</button>
```

不得保留 `setKeyword(tag)`。同一标签再次点击仍保持选择，清除行为统一由选项卡右侧的关闭按钮承担。

- [ ] **步骤 6：运行类型检查**

```powershell
npm run typecheck
```

预期：退出码 `0`，没有 TypeScript 错误。

### 任务 4：执行浏览器绿灯与工程回归

**文件：**
- 验证：`educloud-frontend/student-portal/src/pages/Community.tsx`
- 验证：`educloud-frontend/student-portal/src/features/engagement/components/SelectedTagFilter.tsx`
- 验证：`educloud-frontend/student-portal/package.json`

- [ ] **步骤 1：验证标签、搜索与清除互不覆盖**

刷新 `/community` 后按顺序执行：

1. 在搜索框输入 `流程`，确认列表只显示 React 话题；
2. 点击 `#React`，确认搜索框仍为 `流程`；
3. 确认“我的收藏”右侧出现 `#React` 和“清除标签：React”按钮；
4. 点击关闭按钮，确认胶囊消失、搜索框仍为 `流程`，列表仍按文本搜索过滤；
5. 清空搜索框，确认恢复四条完整讨论。

浏览器断言：

```javascript
const search = document.querySelector('input[placeholder="搜索讨论..."]');
const selectedTag = document.querySelector('button[aria-label="清除标签：React"]');
const titles = [...document.querySelectorAll('main article h2')]
  .map((node) => node.textContent?.trim());

({
  searchValue: search instanceof HTMLInputElement ? search.value : null,
  hasSelectedTag: Boolean(selectedTag),
  titles,
});
```

每个步骤的结果必须与设计一致，不允许通过清空全部状态绕过组合筛选。

- [ ] **步骤 2：验证三个选项卡与标签组合**

选择 `#高等数学` 后依次点击：

- “最新讨论”：显示“分享一份高等数学错题整理模板”；
- “热门讨论”：仍显示该话题，胶囊保持可见；
- “我的收藏”：仍显示该话题，因为它已收藏且带有高等数学标签；
- 点击“清除标签：高等数学”：保持“我的收藏”激活，并恢复两个已收藏话题。

- [ ] **步骤 3：验证空状态与热门标签选中态**

点击 `#考试复习`：

- `aria-pressed` 为 `true`；
- 显示 `#考试复习 ×`；
- 列表显示现有“没有找到相关讨论”空状态；
- 点击关闭按钮后空状态消失，当前选项卡列表恢复。

- [ ] **步骤 4：验证窄屏布局**

将视口设置为 `390 × 844`：

- 三个选项卡和已选标签可在内部横向滚动；
- 页面根节点不产生新的横向溢出；
- 搜索框换行并占据可用宽度；
- 关闭按钮保持可点击且无障碍名称正确。

- [ ] **步骤 5：检查浏览器控制台**

预期：本次交互过程中 `0` 个新增 JavaScript 错误。React Router 现有未来版本提示不属于本次变更，不在此任务中扩展范围处理。

- [ ] **步骤 6：运行最终工程验证**

在 `educloud-frontend/student-portal` 目录运行：

```powershell
npm run typecheck
npm run build
```

在仓库根目录运行：

```powershell
git diff --check -- `
  'educloud-frontend/student-portal/src/pages/Community.tsx' `
  'educloud-frontend/student-portal/src/features/engagement/components/SelectedTagFilter.tsx'
```

预期：两个 npm 命令退出码均为 `0`，Vite 生产构建成功，差异空白检查没有输出错误。

- [ ] **步骤 7：复核工作区边界**

```powershell
git status --short -- `
  'educloud-frontend/student-portal/src/pages/Community.tsx' `
  'educloud-frontend/student-portal/src/features/engagement/components/SelectedTagFilter.tsx'
```

预期：只出现社区页面和新增筛选组件；不得暂存、提交、覆盖或删除项目中其他用户改动。源代码保持未提交，除非用户明确授权处理混合工作区提交。
