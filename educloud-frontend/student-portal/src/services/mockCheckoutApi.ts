import dayjs from 'dayjs';
import type {
  MockPaymentOutcome,
  Order,
  PaymentMethod,
  PaymentStatusSnapshot,
} from '@/types';

/** 结算 mock 需要的课程最小信息（真实课程由 courseApi 解析而来）。 */
export interface CourseInfo {
  id: string;
  title: string;
  price: number;
  cover: string;
}

import { useAuthStore } from '@/stores/useAuthStore';

const getCheckoutStorageKey = (): string => {
  try {
    const user = useAuthStore.getState().user;
    if (user?.id) return `educloud:mock-checkout:${user.id}:v1`;
    const token = localStorage.getItem('student_token');
    if (token) {
      const parts = token.split('.');
      if (parts.length === 3) {
        const payload = JSON.parse(atob(parts[1]));
        const uid = payload.userId || payload.sub || payload.id;
        if (uid) return `educloud:mock-checkout:${uid}:v1`;
      }
    }
  } catch {
    // ignore
  }
  return 'educloud:mock-checkout:anonymous:v1';
};

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
  getCourse: (courseId: string) => Promise<CourseInfo | undefined>;
  grantCourseAccess: (courseId: string) => void;
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

/** 防御性校验 localStorage 解析结果，结构损坏时平滑回退，防止整页白屏崩溃 */
const isValidState = (obj: unknown): obj is PersistedState => {
  if (typeof obj !== 'object' || obj === null) return false;
  const s = obj as Record<string, unknown>;
  return (
    Array.isArray(s.orders) &&
    Array.isArray(s.payments) &&
    typeof s.idempotency === 'object' &&
    s.idempotency !== null
  );
};

const loadState = (): PersistedState => {
  if (typeof window === 'undefined') return emptyState();
  try {
    const raw = window.localStorage.getItem(getCheckoutStorageKey());
    if (!raw) return emptyState();
    const parsed: unknown = JSON.parse(raw);
    return isValidState(parsed) ? parsed : emptyState();
  } catch {
    return emptyState();
  }
};

const saveState = (state: PersistedState) => {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(getCheckoutStorageKey(), JSON.stringify(state));
  } catch {
    // 忽略配额超限或隐私模式存储异常
  }
};

const wait = <T>(value: T, ms = 250): Promise<T> =>
  new Promise((resolve) => globalThis.setTimeout(() => resolve(value), ms));

const nowText = () => dayjs().toISOString();

const createId = (prefix: string) =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

export function createMockCheckoutApi({
  seedOrders,
  courses,
}: CreateMockCheckoutApiOptions) {
  const state = loadState();

  const allOrders = () => [...state.orders, ...seedOrders];

  const normalizeExpiry = (order: Order) => {
    if (
      order.status === 'PENDING_PAYMENT' &&
      dayjs(order.expiresAt).isBefore(dayjs())
    ) {
      order.status = 'CLOSED';
      saveState(state);
    }
    return order;
  };

  state.orders
    .filter((order) => order.status === 'PAID')
    .forEach((order) => {
      if (order.courseId) courses.grantCourseAccess(order.courseId);
    });

  const settleActivePayment = async (payment: PersistedPayment) => {
    if (payment.status !== 'ACTIVE') return payment;

    const elapsed = dayjs().diff(
      dayjs(payment.providerCreatedAt),
      'millisecond',
    );
    if (elapsed < CONFIRM_DELAY_MS) return payment;

    payment.updatedAt = nowText();
    const order = state.orders.find((item) => item.id === payment.orderId);

    if (
      payment.mockOutcome === 'SUCCESS' &&
      (!order || normalizeExpiry(order).status !== 'PENDING_PAYMENT')
    ) {
      payment.status = 'EXPIRED';
      payment.failureCode = order ? 'ORDER_EXPIRED' : 'ORDER_NOT_FOUND';
      saveState(state);
      return payment;
    }

    payment.status = payment.mockOutcome;
    if (payment.mockOutcome === 'FAILED') {
      payment.failureCode = 'MOCK_CHANNEL_REJECTED';
    }
    if (order && payment.mockOutcome === 'SUCCESS') {
      order.status = 'PAID';
      order.paymentMethod = payment.channel;
      order.paidAt = payment.updatedAt;
      if (order.courseId) courses.grantCourseAccess(order.courseId);
    }

    saveState(state);
    return payment;
  };

  const orderApi = {
    getAll: async (userId?: string) => {
      const orders = allOrders()
        .map(normalizeExpiry)
        .filter((order) => !userId || !order.userId || order.userId === userId);
      return wait(orders);
    },

    getById: async (orderId: string, userId?: string) => {
      const found = allOrders().find(
        (order) =>
          order.id === orderId &&
          (!userId || !order.userId || order.userId === userId),
      );
      return wait(found ? normalizeExpiry(found) : undefined);
    },

    getPayableByCourse: async (courseId: string, userId?: string) => {
      const found = allOrders()
        .map(normalizeExpiry)
        .find(
          (order) =>
            order.courseId === courseId &&
            (!userId || !order.userId || order.userId === userId) &&
            (order.status === 'PAID' || order.status === 'PENDING_PAYMENT'),
        );
      return wait(found);
    },

    create: async (courseId: string, idempotencyKey: string, userId?: string) => {
      const course = await courses.getCourse(courseId);
      if (!course) throw new Error('COURSE_NOT_FOUND');
      if (course.price <= 0) {
        throw new Error('FREE_COURSE_REQUIRES_ENROLLMENT');
      }

      const existingId = state.idempotency[idempotencyKey];
      const existing = existingId
        ? state.orders.find((order) => order.id === existingId)
        : undefined;
      if (
        existing &&
        (!userId || !existing.userId || existing.userId === userId) &&
        (normalizeExpiry(existing).status === 'PAID' ||
          existing.status === 'PENDING_PAYMENT')
      ) {
        return wait(existing);
      }
      if (existingId) delete state.idempotency[idempotencyKey];

      const payable = allOrders()
        .map(normalizeExpiry)
        .find(
          (order) =>
            order.courseId === courseId &&
            (!userId || !order.userId || order.userId === userId) &&
            (order.status === 'PAID' || order.status === 'PENDING_PAYMENT'),
        );
      if (payable) return wait(payable);

      const createdAt = nowText();
      const order: Order = {
        id: createId('order'),
        orderNo: `EC${dayjs().format('YYYYMMDDHHmmssSSS')}${String(
          state.orders.length + 1,
        ).padStart(3, '0')}`,
        userId: userId || 'student001',
        courseId: course.id,
        courseTitle: course.title,
        courseCover: course.cover,
        originalAmount: course.price,
        payableAmount: course.price,
        currency: 'CNY',
        status: 'PENDING_PAYMENT',
        createdAt,
        expiresAt: dayjs().add(30, 'minute').toISOString(),
      };

      state.orders.unshift(order);
      state.idempotency[idempotencyKey] = order.id;
      saveState(state);
      return wait(order);
    },

    cancel: async (orderId: string, userId?: string) => {
      const order = state.orders.find(
        (item) =>
          item.id === orderId &&
          (!userId || !item.userId || item.userId === userId),
      );
      if (!order) throw new Error('ORDER_NOT_FOUND');
      if (normalizeExpiry(order).status !== 'PENDING_PAYMENT') {
        throw new Error('ORDER_STATUS_CONFLICT');
      }
      order.status = 'CANCELLED';
      saveState(state);
      return wait(order);
    },
  };

  const paymentApi = {
    create: async (orderId: string, channel: PaymentMethod) => {
      const order = state.orders.find((item) => item.id === orderId);
      if (!order) throw new Error('ORDER_NOT_FOUND');
      if (normalizeExpiry(order).status !== 'PENDING_PAYMENT') {
        throw new Error('ORDER_NOT_PAYABLE');
      }

      const active = state.payments.find(
        (payment) =>
          payment.orderId === orderId && payment.status === 'ACTIVE',
      );
      if (active) return wait(active);

      const configuredOutcome =
        typeof window === 'undefined'
          ? null
          : window.sessionStorage.getItem(OUTCOME_KEY);
      const mockOutcome: MockPaymentOutcome =
        configuredOutcome === 'FAILED' || configuredOutcome === 'CANCELLED'
          ? configuredOutcome
          : 'SUCCESS';
      const createdAt = nowText();
      const payment: PersistedPayment = {
        paymentId: createId('payment'),
        attemptId: createId('attempt'),
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
      const payment = state.payments.find(
        (item) => item.orderId === orderId,
      );
      if (!payment) return wait(undefined);
      const settled = await settleActivePayment(payment);
      return wait(settled);
    },
  };

  return { orderApi, paymentApi };
}