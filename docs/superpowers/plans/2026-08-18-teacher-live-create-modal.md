# 教师端创建直播弹窗实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans` 执行此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将教师端“创建直播”弹窗改为全视口暖色液态玻璃遮罩，锁定背景滚动，并保证桌面端完整展示表单、小屏端仅弹窗内部滚动。

**架构：** 使用 React `createPortal` 将弹窗挂载到 `document.body`，避开 `LiveManage` 页面动画 `transform` 对 `fixed` 定位的影响。弹窗打开期间通过 `useEffect` 临时锁定 `body` 滚动，遮罩内部使用固定视口和弹窗级滚动容器。

**技术栈：** React 18、TypeScript、Tailwind CSS、ReactDOM Portal、Playwright Core、Vite。

---

## 文件结构

- 修改：`educloud-frontend/teacher-portal/src/pages/LiveManage.tsx`——Portal 渲染、背景滚动锁定、液态玻璃弹窗布局。
- 临时测试：`_teacher-live-modal-regression.mjs`——Playwright 桌面/移动回归脚本，验证完成后删除，不提交到仓库。
- 不修改：直播状态、课程选择、创建提交和取消业务逻辑。

### 任务 1：建立修复前失败回归

**文件：**
- 创建：`_teacher-live-modal-regression.mjs`
- 读取：`educloud-frontend/teacher-portal/src/pages/LiveManage.tsx`

- [ ] **步骤 1：编写 Playwright 回归脚本**

脚本使用本地 Playwright Core 和 Chrome，依次验证桌面与移动视口。选择器只依赖现有“创建直播”按钮和弹窗的 `fixed inset-0` / `max-w-lg` 类，避免先修改生产代码来配合测试：

```js
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/LEIJIA~1/AppData/Local/Temp/educloud-live-playwright/node_modules/playwright-core');
const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
});

const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 700 },
];

for (const viewport of viewports) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => localStorage.setItem('teacher_token', 'playwright-teacher-token'));
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });

  await page.goto('http://127.0.0.1:5174/live', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: '创建直播' }).first().click();

  const snapshot = await page.evaluate(() => {
    const fixedLayers = [...document.querySelectorAll('[class~="fixed"][class~="inset-0"]')];
    const overlay = fixedLayers.at(-1);
    const modal = overlay?.querySelector('[class~="max-w-lg"]') ?? overlay?.firstElementChild;
    const rect = (element) => element?.getBoundingClientRect().toJSON() ?? null;
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      overlay: rect(overlay),
      modal: rect(modal),
      modalScrollHeight: modal?.scrollHeight ?? null,
      modalClientHeight: modal?.clientHeight ?? null,
      modalOverflowY: modal ? getComputedStyle(modal).overflowY : null,
      bodyOverflow: getComputedStyle(document.body).overflow,
      scrollWidth: document.documentElement.scrollWidth,
    };
  });

  console.log(JSON.stringify({ viewport: viewport.name, snapshot, errors }, null, 2));
  assert.deepEqual(errors, [], `${viewport.name} 视口产生控制台错误`);
  assert.equal(snapshot.bodyOverflow, 'hidden', `${viewport.name} 未锁定背景滚动`);
  assert.deepEqual(snapshot.overlay, {
    x: 0,
    y: 0,
    width: viewport.width,
    height: viewport.height,
    top: 0,
    right: viewport.width,
    bottom: viewport.height,
    left: 0,
  }, `${viewport.name} 遮罩未覆盖完整视口`);
  assert.ok(snapshot.modal, `${viewport.name} 未找到创建直播弹窗`);
  assert.ok(snapshot.scrollWidth <= viewport.width, `${viewport.name} 产生水平溢出`);

  if (viewport.name === 'desktop') {
    assert.ok(snapshot.modal.bottom <= viewport.height, '桌面端弹窗底部超出视口');
  } else {
    assert.ok(snapshot.modalScrollHeight > snapshot.modalClientHeight, '小屏弹窗未提供内部滚动空间');
    assert.ok(['auto', 'scroll'].includes(snapshot.modalOverflowY), '小屏滚动未限制在弹窗内部');
  }

  await context.close();
}

await browser.close();
```

- [ ] **步骤 2：启动教师端开发服务器并运行失败测试**

运行：

```powershell
pnpm dev --host 127.0.0.1 --port 5174
node _teacher-live-modal-regression.mjs
```

预期：测试失败，当前实现的 `fixed inset-0` 受 `animate-fade-up` 的 `transform` 祖先影响，遮罩边界不等于浏览器视口；背景 `body` 的 overflow 也不是 `hidden`。

### 任务 2：实现 Portal、液态遮罩与滚动锁定

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/pages/LiveManage.tsx`

- [ ] **步骤 1：增加 Portal 导入和背景滚动锁定副作用**

将导入改为：

```tsx
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
```

在现有状态和处理函数之后加入：

```tsx
  useEffect(() => {
    if (!showCreate) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showCreate]);
```

- [ ] **步骤 2：把创建弹窗从页面动画容器移到 `document.body`**

删除当前 `LiveManage` 页面根节点内的 `Create modal` JSX，并在根页面内容外通过 Portal 输出以下结构；字段绑定、`handleCreate`、关闭和清空逻辑保持原样：

```tsx
  const createModal = showCreate
    ? createPortal(
        <div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
          <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
            <div className="pointer-events-none absolute inset-0 overflow-hidden">
              <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
              <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
            </div>
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="create-live-title"
              className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-lg overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
            >
              <h2 id="create-live-title" className="font-display text-xl font-semibold text-ink-900">创建直播</h2>
              <div className="mt-4 space-y-4">
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">直播标题</label>
                  <input
                    type="text"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    className="input-field"
                    placeholder="例如：Spring Boot 微服务架构直播答疑"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">关联课程</label>
                  <select
                    value={newCourseId}
                    onChange={(e) => setNewCourseId(e.target.value)}
                    className="input-field cursor-pointer appearance-none"
                  >
                    <option value="">请选择课程</option>
                    {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
                  </select>
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">开播时间</label>
                  <input
                    type="datetime-local"
                    value={newStartTime}
                    onChange={(e) => setNewStartTime(e.target.value)}
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">直播简介</label>
                  <textarea
                    value={newDesc}
                    onChange={(e) => setNewDesc(e.target.value)}
                    rows={3}
                    className="input-field resize-none"
                    placeholder="简要介绍本次直播内容……"
                  />
                </div>
                <div className="flex gap-3 pt-1">
                  <button onClick={handleCreate} className="btn-primary flex-1">
                    <Radio className="h-4 w-4" />
                    创建直播
                  </button>
                  <button onClick={() => setShowCreate(false)} className="btn-outline flex-1">取消</button>
                </div>
              </div>
            </div>
          </div>
        </div>,
        document.body,
      )
    : null;
```

将 `createModal` 放在页面根 `<div>` 同级的 Fragment 中返回，确保 Portal 不再是 `animate-fade-up` 的后代。液态层由半透明遮罩、`backdrop-blur-xl`、暖琥珀/浅靛蓝模糊光晕和半透明表单容器组成；弹窗内部保留 `overflow-y-auto`，背景层保持 `overflow-hidden`。

- [ ] **步骤 3：运行 TypeScript 检查**

运行：

```powershell
pnpm typecheck
```

预期：退出码为 0，无类型错误。

### 任务 3：验证红绿回归和交互边界

**文件：**
- 使用：`_teacher-live-modal-regression.mjs`

- [ ] **步骤 1：运行桌面与移动 Playwright 回归**

运行：

```powershell
node _teacher-live-modal-regression.mjs
```

预期：桌面和移动两个视口均通过；遮罩为 `{ x: 0, y: 0, width: viewportWidth, height: viewportHeight }`，`bodyOverflow` 为 `hidden`，桌面弹窗底部不超出视口，移动弹窗的 `scrollHeight` 大于 `clientHeight` 且只在弹窗内部滚动。

- [ ] **步骤 2：验证关闭后恢复滚动**

在脚本中点击弹窗内“取消”按钮后重新读取 `getComputedStyle(document.body).overflow`，断言其恢复为 `visible` 或打开前保存的值；再运行脚本确认关闭不会残留滚动锁定。

### 任务 4：构建、审查和清理

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/pages/LiveManage.tsx`
- 删除：`_teacher-live-modal-regression.mjs`

- [ ] **步骤 1：运行教师端生产构建**

运行：

```powershell
pnpm build
```

预期：`tsc && vite build` 退出码为 0；如出现既有 chunk 大小提示，只记录为非阻断警告。

- [ ] **步骤 2：删除临时测试并检查差异**

运行：

```powershell
git diff --check
git status --short
```

预期：只剩 `LiveManage.tsx` 的生产改动，不保留临时脚本或截图。

- [ ] **步骤 3：按中文代码审查规范复核**

重点检查：Portal 是否只在弹窗显示时创建；滚动锁定是否始终有清理函数；字段和创建逻辑是否未被改写；遮罩是否拥有足够层级且不产生页面横向溢出。发现问题按 `[必须修复]`、`[建议修改]`、`[仅供参考]` 分级。

### 任务 5：提交、推送和最终验证

- [ ] **步骤 1：提交实现**

运行：

```powershell
git add educloud-frontend/teacher-portal/src/pages/LiveManage.tsx
git commit -m "fix(教师端): 优化创建直播弹窗布局"
```

- [ ] **步骤 2：推送远程 main**

运行：

```powershell
git push origin HEAD:main
```

- [ ] **步骤 3：核对远程提交和工作区**

运行：

```powershell
git status --short --branch
git log -1 --oneline
git ls-remote origin refs/heads/main
```

预期：工作区干净，本地最新提交与远程 `main` 的哈希一致。
