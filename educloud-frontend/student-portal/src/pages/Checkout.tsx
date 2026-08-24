import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, Loader2, ShieldCheck } from 'lucide-react';
import CheckoutCourseSummary from '@/components/checkout/CheckoutCourseSummary';
import PaymentMethodSelector from '@/components/checkout/PaymentMethodSelector';
import PaymentStatusPanel from '@/components/checkout/PaymentStatusPanel';
import { courseApi, orderApi } from '@/services/api';
import { paymentGateway } from '@/services/paymentGateway';
import {
  clearCheckoutIntentKey,
  getCheckoutIntentKey,
} from '@/utils/checkoutSession';
import { useCartStore } from '@/stores/useCartStore';
import { useAuthStore } from '@/stores/useAuthStore';
import type { CourseDetail, Order, PaymentMethod } from '@/types';

type ViewState =
  | 'LOADING'
  | 'READY'
  | 'CONFIRMING'
  | 'FAILED'
  | 'CANCELLED';

export default function Checkout() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const currentUser = useAuthStore((state) => state.user);
  const removeFromCart = useCartStore((state) => state.removeFromCart);
  const [course, setCourse] = useState<CourseDetail>();
  const [order, setOrder] = useState<Order>();
  const [method, setMethod] = useState<PaymentMethod>('ALIPAY');
  const [viewState, setViewState] = useState<ViewState>('LOADING');
  const [error, setError] = useState('');
  const courseIdParam = courseId ?? '';

  const finishPaidOrder = useCallback(
    (paidOrder: Order) => {
      const cId = paidOrder.courseId ?? courseIdParam;
      if (cId) {
        removeFromCart(cId);
        clearCheckoutIntentKey(cId);
      }
      navigate(`/checkout/success/${paidOrder.id}`, { replace: true });
    },
    [courseIdParam, navigate, removeFromCart],
  );

  useEffect(() => {
    let cancelled = false;

    const loadCheckout = async () => {
      if (!courseIdParam) {
        setError('课程参数无效');
        setViewState('READY');
        return;
      }

      try {
        const [foundCourse, foundOrder] = await Promise.all([
          courseApi.getById(courseIdParam),
          orderApi.getPayableByCourse(courseIdParam, currentUser?.id),
        ]);
        if (cancelled) return;

        if (!foundCourse) {
          setError('课程不存在或已下架');
          setViewState('READY');
          return;
        }
        if (Number(foundCourse.price) === 0) {
          navigate(`/courses/${foundCourse.id}`, { replace: true });
          return;
        }
        if (foundOrder?.status === 'PAID' || foundCourse.enrolled) {
          navigate(`/learn/${foundCourse.id}`, { replace: true });
          return;
        }

        setCourse(foundCourse);
        setOrder(foundOrder);
        if (foundOrder) {
          const payment = await paymentGateway.query(foundOrder.id);
          if (cancelled) return;
          if (payment?.status === 'SUCCESS') {
            const refreshed = await orderApi.getById(foundOrder.id, currentUser?.id);
            if (refreshed?.status === 'PAID') {
              finishPaidOrder(refreshed);
              return;
            }
          }
          if (payment?.status === 'ACTIVE') {
            setViewState('CONFIRMING');
            return;
          }
          if (payment?.status === 'FAILED') {
            setViewState('FAILED');
            return;
          }
          if (payment?.status === 'CANCELLED') {
            setViewState('CANCELLED');
            return;
          }
        }
        setViewState('READY');
      } catch {
        if (!cancelled) {
          setError('加载结算信息失败，请重试');
          setViewState('READY');
        }
      }
    };

    void loadCheckout();
    return () => {
      cancelled = true;
    };
  }, [courseIdParam, currentUser?.id, finishPaidOrder, navigate]);

  useEffect(() => {
    if (viewState !== 'CONFIRMING' || !order) return;
    let attempts = 0;
    const maxAttempts = 20;
    let active = true;

    const timer = globalThis.setInterval(async () => {
      if (!active) return;
      attempts += 1;
      if (attempts > maxAttempts) {
        active = false;
        globalThis.clearInterval(timer);
        setError('支付结果确认超时，请前往订单中心查看或重试');
        setViewState('FAILED');
        return;
      }

      try {
        const payment = await paymentGateway.query(order.id);
        if (!active) return;
        if (!payment || payment.status === 'ACTIVE') return;

        if (payment.status === 'SUCCESS') {
          const refreshed = await orderApi.getById(order.id, currentUser?.id);
          if (refreshed?.status === 'PAID') finishPaidOrder(refreshed);
        } else if (payment.status === 'CANCELLED') {
          setViewState('CANCELLED');
        } else {
          setViewState('FAILED');
        }
      } catch {
        if (!active) return;
        setError('支付状态查询异常');
        setViewState('FAILED');
      }
    }, 600);

    return () => {
      active = false;
      globalThis.clearInterval(timer);
    };
  }, [currentUser?.id, finishPaidOrder, order, viewState]);

  const confirmPayment = async () => {
    if (!course || viewState === 'CONFIRMING') return;

    setError('');
    setViewState('CONFIRMING');
    try {
      const payableOrder =
        order ??
        (await orderApi.create(course.id));
      setOrder(payableOrder);

      if (payableOrder.status === 'PAID') {
        finishPaidOrder(payableOrder);
        return;
      }
      if (payableOrder.status !== 'PENDING_PAYMENT') {
        throw new Error('当前订单已不可支付，请返回课程详情重试');
      }

      const payment = await paymentGateway.initiate({
        orderId: payableOrder.id,
        channel: method,
      });
      const refreshed = await orderApi.getById(payableOrder.id, currentUser?.id);
      if (refreshed) setOrder(refreshed);

      if (payment.status === 'SUCCESS') {
        const paid = await orderApi.getById(payableOrder.id, currentUser?.id);
        if (paid?.status === 'PAID') finishPaidOrder(paid);
      } else if (payment.status === 'CANCELLED') {
        setViewState('CANCELLED');
      } else if (payment.status === 'FAILED') {
        setViewState('FAILED');
      } else if (payment.status === 'ACTIVE') {
        setViewState('CONFIRMING');
      } else {
        setViewState('FAILED');
      }
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : '支付发起失败，请重试',
      );
      setViewState('FAILED');
    }
  };

  if (viewState === 'LOADING') {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="animate-spin text-indigo-800" />
      </div>
    );
  }

  return (
    <main className="min-h-[calc(100vh-6rem)] bg-paper px-4 py-12 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <span className="section-label">确认订单</span>
        <h1 className="display-heading mt-4 text-4xl">安全支付</h1>
        <p className="mt-3 text-ink-500">
          请确认课程与支付方式，支付成功后将立即开通学习权限。
        </p>

        {error && (
          <div
            role="alert"
            className="mt-6 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-red-700"
          >
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {course ? (
          <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_22rem]">
            <CheckoutCourseSummary course={course} />
            <aside className="rounded-3xl border border-white/70 bg-white/85 p-6 shadow-xl shadow-indigo-950/5 backdrop-blur-xl">
              <PaymentMethodSelector
                value={method}
                onChange={setMethod}
                disabled={viewState === 'CONFIRMING'}
              />
              <div className="my-6 border-t border-ink-100" />
              <p className="flex gap-2 text-xs leading-6 text-ink-500">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-green-600" />
                点击确认支付即表示同意购买协议与退款规则；支付结果以订单查询为准。
              </p>

              {(viewState === 'CONFIRMING' ||
                viewState === 'FAILED' ||
                viewState === 'CANCELLED') && (
                <div className="mt-5">
                  <PaymentStatusPanel
                    state={viewState}
                    onRetry={
                      viewState === 'CONFIRMING' ? undefined : confirmPayment
                    }
                  />
                </div>
              )}

              <button
                type="button"
                disabled={viewState === 'CONFIRMING'}
                onClick={confirmPayment}
                className="btn-primary mt-6 w-full disabled:cursor-not-allowed disabled:opacity-60"
              >
                {viewState === 'CONFIRMING'
                  ? '正在确认支付结果…'
                  : Number(course.price) === 0
                    ? '确认支付 ¥0'
                    : `确认支付 ¥${course.price}`}
              </button>
              <Link
                to={`/courses/${course.id}`}
                className="mt-3 block text-center text-sm text-ink-400 hover:text-indigo-800"
              >
                返回课程详情
              </Link>
            </aside>
          </div>
        ) : (
          <div className="mt-8 rounded-3xl border border-ink-100 bg-white p-10 text-center">
            <p className="text-ink-500">无法加载该课程的结算信息。</p>
            <Link to="/courses" className="btn-primary mt-5">
              返回全部课程
            </Link>
          </div>
        )}
      </div>
    </main>
  );
}