import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Receipt, Clock, CheckCircle, XCircle } from 'lucide-react';
import { orderApi } from '@/services/api';
import type { Order, OrderStatus } from '@/types';
import { cn } from '@/utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<OrderStatus, { label: string; className: string; icon: typeof Clock }> = {
  PAID: { label: '已支付', className: 'badge-green', icon: CheckCircle },
  PENDING: { label: '待支付', className: 'badge-amber', icon: Clock },
  REFUNDED: { label: '已退款', className: 'badge-red', icon: XCircle },
  CANCELLED: { label: '已取消', className: 'badge-red', icon: XCircle },
};

export default function Orders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    orderApi.getAll().then((data) => {
      setOrders(data);
      setLoading(false);
    });
  }, []);

  const totalSpent = orders
    .filter((o) => o.status === 'PAID')
    .reduce((sum, o) => sum + o.amount, 0);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <span className="section-label mb-3">订单管理</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">我的订单</h1>
        <p className="text-ink-500 mt-3">查看购买记录与订单状态</p>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="stat-card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-ink-500">订单总数</p>
              <p className="font-display text-3xl font-bold text-ink-900 mt-1">{orders.length}</p>
            </div>
            <Receipt size={32} className="text-indigo-800/20" strokeWidth={1} />
          </div>
        </div>
        <div className="stat-card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-ink-500">已支付</p>
              <p className="font-display text-3xl font-bold text-green-600 mt-1">
                {orders.filter((o) => o.status === 'PAID').length}
              </p>
            </div>
            <CheckCircle size={32} className="text-green-600/20" strokeWidth={1} />
          </div>
        </div>
        <div className="stat-card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-ink-500">累计消费</p>
              <p className="font-display text-3xl font-bold text-amber-600 mt-1">¥{totalSpent}</p>
            </div>
            <Receipt size={32} className="text-amber-600/20" strokeWidth={1} />
          </div>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
        </div>
      ) : (
        <div className="card-editorial overflow-hidden">
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>订单编号</th>
                  <th>课程名称</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>下单时间</th>
                  <th className="text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => {
                  const config = statusConfig[order.status];
                  const StatusIcon = config.icon;
                  return (
                    <tr key={order.id}>
                      <td>
                        <span className="font-mono text-xs text-ink-500">{order.orderNo}</span>
                      </td>
                      <td>
                        <Link
                          to={`/courses/${order.courseId}`}
                          className="font-medium text-ink-900 hover:text-indigo-800 transition-colors"
                        >
                          {order.courseTitle}
                        </Link>
                      </td>
                      <td>
                        <span className="font-display font-bold text-ink-900">¥{order.amount}</span>
                      </td>
                      <td>
                        <span className={cn(config.className)}>
                          <StatusIcon size={12} />
                          {config.label}
                        </span>
                      </td>
                      <td>
                        <span className="text-sm text-ink-500">
                          {dayjs(order.createdAt).format('YYYY-MM-DD HH:mm')}
                        </span>
                      </td>
                      <td className="text-right">
                        {order.status === 'PENDING' ? (
                          <button type="button" className="btn-primary !px-4 !py-2 text-xs">
                            去支付
                          </button>
                        ) : order.status === 'PAID' ? (
                          <Link
                            to={`/learn/${order.courseId}`}
                            className="text-sm text-indigo-800 link-underline"
                          >
                            开始学习
                          </Link>
                        ) : (
                          <span className="text-sm text-ink-300">--</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {orders.length === 0 && (
            <div className="text-center py-16">
              <Receipt size={40} className="mx-auto text-ink-200 mb-3" strokeWidth={1} />
              <p className="text-ink-400">暂无订单记录</p>
              <Link to="/courses" className="btn-primary mt-4">去选课</Link>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
