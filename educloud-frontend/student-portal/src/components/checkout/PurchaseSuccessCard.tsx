import { CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Order } from '@/types';

export default function PurchaseSuccessCard({
  order,
  paymentMethod,
}: {
  order: Order;
  paymentMethod?: string;
}) {
  return (
    <section className="rounded-3xl border border-white/70 bg-white/85 p-8 text-center shadow-2xl shadow-indigo-950/10 backdrop-blur-xl">
      <CheckCircle2
        className="mx-auto h-16 w-16 text-green-600"
        strokeWidth={1.5}
      />
      <h1 className="mt-5 font-display text-4xl font-bold text-ink-900">
        课程购买成功
      </h1>
      <p className="mt-3 text-ink-500">
        课程已加入“我的课程”，学习权限已经开通。
      </p>

      <dl className="mx-auto mt-8 grid max-w-xl gap-3 rounded-2xl bg-paper p-5 text-left text-sm sm:grid-cols-2">
        <div>
          <dt className="text-ink-400">课程名称</dt>
          <dd className="mt-1 font-medium text-ink-800">{order.courseTitle}</dd>
        </div>
        <div>
          <dt className="text-ink-400">订单编号</dt>
          <dd className="mt-1 font-medium text-ink-800">{order.orderNo}</dd>
        </div>
        <div>
          <dt className="text-ink-400">实付金额</dt>
          <dd className="mt-1 font-medium text-ink-800">
            ¥{order.payableAmount}
          </dd>
        </div>
        <div>
          <dt className="text-ink-400">支付方式</dt>
          <dd className="mt-1 font-medium text-ink-800">
            {paymentMethod === 'WECHAT' ? '微信支付'
              : paymentMethod === 'ALIPAY' ? '支付宝'
              : paymentMethod === 'MOCK' ? 'Mock 沙箱'
              : order.paymentMethod === 'WECHAT' ? '微信支付'
              : order.paymentMethod === 'ALIPAY' ? '支付宝'
              : '在线支付'}
          </dd>
        </div>
      </dl>

      <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
        <Link to={`/learn/${order.courseId}`} className="btn-primary">
          开始学习
        </Link>
        <Link to="/orders" className="btn-outline">
          查看订单
        </Link>
        <Link
          to={`/courses/${order.courseId}`}
          className="px-5 py-3 text-sm text-ink-500 hover:text-indigo-800"
        >
          返回课程详情
        </Link>
      </div>
    </section>
  );
}
