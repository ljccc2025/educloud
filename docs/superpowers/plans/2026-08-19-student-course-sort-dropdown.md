# 学生端课程排序弹层实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 用教师端同款视觉语言的自定义选择器替换学生端课程中心原生排序下拉框，同时完整保留五种排序行为和响应式布局。

**架构：** 新增受控展示组件 `CourseSortSelect`，由它独立处理弹层开关、键盘高亮、点击外部关闭和无障碍语义；`CourseList` 继续拥有排序状态与课程排序算法，只通过 `value/options/onChange` 与选择器通信。实现不读取 Store、不修改 MOCK 数据、不引入新依赖。

**技术栈：** React 18、TypeScript 5、Tailwind CSS 3、Lucide React、Vite 5、真实浏览器 Playwright 验收。

---

## 文件结构

- 创建：`educloud-frontend/student-portal/src/components/CourseSortSelect.tsx`
  - 负责紧凑排序触发器、浮层、选项状态、键盘导航、点击外部关闭和 ARIA 语义。
- 修改：`educloud-frontend/student-portal/src/pages/CourseList.tsx:1-25,218-236`
  - 声明强类型排序选项，使用 `CourseSortSelect` 替换原生 `<select>`；保留现有 `sort` 状态和 `filteredCourses` 算法。
- 参考但不修改：`educloud-frontend/teacher-portal/src/components/CourseSelect.tsx`
  - 作为圆角、阴影、淡紫状态、勾选和键盘交互的视觉依据。
- 参考：`docs/superpowers/specs/2026-08-19-student-course-sort-dropdown-design.md`
  - 已确认的范围、行为和成功标准。

## 工作区保护

当前仓库存在大量用户未提交改动。所有编辑必须使用精确补丁，只允许接触上述两个学生端源文件。禁止运行全仓格式化、`git reset`、`git checkout --`、`git clean` 或包含其他路径的批量提交。每次提交必须显式列出目标文件。

### 任务 1：建立修复前浏览器红灯

**文件：**
- 修改：无
- 测试：真实浏览器 `/courses` 行为断言

- [ ] **步骤 1：记录目标文件初始状态**

运行：

```powershell
git status --short -- `
  'educloud-frontend/student-portal/src/pages/CourseList.tsx' `
  'educloud-frontend/student-portal/src/components/CourseSortSelect.tsx'
```

预期：`CourseList.tsx` 没有本任务产生的改动，`CourseSortSelect.tsx` 尚不存在。若 `CourseList.tsx` 已被用户修改，先阅读差异并在现有内容上做最小补丁，不得覆盖。

- [ ] **步骤 2：启动学生端开发服务器**

从仓库根运行：

```powershell
npm --prefix educloud-frontend/student-portal run dev -- --host 127.0.0.1 --port 4176
```

预期：Vite 输出 `http://127.0.0.1:4176/`。保持该进程运行到浏览器验收结束。

- [ ] **步骤 3：运行自定义弹层红灯断言**

使用 Playwright 打开 `http://127.0.0.1:4176/courses`，执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4176/courses');
  await page.waitForLoadState('networkidle');

  const triggerCount = await page
    .getByRole('button', { name: '最受欢迎', exact: true })
    .count();
  const listboxCount = await page.getByRole('listbox').count();

  if (triggerCount !== 1 || listboxCount !== 0) {
    throw new Error(
      `custom sort dropdown missing: trigger=${triggerCount}, listbox=${listboxCount}`,
    );
  }
}
```

预期：FAIL，错误包含 `custom sort dropdown missing: trigger=0, listbox=0`。这证明回归断言能捕获当前原生 `<select>`，而不是在实现前误通过。

### 任务 2：实现独立的紧凑排序选择器

**文件：**
- 创建：`educloud-frontend/student-portal/src/components/CourseSortSelect.tsx`
- 测试：学生端 TypeScript 类型检查

- [ ] **步骤 1：创建最小受控组件**

创建文件并写入：

```tsx
import { useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/utils/cn';

export interface CourseSortOption<T extends string> {
  value: T;
  label: string;
}

interface CourseSortSelectProps<T extends string> {
  value: T;
  options: readonly CourseSortOption<T>[];
  onChange: (value: T) => void;
  placeholder?: string;
}

export default function CourseSortSelect<T extends string>({
  value,
  options,
  onChange,
  placeholder = '请选择排序',
}: CourseSortSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();

  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined;
  const label = selected?.label ?? options[0]?.label ?? placeholder;

  useEffect(() => {
    if (!open) return;

    const handleOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [open]);

  useEffect(() => {
    if (open) {
      setHighlightIndex(selectedIndex >= 0 ? selectedIndex : 0);
    }
  }, [open, selectedIndex]);

  useEffect(() => {
    if (!open || !listRef.current) return;
    const option = listRef.current.children[highlightIndex] as HTMLElement | undefined;
    option?.scrollIntoView({ block: 'nearest' });
  }, [highlightIndex, open]);

  const choose = (option: CourseSortOption<T>) => {
    onChange(option.value);
    setOpen(false);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (!open) {
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
        event.preventDefault();
        setOpen(true);
      }
      return;
    }

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (options.length > 0) {
          setHighlightIndex((index) => Math.min(index + 1, options.length - 1));
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (options.length > 0) {
          setHighlightIndex((index) => Math.max(index - 1, 0));
        }
        break;
      case 'Enter':
        event.preventDefault();
        if (options[highlightIndex]) choose(options[highlightIndex]);
        break;
      case 'Escape':
        event.preventDefault();
        setOpen(false);
        break;
    }
  };

  return (
    <div
      ref={containerRef}
      className="relative w-full shrink-0 sm:w-52"
      onKeyDown={handleKeyDown}
    >
      <button
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        onClick={() => setOpen((current) => !current)}
        className={cn(
          'flex min-h-[50px] w-full items-center justify-between gap-3 bg-white px-4 py-3 text-left',
          'rounded-xl border text-sm font-medium text-ink-700 transition-all duration-200',
          'focus:outline-none focus:ring-1 focus:ring-indigo-800',
          open
            ? 'border-indigo-800 ring-1 ring-indigo-800 shadow-sm'
            : 'border-ink-200 hover:border-ink-300',
        )}
      >
        <span className="truncate">{label}</span>
        <ChevronDown
          className={cn(
            'h-4 w-4 shrink-0 text-ink-400 transition-transform duration-200',
            open && 'rotate-180',
          )}
        />
      </button>

      {open && (
        <div
          className={cn(
            'absolute right-0 z-40 mt-2 w-full min-w-[13rem] overflow-hidden',
            'rounded-2xl border border-ink-100 bg-white p-2',
            'shadow-2xl shadow-ink-900/10 animate-fade-in',
          )}
        >
          <div ref={listRef} id={listboxId} role="listbox">
            {options.map((option, index) => {
              const isSelected = option.value === value;
              const isHighlighted = index === highlightIndex;

              return (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => choose(option)}
                  onMouseEnter={() => setHighlightIndex(index)}
                  className={cn(
                    'flex min-h-10 w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5',
                    'text-left text-sm transition-colors',
                    isHighlighted
                      ? 'bg-indigo-50/60 text-indigo-800'
                      : 'text-ink-600 hover:bg-ink-50',
                    isSelected && 'bg-indigo-50 font-semibold text-indigo-800',
                  )}
                >
                  <span>{option.label}</span>
                  {isSelected ? (
                    <Check className="h-4 w-4 shrink-0 text-indigo-800" aria-hidden="true" />
                  ) : (
                    <span className="h-4 w-4 shrink-0" aria-hidden="true" />
                  )}
                </button>
              );
            })}

            {options.length === 0 && (
              <p className="px-3 py-5 text-center text-sm text-ink-400">
                暂无排序选项
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **步骤 2：运行类型检查**

运行：

```powershell
npm --prefix educloud-frontend/student-portal run typecheck
```

预期：退出码 `0`，没有 TypeScript 错误。

- [ ] **步骤 3：提交独立组件**

运行：

```powershell
git add -- 'educloud-frontend/student-portal/src/components/CourseSortSelect.tsx'
git commit -m "feat(学生端): 添加课程排序选择器" -- `
  'educloud-frontend/student-portal/src/components/CourseSortSelect.tsx'
```

预期：提交只包含新组件，不包含工作区其他改动。

### 任务 3：接入课程中心并完成浏览器绿灯

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/CourseList.tsx:1-25,218-236`
- 测试：真实浏览器自定义弹层与排序行为断言

- [ ] **步骤 1：导入组件和选项类型**

在 `CourseList.tsx` 的 `CourseCard` 导入后添加：

```tsx
import CourseSortSelect, { type CourseSortOption } from '@/components/CourseSortSelect';
```

将排序选项声明改为：

```tsx
const sortOptions: readonly CourseSortOption<SortOption>[] = [
  { value: 'popular', label: '最受欢迎' },
  { value: 'newest', label: '最新发布' },
  { value: 'price-asc', label: '价格从低到高' },
  { value: 'price-desc', label: '价格从高到低' },
  { value: 'rating', label: '评分最高' },
];
```

- [ ] **步骤 2：替换原生 select**

删除：

```tsx
<select
  value={sort}
  onChange={(e) => setSort(e.target.value as SortOption)}
  className="px-4 py-3 bg-white border border-ink-200 text-ink-700 text-sm focus:outline-none focus:border-indigo-800 cursor-pointer"
>
  {sortOptions.map((opt) => (
    <option key={opt.value} value={opt.value}>{opt.label}</option>
  ))}
</select>
```

替换为：

```tsx
<CourseSortSelect
  value={sort}
  options={sortOptions}
  onChange={setSort}
/>
```

不要改动 `filteredCourses`、`clearFilters` 或其他筛选组件。

- [ ] **步骤 3：重新运行红灯脚本并验证转绿**

使用 Playwright 重新加载 `/courses`，执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4176/courses');
  await page.waitForLoadState('networkidle');

  const trigger = page.getByRole('button', { name: '最受欢迎', exact: true });
  if ((await trigger.count()) !== 1) throw new Error('custom trigger missing');

  await trigger.click();
  const listbox = page.getByRole('listbox');
  const options = listbox.getByRole('option');

  if ((await listbox.count()) !== 1) throw new Error('listbox missing');
  if ((await options.count()) !== 5) throw new Error('expected five sort options');

  const selected = listbox.getByRole('option', { name: '最受欢迎', exact: true });
  if ((await selected.getAttribute('aria-selected')) !== 'true') {
    throw new Error('selected state is not synchronized');
  }
  if ((await selected.locator('svg').count()) !== 1) {
    throw new Error('selected check icon missing');
  }
}
```

预期：PASS；存在一个自定义触发器、一个 `listbox`、五个选项，且“最受欢迎”具有选中语义和勾选图标。

- [ ] **步骤 4：验证鼠标排序、外部关闭与清除筛选**

执行：

```ts
async (page) => {
  const trigger = page.getByRole('button', { name: '最受欢迎', exact: true });
  await trigger.click();
  await page.getByRole('option', { name: '价格从低到高', exact: true }).click();

  const updatedTrigger = page.getByRole('button', { name: '价格从低到高', exact: true });
  if ((await updatedTrigger.count()) !== 1) throw new Error('trigger label did not update');
  if ((await page.getByRole('listbox').count()) !== 0) throw new Error('listbox did not close');

  const priceTexts = await page.locator('a[href^="/courses/"] .font-display.text-2xl').allTextContents();
  const prices = priceTexts.map((text) => text.trim() === '免费' ? 0 : Number(text.replace(/[^0-9.]/g, '')));
  const sorted = prices.every((price, index) => index === 0 || prices[index - 1] <= price);
  if (!sorted) throw new Error(`course prices are not ascending: ${prices.join(',')}`);

  await updatedTrigger.click();
  await page.getByRole('heading', { name: '全部课程' }).click();
  if ((await page.getByRole('listbox').count()) !== 0) throw new Error('outside click did not close');

  await page.getByRole('button', { name: '清除全部筛选' }).click();
  if ((await page.getByRole('button', { name: '最受欢迎', exact: true }).count()) !== 1) {
    throw new Error('clear filters did not restore popular sort');
  }
}
```

预期：PASS；升序结果单调、点击外部关闭，清除筛选恢复“最受欢迎”。

- [ ] **步骤 5：验证键盘交互**

执行：

```ts
async (page) => {
  const trigger = page.getByRole('button', { name: '最受欢迎', exact: true });
  await trigger.focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');

  if ((await page.getByRole('button', { name: '最新发布', exact: true }).count()) !== 1) {
    throw new Error('keyboard selection failed');
  }

  const newestTrigger = page.getByRole('button', { name: '最新发布', exact: true });
  await newestTrigger.press('Enter');
  await newestTrigger.press('Escape');
  if ((await page.getByRole('listbox').count()) !== 0) {
    throw new Error('Escape did not close the listbox');
  }
  if ((await page.getByRole('button', { name: '最新发布', exact: true }).count()) !== 1) {
    throw new Error('Escape changed the current value');
  }
}
```

预期：PASS；方向键与 Enter 完成选择，Escape 只关闭弹层、不改变当前排序值。

- [ ] **步骤 6：提交课程中心接入**

运行：

```powershell
git add -- 'educloud-frontend/student-portal/src/pages/CourseList.tsx'
git commit -m "feat(学生端): 统一课程排序弹层样式" -- `
  'educloud-frontend/student-portal/src/pages/CourseList.tsx'
```

预期：提交只包含 `CourseList.tsx` 的导入、类型声明和选择器替换。

### 任务 4：响应式、视觉和工程验收

**文件：**
- 修改：无；如果验收发现问题，只允许对任务 2、3 的两个目标文件做最小修正
- 测试：桌面和 390px 浏览器、类型检查、生产构建、差异检查

- [ ] **步骤 1：验证桌面弹层视觉几何**

将浏览器调整为 `1440 × 900`，打开弹层并断言：

```ts
async (page) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://127.0.0.1:4176/courses');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: '最受欢迎', exact: true }).click();

  const triggerBox = await page.getByRole('button', { name: '最受欢迎', exact: true }).boundingBox();
  const listboxBox = await page.getByRole('listbox').locator('..').boundingBox();
  const selected = page.getByRole('option', { name: '最受欢迎', exact: true });

  if (!triggerBox || !listboxBox) throw new Error('dropdown geometry missing');
  if (listboxBox.width < triggerBox.width) throw new Error('panel is narrower than trigger');
  if (listboxBox.y <= triggerBox.y + triggerBox.height) throw new Error('panel overlaps trigger');
  if ((await selected.evaluate((element) => getComputedStyle(element).backgroundColor)) === 'rgba(0, 0, 0, 0)') {
    throw new Error('selected option has no visual background');
  }
}
```

预期：PASS；弹层位于触发器下方、不窄于触发器，选中项具有可见背景。

- [ ] **步骤 2：验证 390px 响应式布局**

执行：

```ts
async (page) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await page.waitForLoadState('networkidle');

  const trigger = page.getByRole('button', { name: '最受欢迎', exact: true });
  await trigger.click();
  const triggerBox = await trigger.boundingBox();
  const panelBox = await page.getByRole('listbox').locator('..').boundingBox();
  const rootOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );

  if (!triggerBox || !panelBox) throw new Error('mobile dropdown geometry missing');
  if (triggerBox.width > 358) throw new Error(`mobile trigger too wide: ${triggerBox.width}`);
  if (panelBox.x < 0 || panelBox.x + panelBox.width > 390) {
    throw new Error(`panel outside viewport: ${JSON.stringify(panelBox)}`);
  }
  if (rootOverflow) throw new Error('page has horizontal overflow');
}
```

预期：PASS；触发器占用工具栏整行，弹层完整位于视口内，页面没有横向滚动。

- [ ] **步骤 3：检查浏览器控制台**

查询当前浏览器会话的 `error` 级别控制台消息。

预期：`Errors: 0`。现有非错误级 React Router 提示不属于本任务，但不得新增 warning 或 error。

- [ ] **步骤 4：运行完整工程验证**

从仓库根依次运行：

```powershell
npm --prefix educloud-frontend/student-portal run typecheck
npm --prefix educloud-frontend/student-portal run build
git diff --check -- `
  'educloud-frontend/student-portal/src/components/CourseSortSelect.tsx' `
  'educloud-frontend/student-portal/src/pages/CourseList.tsx'
git status --short -- `
  'educloud-frontend/student-portal/src/components/CourseSortSelect.tsx' `
  'educloud-frontend/student-portal/src/pages/CourseList.tsx'
```

预期：

- `typecheck` 退出码 `0`；
- `build` 退出码 `0`，Vite 成功生成 `dist`；
- `git diff --check` 退出码 `0`；
- 两个目标文件都已通过精确提交保持干净；
- 工作区其他用户改动没有被暂存、覆盖或提交。

- [ ] **步骤 5：停止开发服务器并汇总证据**

向开发服务器终端发送 `Ctrl+C`。最终交付说明必须包含：

- 原生 `<select>` 已替换为自定义列表框；
- 五个排序项、鼠标和键盘选择、外部关闭与清除筛选验证结果；
- 桌面和 390px 几何断言；
- 类型检查、构建、控制台错误数和差异检查结果；
- 两个精确源代码提交，不得笼统声称整个脏工作区已提交或清理。
