# 学生端课程购买闭环实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将课程详情的“立即购买”改造成可恢复、不可重复的独立结算与 MOCK 支付闭环，并保留后续真实支付宝/微信支付的统一适配边界。

**架构：** 课程详情只负责区分已选课、免费课程和付费课程；付费课程进入受保护结算页，由独立 MOCK Checkout API 维护订单、支付尝试、持久化与幂等状态，再通过 `PaymentGateway` 接口驱动支付确认。成功页只根据权威订单查询展示成功，登录返回地址、订单页恢复和课程状态刷新共同完成闭环；浏览器返回值永远不能直接写入 `PAID`。

**技术栈：** React 18、TypeScript 5、React Router 6、Zustand 5、Tailwind CSS 3、Lucide React、Day.js、Vite 5、pnpm、真实浏览器 Playwright 验收。

---

## 文件结构

- 修改：`educloud-frontend/student-portal/src/types/index.ts:150-167`
  - 统一订单状态命名，补充权威金额、币种、失效时间、支付时间和支付尝试快照类型。
- 创建：`educloud-frontend/student-portal/src/services/mockCheckoutApi.ts`
  - 唯一的 MOCK 订单与支付状态机，负责本地持久化、幂等、过期、失败/取消和课程权限开通。
- 修改：`educloud-frontend/student-portal/src/services/api.ts:290-319,427-447`
  - 更新种子订单，使用 `createMockCheckoutApi` 替换当前点击即 `PAID` 的 `orderApi.create()`。
- 创建：`educloud-frontend/student-portal/src/services/paymentGateway.ts`
  - 定义与渠道无关的 `PaymentGateway`，实现当前 `MockPaymentGateway`；支付宝/微信作为请求渠道，不在页面中散落分支。
- 创建：`educloud-frontend/student-portal/src/utils/checkoutSession.ts`
  - 维护每门课程稳定的结算幂等键，并校验登录后的站内返回地址。
- 创建：`educloud-frontend/student-portal/src/components/checkout/CheckoutCourseSummary.tsx`
  - 展示课程、讲师、原价、优惠和实付金额。
- 创建：`educloud-frontend/student-portal/src/components/checkout/PaymentMethodSelector.tsx`
  - 支付宝/微信选择器与无障碍选中语义。
- 创建：`educloud-frontend/student-portal/src/components/checkout/PaymentStatusPanel.tsx`
  - 展示确认中、失败、取消和重试反馈。
- 创建：`educloud-frontend/student-portal/src/components/checkout/PurchaseSuccessCard.tsx`
  - 展示权威已支付订单和学习/订单入口。
- 创建：`educloud-frontend/student-portal/src/pages/Checkout.tsx`
  - 恢复或创建待支付订单，调用支付网关并根据查询结果跳转。
- 创建：`educloud-frontend/student-portal/src/pages/CheckoutSuccess.tsx`
  - 按订单 ID 查询结果，确认 `PAID` 后才展示成功。
- 修改：`educloud-frontend/student-portal/src/App.tsx:1-26,33-48`
  - ProtectedRoute 保留原目标地址，注册结算页和成功页。
- 修改：`educloud-frontend/student-portal/src/pages/Login.tsx:1-35`
  - 登录成功后只返回经过白名单校验的站内地址。
- 修改：`educloud-frontend/student-portal/src/pages/CourseDetail.tsx:1-52,105-151`
  - 删除错误的 `/courses` 跳转，补齐已选课、免费选课、付费结算和错误反馈。
- 修改：`educloud-frontend/student-portal/src/pages/Orders.tsx:1-29,91-135`
  - 适配正式订单状态和金额字段，为待支付订单提供真正可用的“继续支付”。
- 参考：`docs/superpowers/specs/2026-08-19-student-course-purchase-flow-design.md`
  - 已确认的交互、可信边界、异常矩阵和成功标准。

## 工作区保护

当前工作区存在大量用户未提交改动，其中 `App.tsx`、`CourseDetail.tsx` 和 `services/api.ts` 已经是脏文件。实施前必须逐个阅读它们的当前差异，并始终在当前内容上应用最小补丁。禁止执行全仓格式化、`git reset`、`git checkout --`、`git clean`、stash 或批量暂存。

源代码实现阶段不提交任何包含任务开始前既有差异的文件。只有当某个目标文件在任务开始前已确认干净，或者是本计划新建文件时，才允许精确暂存；若一个原子提交依赖脏文件中的未提交补丁，则整组文件保持未暂存，在最终交付中逐项列明。计划文档自身单独提交。

### 任务 1：建立购买缺陷红灯与工作区基线

**文件：**
- 修改：无
- 测试：真实浏览器 `/courses/12` 购买路径

- [ ] **步骤 1：记录所有目标文件的初始状态**

从仓库根运行：

```powershell
$targets = @(
  'educloud-frontend/student-portal/src/types/index.ts',
  'educloud-frontend/student-portal/src/services/api.ts',
  'educloud-frontend/student-portal/src/App.tsx',
  'educloud-frontend/student-portal/src/pages/Login.tsx',
  'educloud-frontend/student-portal/src/pages/CourseDetail.tsx',
  'educloud-frontend/student-portal/src/pages/Orders.tsx'
)
git status --short -- $targets
git diff -- $targets
```

预期：输出会显示 `App.tsx`、`CourseDetail.tsx` 和 `api.ts` 的既有改动。保存该输出作为保护基线；后续不得把这些既有差异解释为本任务产出。

- [ ] **步骤 2：启动学生端开发服务器**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run dev -- --host 127.0.0.1 --port 4178
```

预期：Vite 输出 `http://127.0.0.1:4178/`，进程保持运行。

- [ ] **步骤 3：执行当前缺陷红灯**

使用 Playwright 执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4178/courses/12');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: '立即购买', exact: true }).click();

  const pathname = new URL(page.url()).pathname;
  if (pathname !== '/checkout/12') {
    throw new Error(`buy now did not enter checkout: ${pathname}`);
  }
}
```

预期：FAIL，错误包含 `buy now did not enter checkout: /courses`。这证明测试捕获的是用户报告的真实缺陷。

- [ ] **步骤 4：确认尚无伪成功**

执行：

```ts
async (page) => {
  const successHeading = await page
    .getByRole('heading', { name: '课程购买成功', exact: true })
    .count();
  if (successHeading !== 0) {
    throw new Error('current flow rendered an untrusted success state');
  }
}
```

预期：PASS，当前页面没有支付成功语义；红灯只针对缺少结算闭环。

### 任务 2：建立订单与支付领域类型

**文件：**
- 修改：`educloud-frontend/student-portal/src/types/index.ts:150-167`
- 测试：学生端 TypeScript 类型检查

- [ ] **步骤 1：用后端一致的状态替换现有简写类型**

将订单类型区域替换为：

```ts
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CANCELLED'
  | 'CLOSED'
  | 'REFUNDING'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

export type PaymentMethod = 'ALIPAY' | 'WECHAT';

export type PaymentAttemptStatus =
  | 'ACTIVE'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export type MockPaymentOutcome = 'SUCCESS' | 'FAILED' | 'CANCELLED';

export interface Order {
  id: string;
  orderNo: string;
  courseId: number;
  courseTitle: string;
  courseCover: string;
  originalAmount: number;
  payableAmount: number;
  currency: 'CNY';
  paymentMethod?: PaymentMethod;
  status: OrderStatus;
  createdAt: string;
  expiresAt: string;
  paidAt?: string;
}

export interface PaymentStatusSnapshot {
  paymentId: string;
  attemptId: string;
  orderId: string;
  channel: PaymentMethod;
  status: PaymentAttemptStatus;
  failureCode?: string;
  providerCreatedAt: string;
  updatedAt: string;
}

export interface PaymentRequest {
  orderId: string;
  channel: PaymentMethod;
}
```

不要把 `PROCESSING` 或 `FAILED` 添加到 `OrderStatus`；它们属于支付或页面状态。

- [ ] **步骤 2：运行类型检查并记录预期红灯**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
```

预期：FAIL，错误集中在 `api.ts` 和 `Orders.tsx`，包含旧的 `PENDING`、`amount` 或必填订单字段缺失。该红灯证明类型契约会强制后续调用方同步迁移。

### 任务 3：实现持久化 MOCK Checkout API

**文件：**
- 创建：`educloud-frontend/student-portal/src/services/mockCheckoutApi.ts`
- 修改：`educloud-frontend/student-portal/src/services/api.ts:290-319,427-447`
- 测试：TypeScript 类型检查和浏览器状态恢复

- [ ] **步骤 1：创建唯一的 MOCK 订单与支付状态机**

创建 `mockCheckoutApi.ts`，写入：

```ts
import dayjs from 'dayjs';
import type {
  Course,
  MockPaymentOutcome,
  Order,
  PaymentMethod,
  PaymentStatusSnapshot,
} from '@/types';

const STORAGE_KEY = 'educloud:mock-checkout:v1';
const OUTCOME_KEY = 'educloud:mock-payment-outcome';
const CONFIRM_DELAY_MS = 650;

interface PersistedPayment extends PaymentStatusSnapshot {
  mockOutcome: MockPaymentOutcome;
}

interface PersistedState {
  orders: Order[];
  payments: PersistedPayment[];
  idempotency: Record<string, string>;
}

interface CourseRepository {
  getCourse: (courseId: number) => Course | undefined;
  grantCourseAccess: (courseId: number) => void;
}

interface CreateMockCheckoutApiOptions {
  seedOrders: Order[];
  courses: CourseRepository;
}

const emptyState = (): PersistedState => ({
  orders: [],
  payments: [],
  idempotency: {},
});

const loadState = (): PersistedState => {
  if (typeof window === 'undefined') return emptyState();
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) as PersistedState : emptyState();
  } catch {
    return emptyState();
  }
};

const saveState = (state: PersistedState) => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }
};

const wait = <T>(value: T, ms = 250) =>
  new Promise<T>((resolve) => window.setTimeout(() => resolve(value), ms));

const nowText = () => dayjs().format('YYYY-MM-DD HH:mm:ss');
const id = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

export function createMockCheckoutApi({ seedOrders, courses }: CreateMockCheckoutApiOptions) {
  const state = loadState();

  const allOrders = () => [...state.orders, ...seedOrders];

  const normalizeExpiry = (order: Order) => {
    if (order.status === 'PENDING_PAYMENT' && dayjs(order.expiresAt).isBefore(dayjs())) {
      order.status = 'CLOSED';
      saveState(state);
    }
    return order;
  };

  state.orders
    .filter((order) => order.status === 'PAID')
    .forEach((order) => courses.grantCourseAccess(order.courseId));

  const settleActivePayment = async (payment: PersistedPayment) => {
    if (payment.status !== 'ACTIVE') return payment;
    const elapsed = dayjs().diff(dayjs(payment.providerCreatedAt), 'millisecond');
    if (elapsed < CONFIRM_DELAY_MS) return payment;

    payment.status = payment.mockOutcome;
    payment.updatedAt = nowText();
    if (payment.mockOutcome === 'FAILED') payment.failureCode = 'MOCK_CHANNEL_REJECTED';

    const order = state.orders.find((item) => item.id === payment.orderId);
    if (order && payment.mockOutcome === 'SUCCESS') {
      order.status = 'PAID';
      order.paymentMethod = payment.channel;
      order.paidAt = payment.updatedAt;
      courses.grantCourseAccess(order.courseId);
    }
    saveState(state);
    return payment;
  };

  const orderApi = {
    getAll: async () => wait(allOrders().map(normalizeExpiry)),
    getById: async (orderId: string) => {
      const found = allOrders().find((order) => order.id === orderId);
      return wait(found ? normalizeExpiry(found) : undefined);
    },
    getPayableByCourse: async (courseId: number) => {
      const found = allOrders()
        .map(normalizeExpiry)
        .find((order) =>
          order.courseId === courseId &&
          (order.status === 'PAID' || order.status === 'PENDING_PAYMENT'));
      return wait(found);
    },
    create: async (courseId: number, idempotencyKey: string) => {
      const course = courses.getCourse(courseId);
      if (!course) throw new Error('COURSE_NOT_FOUND');
      if (course.price <= 0) throw new Error('FREE_COURSE_REQUIRES_ENROLLMENT');

      const existingId = state.idempotency[idempotencyKey];
      const existing = existingId
        ? state.orders.find((order) => order.id === existingId)
        : undefined;
      if (existing) return wait(normalizeExpiry(existing));

      const payable = allOrders()
        .map(normalizeExpiry)
        .find((order) =>
          order.courseId === courseId &&
          (order.status === 'PAID' || order.status === 'PENDING_PAYMENT'));
      if (payable) return wait(payable);

      const createdAt = nowText();
      const order: Order = {
        id: id('order'),
        orderNo: `EC${dayjs().format('YYYYMMDDHHmmss')}`,
        courseId: course.id,
        courseTitle: course.title,
        courseCover: course.cover,
        originalAmount: course.originalPrice ?? course.price,
        payableAmount: course.price,
        currency: 'CNY',
        status: 'PENDING_PAYMENT',
        createdAt,
        expiresAt: dayjs().add(30, 'minute').format('YYYY-MM-DD HH:mm:ss'),
      };
      state.orders.unshift(order);
      state.idempotency[idempotencyKey] = order.id;
      saveState(state);
      return wait(order);
    },
    cancel: async (orderId: string) => {
      const order = state.orders.find((item) => item.id === orderId);
      if (!order) throw new Error('ORDER_NOT_FOUND');
      if (order.status !== 'PENDING_PAYMENT') throw new Error('ORDER_STATUS_CONFLICT');
      order.status = 'CANCELLED';
      saveState(state);
      return wait(order);
    },
  };

  const paymentApi = {
    create: async (orderId: string, channel: PaymentMethod) => {
      const order = state.orders.find((item) => item.id === orderId);
      if (!order) throw new Error('ORDER_NOT_FOUND');
      normalizeExpiry(order);
      if (order.status !== 'PENDING_PAYMENT') throw new Error('ORDER_NOT_PAYABLE');

      const active = state.payments.find(
        (payment) => payment.orderId === orderId && payment.status === 'ACTIVE',
      );
      if (active) return wait(active);

      const createdAt = nowText();
      const configuredOutcome = typeof window === 'undefined'
        ? null
        : window.sessionStorage.getItem(OUTCOME_KEY);
      const mockOutcome: MockPaymentOutcome =
        configuredOutcome === 'FAILED' || configuredOutcome === 'CANCELLED'
          ? configuredOutcome
          : 'SUCCESS';
      const payment: PersistedPayment = {
        paymentId: id('payment'),
        attemptId: id('attempt'),
        orderId,
        channel,
        status: 'ACTIVE',
        providerCreatedAt: createdAt,
        updatedAt: createdAt,
        mockOutcome,
      };
      state.payments.unshift(payment);
      saveState(state);
      return wait(payment);
    },
    getByOrderId: async (orderId: string) => {
      const payment = state.payments.find((item) => item.orderId === orderId);
      if (!payment) return wait(undefined);
      const settled = await settleActivePayment(payment);
      return wait(settled);
    },
  };

  return { orderApi, paymentApi };
}
```

该文件是 MOCK 订单和支付的唯一可变数据源；页面不得再直接修改订单数组。

- [ ] **步骤 2：迁移种子订单到新字段**

在 `api.ts` 的 `generateOrders()` 中：

```ts
const statuses: Order['status'][] = [
  'PAID',
  'PAID',
  'PAID',
  'PENDING_PAYMENT',
  'REFUNDED',
  'CANCELLED',
];
```

每个订单对象将 `amount` 替换为：

```ts
originalAmount: c.originalPrice ?? c.price,
payableAmount: c.price,
currency: 'CNY',
expiresAt: createdAt.add(30, 'minute').format('YYYY-MM-DD HH:mm:ss'),
paidAt: statuses[i % statuses.length] === 'PAID'
  ? createdAt.add(2, 'minute').format('YYYY-MM-DD HH:mm:ss')
  : undefined,
```

- [ ] **步骤 3：替换旧 orderApi**

在 `api.ts` 顶部添加：

```ts
import { createMockCheckoutApi } from './mockCheckoutApi';
```

删除旧的 `export const orderApi = { ... }`，在 `courseApi` 声明之后、其他调用方导出之前添加：

```ts
export const { orderApi, paymentApi } = createMockCheckoutApi({
  seedOrders: orders,
  courses: {
    getCourse: (courseId) => courses.find((course) => course.id === courseId),
    grantCourseAccess: (courseId) => {
      const course = courses.find((item) => item.id === courseId);
      if (course) {
        course.enrolled = true;
        course.progress = 0;
      }
    },
  },
});
```

不要保留第二套旧 `orderApi.create()`。

- [ ] **步骤 4：运行类型检查**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
```

预期：仍可能只在 `Orders.tsx` 报旧字段错误；`mockCheckoutApi.ts` 和 `api.ts` 不产生新错误。若 `api.ts` 的既有用户改动出现错误，只修复与本任务新增类型直接相关的行。

### 任务 4：实现支付网关、幂等会话和安全返回地址

**文件：**
- 创建：`educloud-frontend/student-portal/src/services/paymentGateway.ts`
- 创建：`educloud-frontend/student-portal/src/utils/checkoutSession.ts`
- 测试：TypeScript 类型检查

- [ ] **步骤 1：实现统一 PaymentGateway**

创建 `paymentGateway.ts`：

```ts
import { paymentApi } from '@/services/api';
import type { PaymentRequest, PaymentStatusSnapshot } from '@/types';

export interface PaymentGateway {
  initiate(request: PaymentRequest): Promise<PaymentStatusSnapshot>;
  query(orderId: string): Promise<PaymentStatusSnapshot | undefined>;
}

export class MockPaymentGateway implements PaymentGateway {
  async initiate(request: PaymentRequest) {
    await paymentApi.create(request.orderId, request.channel);
    await new Promise((resolve) => window.setTimeout(resolve, 700));
    const result = await paymentApi.getByOrderId(request.orderId);
    if (!result) throw new Error('PAYMENT_STATUS_MISSING');
    return result;
  }

  query(orderId: string) {
    return paymentApi.getByOrderId(orderId);
  }
}

export const paymentGateway: PaymentGateway = new MockPaymentGateway();
```

支付宝和微信是 `PaymentRequest.channel`，不是两个页面实现。真实渠道接入时只替换 `paymentGateway` 注入，不改 Checkout 页面。

- [ ] **步骤 2：实现稳定幂等键与安全重定向**

创建 `checkoutSession.ts`：

```ts
const INTENT_PREFIX = 'educloud:checkout-intent:';

export function getCheckoutIntentKey(courseId: number) {
  const storageKey = `${INTENT_PREFIX}${courseId}`;
  const existing = window.sessionStorage.getItem(storageKey);
  if (existing) return existing;
  const created = window.crypto.randomUUID();
  window.sessionStorage.setItem(storageKey, created);
  return created;
}

export function clearCheckoutIntentKey(courseId: number) {
  window.sessionStorage.removeItem(`${INTENT_PREFIX}${courseId}`);
}

export function getSafeInternalRedirect(value: string | null, fallback = '/') {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return fallback;
  try {
    const url = new URL(value, window.location.origin);
    return url.origin === window.location.origin
      ? `${url.pathname}${url.search}${url.hash}`
      : fallback;
  } catch {
    return fallback;
  }
}
```

- [ ] **步骤 3：运行类型检查**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
```

预期：新建的两个文件没有错误；剩余错误仅来自尚未迁移的页面。

### 任务 5：实现结算展示组件

**文件：**
- 创建：`educloud-frontend/student-portal/src/components/checkout/CheckoutCourseSummary.tsx`
- 创建：`educloud-frontend/student-portal/src/components/checkout/PaymentMethodSelector.tsx`
- 创建：`educloud-frontend/student-portal/src/components/checkout/PaymentStatusPanel.tsx`
- 创建：`educloud-frontend/student-portal/src/components/checkout/PurchaseSuccessCard.tsx`
- 测试：TypeScript 类型检查

- [ ] **步骤 1：实现课程与金额摘要**

`CheckoutCourseSummary.tsx`：

```tsx
import type { Course } from '@/types';

export default function CheckoutCourseSummary({ course }: { course: Course }) {
  const originalAmount = course.originalPrice ?? course.price;
  const discount = originalAmount - course.price;

  return (
    <section aria-labelledby="checkout-course-title" className="rounded-3xl border border-white/70 bg-white/80 p-6 shadow-xl shadow-indigo-950/5 backdrop-blur-xl">
      <div className="flex flex-col gap-5 sm:flex-row">
        <img src={course.cover} alt="" className="h-32 w-full rounded-2xl object-cover sm:w-52" />
        <div className="min-w-0 flex-1">
          <p className="text-sm text-ink-400">{course.teacherName} · 永久访问</p>
          <h2 id="checkout-course-title" className="mt-2 font-display text-2xl font-bold text-ink-900">{course.title}</h2>
          <div className="mt-5 flex flex-wrap items-baseline gap-3">
            <strong className="font-display text-3xl text-indigo-800">¥{course.price}</strong>
            {discount > 0 && <><span className="text-ink-300 line-through">¥{originalAmount}</span><span className="text-sm text-amber-600">立省 ¥{discount}</span></>}
          </div>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **步骤 2：实现支付方式选择器**

`PaymentMethodSelector.tsx`：

```tsx
import { CheckCircle2, Smartphone } from 'lucide-react';
import type { PaymentMethod } from '@/types';
import { cn } from '@/utils/cn';

const methods: Array<{ value: PaymentMethod; label: string; description: string }> = [
  { value: 'ALIPAY', label: '支付宝', description: '安全快捷支付' },
  { value: 'WECHAT', label: '微信支付', description: '使用微信完成支付' },
];

export default function PaymentMethodSelector({ value, onChange }: { value: PaymentMethod; onChange: (value: PaymentMethod) => void }) {
  return (
    <fieldset>
      <legend className="mb-3 text-sm font-semibold text-ink-700">选择支付方式</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        {methods.map((method) => {
          const selected = method.value === value;
          return <button key={method.value} type="button" role="radio" aria-checked={selected} onClick={() => onChange(method.value)} className={cn('flex items-center gap-3 rounded-2xl border p-4 text-left transition-colors', selected ? 'border-indigo-700 bg-indigo-50/70' : 'border-ink-100 bg-white hover:border-ink-200')}>
            <Smartphone className="text-indigo-700" aria-hidden="true" />
            <span className="flex-1"><strong className="block text-ink-900">{method.label}</strong><span className="text-xs text-ink-400">{method.description}</span></span>
            {selected && <CheckCircle2 className="text-indigo-700" aria-hidden="true" />}
          </button>;
        })}
      </div>
    </fieldset>
  );
}
```

- [ ] **步骤 3：实现支付状态反馈**

`PaymentStatusPanel.tsx`：

```tsx
import { AlertCircle, Loader2, XCircle } from 'lucide-react';

interface Props {
  state: 'CONFIRMING' | 'FAILED' | 'CANCELLED';
  onRetry?: () => void;
}

export default function PaymentStatusPanel({ state, onRetry }: Props) {
  const confirming = state === 'CONFIRMING';
  return (
    <div role={confirming ? 'status' : 'alert'} className="rounded-2xl border border-ink-100 bg-white/80 p-4">
      <div className="flex items-start gap-3">
        {confirming ? <Loader2 className="animate-spin text-indigo-700" /> : state === 'FAILED' ? <AlertCircle className="text-red-600" /> : <XCircle className="text-amber-600" />}
        <div className="flex-1">
          <strong className="text-ink-900">{confirming ? '正在确认支付结果' : state === 'FAILED' ? '支付未完成' : '已取消本次支付'}</strong>
          <p className="mt-1 text-sm text-ink-500">{confirming ? '请不要关闭页面，系统正在查询权威订单状态。' : '课程尚未开通，你可以保留当前订单并重新支付。'}</p>
          {!confirming && onRetry && <button type="button" onClick={onRetry} className="mt-3 text-sm font-medium text-indigo-800 underline">重新支付</button>}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **步骤 4：实现成功结果卡**

`PurchaseSuccessCard.tsx`：

```tsx
import { CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Order } from '@/types';

export default function PurchaseSuccessCard({ order }: { order: Order }) {
  return (
    <section className="rounded-3xl border border-white/70 bg-white/85 p-8 text-center shadow-2xl shadow-indigo-950/10 backdrop-blur-xl">
      <CheckCircle2 className="mx-auto h-16 w-16 text-green-600" strokeWidth={1.5} />
      <h1 className="mt-5 font-display text-4xl font-bold text-ink-900">课程购买成功</h1>
      <p className="mt-3 text-ink-500">课程已加入“我的课程”，学习权限已经开通。</p>
      <dl className="mx-auto mt-8 grid max-w-xl gap-3 rounded-2xl bg-paper p-5 text-left text-sm sm:grid-cols-2">
        <div><dt className="text-ink-400">订单编号</dt><dd className="mt-1 font-medium text-ink-800">{order.orderNo}</dd></div>
        <div><dt className="text-ink-400">实付金额</dt><dd className="mt-1 font-medium text-ink-800">¥{order.payableAmount}</dd></div>
      </dl>
      <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
        <Link to={`/learn/${order.courseId}`} className="btn-primary">开始学习</Link>
        <Link to="/orders" className="btn-outline">查看订单</Link>
        <Link to={`/courses/${order.courseId}`} className="px-5 py-3 text-sm text-ink-500 hover:text-indigo-800">返回课程详情</Link>
      </div>
    </section>
  );
}
```

- [ ] **步骤 5：运行类型检查**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
```

预期：四个组件无 TypeScript 错误。

### 任务 6：实现独立结算页

**文件：**
- 创建：`educloud-frontend/student-portal/src/pages/Checkout.tsx`
- 测试：真实浏览器成功、失败和取消路径

- [ ] **步骤 1：实现结算页状态编排**

创建 `Checkout.tsx`。状态和关键方法必须完整采用以下实现；JSX 使用任务 5 的组件按“课程摘要 → 支付方式 → 协议 → 确认按钮”排列：

```tsx
import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { Loader2, ShieldCheck } from 'lucide-react';
import CheckoutCourseSummary from '@/components/checkout/CheckoutCourseSummary';
import PaymentMethodSelector from '@/components/checkout/PaymentMethodSelector';
import PaymentStatusPanel from '@/components/checkout/PaymentStatusPanel';
import { courseApi, orderApi } from '@/services/api';
import { paymentGateway } from '@/services/paymentGateway';
import { clearCheckoutIntentKey, getCheckoutIntentKey } from '@/utils/checkoutSession';
import { useCartStore } from '@/stores/useCartStore';
import type { Course, Order, PaymentMethod } from '@/types';

type ViewState = 'LOADING' | 'READY' | 'CONFIRMING' | 'FAILED' | 'CANCELLED';

export default function Checkout() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const removeFromCart = useCartStore((state) => state.removeFromCart);
  const [course, setCourse] = useState<Course>();
  const [order, setOrder] = useState<Order>();
  const [method, setMethod] = useState<PaymentMethod>('ALIPAY');
  const [viewState, setViewState] = useState<ViewState>('LOADING');
  const [error, setError] = useState('');
  const numericCourseId = Number(courseId);

  const finishPaidOrder = useCallback((paidOrder: Order) => {
    removeFromCart(paidOrder.courseId);
    clearCheckoutIntentKey(paidOrder.courseId);
    navigate(`/checkout/success/${paidOrder.id}`, { replace: true });
  }, [navigate, removeFromCart]);

  useEffect(() => {
    if (!Number.isInteger(numericCourseId)) {
      setError('课程参数无效');
      setViewState('READY');
      return;
    }
    Promise.all([
      courseApi.getById(numericCourseId),
      orderApi.getPayableByCourse(numericCourseId),
    ]).then(([foundCourse, foundOrder]) => {
      if (!foundCourse) {
        setError('课程不存在或已下架');
        setViewState('READY');
        return;
      }
      if (foundCourse.price === 0) {
        navigate(`/courses/${foundCourse.id}`, { replace: true });
        return;
      }
      if (foundOrder?.status === 'PAID' || foundCourse.enrolled) {
        navigate(`/learn/${foundCourse.id}`, { replace: true });
        return;
      }
      setCourse(foundCourse);
      setOrder(foundOrder);
      setViewState('READY');
    }).catch(() => {
      setError('结算信息加载失败，请返回课程详情后重试');
      setViewState('READY');
    });
  }, [navigate, numericCourseId]);

  const confirmPayment = async () => {
    if (!course || viewState === 'CONFIRMING') return;
    setError('');
    setViewState('CONFIRMING');
    try {
      const payableOrder = order ?? await orderApi.create(
        course.id,
        getCheckoutIntentKey(course.id),
      );
      setOrder(payableOrder);
      if (payableOrder.status === 'PAID') {
        finishPaidOrder(payableOrder);
        return;
      }
      const payment = await paymentGateway.initiate({
        orderId: payableOrder.id,
        channel: method,
      });
      const refreshed = await orderApi.getById(payableOrder.id);
      if (payment.status === 'SUCCESS' && refreshed?.status === 'PAID') {
        finishPaidOrder(refreshed);
      } else if (payment.status === 'CANCELLED') {
        setViewState('CANCELLED');
      } else {
        setViewState('FAILED');
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '支付发起失败，请重试');
      setViewState('FAILED');
    }
  };

  if (viewState === 'LOADING') return <div className="flex min-h-[60vh] items-center justify-center"><Loader2 className="animate-spin text-indigo-800" /></div>;
  if (!course && !error) return <Navigate to="/courses" replace />;

  return (
    <main className="min-h-[calc(100vh-6rem)] bg-paper px-4 py-12 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <span className="section-label">确认订单</span>
        <h1 className="display-heading mt-4 text-4xl">安全支付</h1>
        {error && <div role="alert" className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-4 text-red-700">{error}</div>}
        {course && <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_22rem]">
          <CheckoutCourseSummary course={course} />
          <aside className="rounded-3xl border border-white/70 bg-white/85 p-6 shadow-xl shadow-indigo-950/5 backdrop-blur-xl">
            <PaymentMethodSelector value={method} onChange={setMethod} />
            <div className="my-6 border-t border-ink-100" />
            <p className="flex gap-2 text-xs leading-6 text-ink-500"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-green-600" />点击确认支付即表示同意购买协议与退款规则；支付结果以订单查询为准。</p>
            {(viewState === 'CONFIRMING' || viewState === 'FAILED' || viewState === 'CANCELLED') && <div className="mt-5"><PaymentStatusPanel state={viewState} onRetry={viewState === 'CONFIRMING' ? undefined : confirmPayment} /></div>}
            <button type="button" disabled={viewState === 'CONFIRMING'} onClick={confirmPayment} className="btn-primary mt-6 w-full disabled:cursor-not-allowed disabled:opacity-60">{viewState === 'CONFIRMING' ? '正在确认支付结果…' : `确认支付 ¥${course.price}`}</button>
            <Link to={`/courses/${course.id}`} className="mt-3 block text-center text-sm text-ink-400 hover:text-indigo-800">返回课程详情</Link>
          </aside>
        </div>}
      </div>
    </main>
  );
}
```

- [ ] **步骤 2：类型检查结算页**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
```

预期：`Checkout.tsx` 没有错误；尚未迁移的 `Orders.tsx` 仍可能红灯。

### 任务 7：实现成功页、受保护路由与登录返回

**文件：**
- 创建：`educloud-frontend/student-portal/src/pages/CheckoutSuccess.tsx`
- 修改：`educloud-frontend/student-portal/src/App.tsx:1-26,33-48`
- 修改：`educloud-frontend/student-portal/src/pages/Login.tsx:1-35`
- 测试：受保护路由和伪成功防护

- [ ] **步骤 1：实现只信任订单查询的成功页**

创建 `CheckoutSuccess.tsx`：

```tsx
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import PaymentStatusPanel from '@/components/checkout/PaymentStatusPanel';
import PurchaseSuccessCard from '@/components/checkout/PurchaseSuccessCard';
import { orderApi } from '@/services/api';
import { paymentGateway } from '@/services/paymentGateway';
import type { Order } from '@/types';

export default function CheckoutSuccess() {
  const { orderId } = useParams<{ orderId: string }>();
  const [order, setOrder] = useState<Order>();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!orderId) {
      setMessage('订单参数无效');
      setLoading(false);
      return;
    }
    let cancelled = false;
    const load = async () => {
      await paymentGateway.query(orderId);
      const found = await orderApi.getById(orderId);
      if (cancelled) return;
      if (!found) setMessage('订单不存在或无权访问');
      else if (found.status !== 'PAID') setMessage('支付结果尚未确认，请从订单页继续支付');
      else setOrder(found);
      setLoading(false);
    };
    void load();
    return () => { cancelled = true; };
  }, [orderId]);

  if (loading) return <div className="flex min-h-[60vh] items-center justify-center"><Loader2 className="animate-spin text-indigo-800" /></div>;
  return <main className="min-h-[calc(100vh-6rem)] bg-paper px-4 py-16"><div className="mx-auto max-w-4xl">{order ? <PurchaseSuccessCard order={order} /> : <div className="rounded-3xl bg-white p-8 text-center"><PaymentStatusPanel state="FAILED" /><p className="mt-4 text-ink-500">{message}</p><Link to="/orders" className="btn-primary mt-6">查看我的订单</Link></div>}</div></main>;
}
```

- [ ] **步骤 2：让 ProtectedRoute 保留原始目标地址**

在 `App.tsx` 从 React Router 导入 `useLocation`，将 ProtectedRoute 改为：

```tsx
function ProtectedRoute({ children }: { children: JSX.Element }) {
  const token = useAuthStore((state) => state.token);
  const location = useLocation();
  if (!token) {
    const redirect = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace />;
  }
  return children;
}
```

导入两个新页面并在 MainLayout 内注册：

```tsx
<Route path="checkout/:courseId" element={<ProtectedRoute><Checkout /></ProtectedRoute>} />
<Route path="checkout/success/:orderId" element={<ProtectedRoute><CheckoutSuccess /></ProtectedRoute>} />
```

- [ ] **步骤 3：登录后消费安全返回地址**

`Login.tsx` 改为从 React Router 导入 `useSearchParams`，从工具文件导入 `getSafeInternalRedirect`，并在组件内声明：

```tsx
const [searchParams] = useSearchParams();
const redirectTo = getSafeInternalRedirect(searchParams.get('redirect'));
```

将已登录重定向和登录成功导航分别改为：

```tsx
if (token) return <Navigate to={redirectTo} replace />;
```

```tsx
if (success) navigate(redirectTo, { replace: true });
```

- [ ] **步骤 4：运行安全返回红绿验证**

在新的无登录浏览器上下文中执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4178/checkout/12');
  if (!page.url().includes('/login?redirect=')) throw new Error('protected checkout lost return target');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL('**/checkout/12');

  await page.evaluate(() => localStorage.removeItem('student_token'));
  await page.goto('http://127.0.0.1:4178/login?redirect=https://evil.example');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL('http://127.0.0.1:4178/');
}
```

预期：PASS；正常目标返回结算页，外部 URL 被降级为首页。

- [ ] **步骤 5：验证伪造成功页不会成功**

执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4178/checkout/success/not-an-order');
  if (await page.getByRole('heading', { name: '课程购买成功' }).count()) {
    throw new Error('forged success URL rendered success');
  }
  await page.getByText('订单不存在或无权访问').waitFor();
}
```

预期：PASS，不出现成功标题和学习权限承诺。

### 任务 8：接入课程详情与订单恢复入口

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/CourseDetail.tsx:1-52,105-151`
- 修改：`educloud-frontend/student-portal/src/pages/Orders.tsx:1-29,91-135`
- 测试：付费、免费、已购买和待支付路径

- [ ] **步骤 1：修复课程详情购买按钮**

在 `CourseDetail.tsx` 中保留现有加入购物车函数，新增 `courseApi`、`useAuthStore` 和 `useSearchParams` 依赖，并实现：

```tsx
const token = useAuthStore((state) => state.token);
const [searchParams] = useSearchParams();
const [enrolling, setEnrolling] = useState(false);
const [purchaseError, setPurchaseError] = useState('');

const enrollFreeCourse = useCallback(async () => {
  const course = currentCourse;
  if (!course || course.price !== 0 || course.enrolled || enrolling) return;
  if (!token) {
    const redirect = `/courses/${course.id}?intent=enroll`;
    navigate(`/login?redirect=${encodeURIComponent(redirect)}`);
    return;
  }
  setEnrolling(true);
  setPurchaseError('');
  try {
    await courseApi.enroll(course.id);
    await fetchCourse(String(course.id));
    navigate(`/learn/${course.id}`);
  } catch {
    setPurchaseError('免费选课失败，请稍后重试');
  } finally {
    setEnrolling(false);
  }
}, [currentCourse, enrolling, fetchCourse, navigate, token]);

useEffect(() => {
  if (searchParams.get('intent') === 'enroll' && token && currentCourse?.price === 0 && !currentCourse.enrolled) {
    void enrollFreeCourse();
  }
}, [currentCourse, enrollFreeCourse, searchParams, token]);
```

上述状态、回调和自动选课 Effect 必须放在现有加载态条件返回之前；加载完成后再保留现有 `const course = currentCourse` 供 JSX 使用，确保 Hook 调用顺序稳定。

将价格卡按钮区域改为互斥分支：

```tsx
{course.enrolled ? (
  <Link to={`/learn/${course.id}`} className="btn-primary w-full"><Play size={16} />继续学习</Link>
) : course.price === 0 ? (
  <button type="button" disabled={enrolling} onClick={enrollFreeCourse} className="btn-primary w-full disabled:opacity-60">{enrolling ? '正在加入…' : '免费加入学习'}</button>
) : (
  <>
    <button type="button" onClick={handleAddToCart} className={cn('w-full py-3 font-medium text-sm transition-all duration-300 flex items-center justify-center gap-2', added || inCart ? 'bg-green-600 text-white' : 'bg-amber-600 text-white hover:bg-amber-500')}>{added || inCart ? <><Check size={16} />已加入购物车</> : <><ShoppingCart size={16} />加入购物车</>}</button>
    <button type="button" onClick={() => navigate(`/checkout/${course.id}`)} className="btn-outline w-full">立即购买</button>
  </>
)}
{purchaseError && <p role="alert" className="text-sm text-red-600">{purchaseError}</p>}
```

必须彻底删除 `navigate('/courses')`。已选课状态下不能继续显示“立即购买”。

- [ ] **步骤 2：迁移订单页状态配置**

`Orders.tsx` 的状态配置改为：

```tsx
const statusConfig: Record<OrderStatus, { label: string; className: string; icon: typeof Clock }> = {
  PENDING_PAYMENT: { label: '待支付', className: 'badge-amber', icon: Clock },
  PAID: { label: '已支付', className: 'badge-green', icon: CheckCircle },
  CANCELLED: { label: '已取消', className: 'badge-red', icon: XCircle },
  CLOSED: { label: '已关闭', className: 'badge-red', icon: XCircle },
  REFUNDING: { label: '退款中', className: 'badge-amber', icon: Clock },
  PARTIALLY_REFUNDED: { label: '部分退款', className: 'badge-amber', icon: Clock },
  REFUNDED: { label: '已退款', className: 'badge-red', icon: XCircle },
};
```

累计消费和金额显示分别使用：

```tsx
.reduce((sum, order) => sum + order.payableAmount, 0)
```

```tsx
¥{order.payableAmount}
```

待支付操作改为真实入口：

```tsx
{order.status === 'PENDING_PAYMENT' ? (
  <Link to={`/checkout/${order.courseId}`} className="btn-primary !px-4 !py-2 text-xs">继续支付</Link>
) : order.status === 'PAID' ? (
  <Link to={`/learn/${order.courseId}`} className="text-sm text-indigo-800 link-underline">开始学习</Link>
) : (
  <span className="text-sm text-ink-300">--</span>
)}
```

- [ ] **步骤 3：运行完整类型检查**

运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
```

预期：退出码 `0`，旧 `PENDING` 和 `amount` 不再存在，所有新增页面、组件和服务类型一致。

- [ ] **步骤 4：重新运行最初红灯并确认转绿**

执行任务 1 的购买断言。

预期：PASS，点击课程 12 的“立即购买”进入 `/checkout/12`，不再跳到 `/courses`。

- [ ] **步骤 5：验证免费课程不创建订单**

使用新上下文登录后执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4178/courses/10');
  const before = await page.evaluate(() => localStorage.getItem('educloud:mock-checkout:v1'));
  await page.getByRole('button', { name: '免费加入学习' }).click();
  await page.waitForURL('**/learn/10');
  const after = await page.evaluate(() => localStorage.getItem('educloud:mock-checkout:v1'));
  if (before !== after) throw new Error('free enrollment created checkout state');
}
```

预期：PASS，进入学习页且 Checkout 存储未新增订单。

### 任务 9：完成 MOCK 支付场景矩阵与最终验收

**文件：**
- 修改：无；若验收发现问题，只允许对本计划列出的目标文件做最小修复
- 测试：真实浏览器、类型检查、生产构建、差异检查

- [ ] **步骤 1：验证成功闭环和刷新恢复**

在清空 `educloud:mock-checkout:v1` 的新浏览器上下文中登录，执行：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4178/courses/12');
  await page.getByRole('button', { name: '立即购买' }).click();
  await page.waitForURL('**/checkout/12');
  await page.getByRole('radio', { name: /微信支付/ }).click();
  await page.getByRole('button', { name: '确认支付 ¥349' }).click();
  await page.waitForURL('**/checkout/success/**');
  await page.getByRole('heading', { name: '课程购买成功' }).waitFor();
  await page.reload();
  await page.getByRole('heading', { name: '课程购买成功' }).waitFor();
  await page.getByRole('link', { name: '返回课程详情' }).click();
  await page.getByRole('link', { name: '继续学习' }).waitFor();
}
```

预期：PASS；支付成功、刷新后仍成功、课程详情不再出现购买按钮。

- [ ] **步骤 2：验证失败后重试只产生一笔订单**

新上下文中设置失败结果：

```ts
async (page) => {
  await page.addInitScript(() => {
    localStorage.setItem('student_token', 'mock-test-token');
    sessionStorage.setItem('educloud:mock-payment-outcome', 'FAILED');
  });
  await page.goto('http://127.0.0.1:4178/checkout/14');
  await page.getByRole('button', { name: /确认支付/ }).click();
  await page.getByText('支付未完成').waitFor();
  const firstCount = await page.evaluate(() => {
    const raw = localStorage.getItem('educloud:mock-checkout:v1');
    return raw ? JSON.parse(raw).orders.filter((order: { courseId: number }) => order.courseId === 14).length : 0;
  });
  await page.evaluate(() => sessionStorage.setItem('educloud:mock-payment-outcome', 'SUCCESS'));
  await page.getByRole('button', { name: '重新支付' }).click();
  await page.waitForURL('**/checkout/success/**');
  const secondCount = await page.evaluate(() => {
    const raw = localStorage.getItem('educloud:mock-checkout:v1');
    return raw ? JSON.parse(raw).orders.filter((order: { courseId: number }) => order.courseId === 14).length : 0;
  });
  if (firstCount !== 1 || secondCount !== 1) throw new Error(`duplicate orders: ${firstCount}/${secondCount}`);
}
```

预期：PASS；失败不授予权限，重试复用订单并最终成功。

- [ ] **步骤 3：验证取消支付不产生权限**

新上下文中将 `educloud:mock-payment-outcome` 设为 `CANCELLED`，购买课程 15：

```ts
async (page) => {
  await page.goto('http://127.0.0.1:4178/checkout/15');
  await page.getByRole('button', { name: /确认支付/ }).click();
  await page.getByText('已取消本次支付').waitFor();
  await page.goto('http://127.0.0.1:4178/courses/15');
  if ((await page.getByRole('button', { name: '立即购买' }).count()) !== 1) {
    throw new Error('cancelled payment incorrectly granted course access');
  }
}
```

预期：PASS，课程仍可购买且没有学习权限。

- [ ] **步骤 4：验证重复点击和待支付恢复**

在结算页快速双击确认按钮，随后统计同课程订单和活动支付尝试：

```ts
async (page) => {
  const button = page.getByRole('button', { name: /确认支付/ });
  await Promise.all([button.click(), button.click({ force: true })]);
  const counts = await page.evaluate(() => {
    const state = JSON.parse(localStorage.getItem('educloud:mock-checkout:v1') ?? '{}');
    return {
      orders: (state.orders ?? []).filter((order: { courseId: number }) => order.courseId === 16).length,
      active: (state.payments ?? []).filter((payment: { orderId: string; status: string }) => payment.status === 'ACTIVE').length,
    };
  });
  if (counts.orders !== 1 || counts.active > 1) throw new Error(`idempotency broken: ${JSON.stringify(counts)}`);
}
```

预期：PASS，只存在一笔课程订单，活动支付尝试最多一个。若 Playwright 因按钮立即禁用而拒绝第二次点击，也视为 UI 防重复生效，但仍必须检查持久化计数。

- [ ] **步骤 5：检查购物车、订单页和我的课程一致性**

购买前先把课程 12 加入购物车；成功后断言导航栏购物车数量减少、`/orders` 显示同一订单为“已支付”、`/my-courses` 出现课程 12，并且订单页不存在对该订单的“继续支付”。

预期：三处状态一致；不能只验证成功页文案。

- [ ] **步骤 6：检查桌面与移动端几何和控制台**

分别使用 `1440 × 900` 和 `390 × 844` 打开结算页、确认中状态和成功页，断言：

```ts
async (page) => {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  if (overflow) throw new Error('checkout flow has horizontal overflow');
  const errors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
}
```

预期：无横向溢出；按钮不遮挡协议；控制台没有新增 error。监听器应在导航前注册，最终输出实际错误数组。

- [ ] **步骤 7：运行最终工程验证**

从仓库根运行：

```powershell
pnpm --dir educloud-frontend/student-portal run typecheck
pnpm --dir educloud-frontend/student-portal run build
git diff --check -- `
  'educloud-frontend/student-portal/src/types/index.ts' `
  'educloud-frontend/student-portal/src/services/mockCheckoutApi.ts' `
  'educloud-frontend/student-portal/src/services/api.ts' `
  'educloud-frontend/student-portal/src/services/paymentGateway.ts' `
  'educloud-frontend/student-portal/src/utils/checkoutSession.ts' `
  'educloud-frontend/student-portal/src/components/checkout' `
  'educloud-frontend/student-portal/src/pages/Checkout.tsx' `
  'educloud-frontend/student-portal/src/pages/CheckoutSuccess.tsx' `
  'educloud-frontend/student-portal/src/App.tsx' `
  'educloud-frontend/student-portal/src/pages/Login.tsx' `
  'educloud-frontend/student-portal/src/pages/CourseDetail.tsx' `
  'educloud-frontend/student-portal/src/pages/Orders.tsx'
```

预期：

- `typecheck` 退出码 `0`；
- `build` 退出码 `0`，Vite 成功生成 `dist`；
- `git diff --check` 退出码 `0`；
- 浏览器红灯已转绿，成功、失败、取消、重试、刷新、登录返回、免费课程和伪成功防护均有实际证据；
- 不得把 MOCK 支付描述为真实支付宝/微信支付已接通。

- [ ] **步骤 8：复核工作区保护并停止服务器**

运行：

```powershell
git status --short
git diff --stat
```

逐项对照任务 1 的初始差异，确认用户原有改动仍在。不要暂存或提交 `App.tsx`、`CourseDetail.tsx`、`api.ts` 的既有差异；向 Vite 终端发送 `Ctrl+C`。最终交付列出本任务新增/修改文件、未提交边界、所有验证命令的实际退出码和浏览器场景结果。
