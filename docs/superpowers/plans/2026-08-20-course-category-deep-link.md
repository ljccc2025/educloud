# 首页课程分类深链与编号布局实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 在当前会话逐任务实现。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 首页分类卡片进入课程中心后自动选中同名分类，并让装饰编号 `02` 不再覆盖说明文字。

**架构：** 复用现有 `?category=` 链接协议，将课程中心的分类筛选改为由 URL 查询参数驱动；分类数据仍来自 `categories`。编号改用正常文档流布局，避免依赖固定内边距或绝对定位碰撞。

**技术栈：** React 18、TypeScript、React Router 6、Tailwind CSS、Vite、Playwright。

**工作区边界：** 只修改 `Home.tsx` 和 `CourseList.tsx`；文档可独立提交，生产代码保持未提交，不清理其他已有差异。

---

### 任务 1：建立深链和布局碰撞红灯

**文件：**
- 诊断：`educloud-frontend/student-portal/src/pages/Home.tsx`
- 诊断：`educloud-frontend/student-portal/src/pages/CourseList.tsx`

- [ ] **步骤 1：启动开发服务**

运行 `npm run dev -- --host 127.0.0.1 --port 4179`，预期首页和 `/courses` 可访问。

- [ ] **步骤 2：运行首页深链红灯**

用 Playwright 读取 `[data-home-category-card]` 的文字和 `href`，断言“计算机”为 `/courses?category=%E8%AE%A1%E7%AE%97%E6%9C%BA`。预期 FAIL：当前值是 `/courses`。

- [ ] **步骤 3：运行课程中心参数红灯**

访问 `/courses?category=数学`，断言“数学”筛选按钮为选中态且结果课程分类均为“数学”。预期 FAIL：当前页面仍选中“全部分类”。

- [ ] **步骤 4：运行编号碰撞红灯**

在 1440px 视口读取 `data-home-category-number` 与 `data-home-category-description` 的 DOMRect，断言两者不相交。预期 FAIL：当前绝对定位编号与说明区域相交，或语义节点尚不存在。

### 任务 2：实现 URL 驱动的分类筛选

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx`
- 修改：`educloud-frontend/student-portal/src/pages/CourseList.tsx`

- [ ] **步骤 1：生成首页分类深链**

将卡片目标改为：

```tsx
to={`/courses?category=${encodeURIComponent(cat.name)}`}
```

- [ ] **步骤 2：读取并验证查询参数**

在 `CourseList` 使用 `useSearchParams`，从 `category` 参数派生 `selectedCategory`；仅接受 `categories.some((cat) => cat.name === categoryParam)` 的值，否则使用 `all`。

- [ ] **步骤 3：同步侧栏选择与清除行为**

新增局部 `selectCategory` 函数，复制当前 `URLSearchParams`，选择分类时设置 `category`，选择全部时删除；分类按钮和 `clearFilters` 使用该函数，不保留第二份分类 state。

- [ ] **步骤 4：运行深链绿灯**

重新运行任务 1 的深链与筛选断言，并额外验证“数学”“计算机”、无效分类、侧栏切换和清除筛选。预期全部 PASS。

### 任务 3：修复编号布局

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx`

- [ ] **步骤 1：将编号移入说明列正常文档流**

在右侧说明列使用 `flex flex-col`；编号添加 `data-home-category-number` 并右对齐，说明添加 `data-home-category-description`，不再使用 `absolute right-4 top-0`。

- [ ] **步骤 2：运行桌面与移动端绿灯**

在 1440×900 和 390×844 下断言编号与说明 DOMRect 不相交、页面横向溢出为 0；截图检查标题节奏和卡片网格保持不变。

### 任务 4：工程验收

**文件：**
- 验证：`educloud-frontend/student-portal/src/pages/Home.tsx`
- 验证：`educloud-frontend/student-portal/src/pages/CourseList.tsx`

- [ ] **步骤 1：运行 `npm run typecheck`**

预期退出码 0。

- [ ] **步骤 2：运行 `npm run build`**

预期 TypeScript 与 Vite 构建成功。

- [ ] **步骤 3：运行限定差异检查**

运行 `git diff --check -- educloud-frontend/student-portal/src/pages/Home.tsx educloud-frontend/student-portal/src/pages/CourseList.tsx`，预期退出码 0。

- [ ] **步骤 4：独立审查并停止开发服务**

审查只覆盖本计划的分类深链、URL 同步与编号布局，忽略两个文件中的既有差异；修复 Critical/Important 后重新验证，最后停止 4179 服务。
