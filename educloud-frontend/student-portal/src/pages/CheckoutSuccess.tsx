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
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!orderId) {
      setMessage('订单参数无效');
      setLoading(false);
      return;
    }

    let cancelled = false;
    let retryTimer: ReturnType<typeof globalThis.setTimeout> | undefined;

    const load = async (attempt = 0) => {
      try {
        const payment = await paymentGateway.query(orderId);
        const found = await orderApi.getById(orderId);
        if (cancelled) return;

        if (!found) {
          setConfirming(false);
          setMessage('订单不存在或无权访问');
        } else if (found.status === 'PAID') {
          setConfirming(false);
          setOrder(found);
        } else if (
          found.status === 'PENDING_PAYMENT' &&
          (payment?.status === 'ACTIVE' || payment?.status === 'SUCCESS') &&
          attempt < 25
        ) {
          setConfirming(true);
          setMessage('支付结果确认中，请不要关闭页面');
          retryTimer = globalThis.setTimeout(() => {
            void load(attempt + 1);
          }, 400);
        } else {
          setConfirming(false);
          setMessage('支付结果尚未确认，请从订单页继续支付');
        }
      } catch {
        if (!cancelled) {
          setConfirming(false);
          setMessage('订单结果查询失败，请稍后重试');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
      if (retryTimer) globalThis.clearTimeout(retryTimer);
    };
  }, [orderId]);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="animate-spin text-indigo-800" />
      </div>
    );
  }

  return (
    <main className="min-h-[calc(100vh-6rem)] bg-paper px-4 py-16">
      <div className="mx-auto max-w-4xl">
        {order ? (
          <PurchaseSuccessCard order={order} />
        ) : confirming ? (
          <div className="rounded-3xl bg-white p-8 shadow-xl shadow-indigo-950/5">
            <PaymentStatusPanel state="CONFIRMING" />
            <p className="mt-4 text-center text-ink-500">{message}</p>
          </div>
        ) : (
          <div className="rounded-3xl bg-white p-8 text-center shadow-xl shadow-indigo-950/5">
            <PaymentStatusPanel state="FAILED" />
            <p className="mt-4 text-ink-500">{message}</p>
            <Link to="/orders" className="btn-primary mt-6">
              查看我的订单
            </Link>
          </div>
        )}
      </div>
    </main>
  );
}
