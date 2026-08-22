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
    return raw ? (JSON.parse(raw) as PersistedState) : emptyState();
  } catch {
    return emptyState();
  }
};

const saveState = (state: PersistedState) => {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
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
    .forEach((order) => courses.grantCourseAccess(order.courseId));

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
        .find(
          (order) =>
            order.courseId === courseId &&
            (order.status === 'PAID' ||
              order.status === 'PENDING_PAYMENT'),
        );
      return wait(found);
    },

    create: async (courseId: number, idempotencyKey: string) => {
      const course = courses.getCourse(courseId);
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
            (order.status === 'PAID' ||
              order.status === 'PENDING_PAYMENT'),
        );
      if (payable) return wait(payable);

      const createdAt = nowText();
      const order: Order = {
        id: createId('order'),
        orderNo: `EC${dayjs().format('YYYYMMDDHHmmssSSS')}${String(
          state.orders.length + 1,
        ).padStart(3, '0')}`,
        courseId: course.id,
        courseTitle: course.title,
        courseCover: course.cover,
        originalAmount: course.originalPrice ?? course.price,
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

    cancel: async (orderId: string) => {
      const order = state.orders.find((item) => item.id === orderId);
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
