import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Receipt, Clock, CheckCircle, XCircle, AlertCircle } from 'lucide-react';
import { orderApi } from '@/services/api';
import type { Order, OrderStatus } from '@/types';
import { cn } from '@/utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<OrderStatus, { label: string; className: string; icon: typeof Clock }> = {
  PENDING_PAYMENT: { label: '待支付', className: 'badge-amber', icon: Clock },
  PAID: { label: '已支付', className: 'badge-green', icon: CheckCircle },
  CANCELLED: { label: '已取消', className: 'badge-red', icon: XCircle },
  CLOSED: { label: '已关闭', className: 'badge-red', icon: XCircle },
  REFUNDING: { label: '退款中', className: 'badge-amber', icon: Clock },
  PARTIALLY_REFUNDED: { label: '部分退款', className: 'badge-amber', icon: Clock },
  REFUNDED: { label: '已退款', className: 'badge-red', icon: XCircle },
};

type FilterStatus = 'ALL' | 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED';

export default function Orders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<FilterStatus>('ALL');
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const fetchOrders = async (currentFilter = filter) => {
    setLoading(true);
    try {
      const statusParam = currentFilter === 'ALL' ? undefined : currentFilter;
      const data = await orderApi.getAll({ status: statusParam });
      setOrders(data);
    } catch {
      setOrders([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(filter);
  }, [filter]);

  const handleCancelOrder = async (orderId: string) => {
    if (!confirm('确定要取消该订单吗？')) return;
    setActionLoadingId(orderId);
    try {
      await orderApi.cancelOrder(orderId);
      setMessage('订单已成功取消');
      await fetchOrders();
    } catch (err: any) {
      alert(err?.message || '取消订单失败');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleMockPay = async (orderId: string) => {
    setActionLoadingId(orderId);
    try {
      await orderApi.mockPayOrder(orderId);
      setMessage('支付成功，已开通课程学习权限！');
      await fetchOrders();
    } catch (err: any) {
      alert(err?.message || '模拟支付失败');
    } finally {
      setActionLoadingId(null);
    }
  };

  const totalSpent = orders
    .filter((o) => o.status === 'PAID')
    .reduce((sum, o) => sum + o.payableAmount, 0);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <span className="section-label mb-3">订单管理</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">我的订单</h1>
        <p className="text-ink-500 mt-3">查看购买记录与订单状态</p>
      </div>

      {message && (
        <div className="mb-6 flex items-center justify-between rounded-xl bg-green-50 p-4 text-sm text-green-700 border border-green-200">
          <span>{message}</span>
          <button type="button" onClick={() => setMessage(null)} className="text-xs font-semibold underline">
            关闭
          </button>
        </div>
      )}

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

      {/* Tabs */}
      <div className="flex items-center gap-2 mb-6 border-b border-ink-100 pb-3">
        {(['ALL', 'PENDING_PAYMENT', 'PAID', 'CANCELLED'] as const).map((tab) => {
          const labels: Record<FilterStatus, string> = {
            ALL: '全部',
            PENDING_PAYMENT: '待支付',
            PAID: '已完成',
            CANCELLED: '已取消',
          };
          const active = filter === tab;
          return (
            <button
              key={tab}
              type="button"
              onClick={() => setFilter(tab)}
              className={cn(
                'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                active ? 'bg-indigo-800 text-white' : 'text-ink-600 hover:bg-ink-100',
              )}
            >
              {labels[tab]}
            </button>
          );
        })}
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
                  const config = statusConfig[order.status] ?? statusConfig.PENDING_PAYMENT;
                  const StatusIcon = config.icon;
                  const isActionLoading = actionLoadingId === order.id;

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
                        <span className="font-display font-bold text-ink-900">¥{order.payableAmount}</span>
                      </td>
                      <td>
                        <span className={cn(config.className)}>
                          <StatusIcon size={12} />
                          {config.label}
                        </span>
                      </td>
                      <td>
                        <span className="text-sm text-ink-500">
                          {order.createdAt ? dayjs(order.createdAt).format('YYYY-MM-DD HH:mm') : '--'}
                        </span>
                      </td>
                      <td className="text-right">
                        <div className="flex items-center justify-end gap-2">
                          {order.status === 'PENDING_PAYMENT' ? (
                            <>
                              <button
                                type="button"
                                disabled={isActionLoading}
                                onClick={() => handleMockPay(order.id)}
                                className="btn-primary !px-3 !py-1.5 text-xs"
                              >
                                {isActionLoading ? '处理中…' : '立即支付'}
                              </button>
                              <button
                                type="button"
                                disabled={isActionLoading}
                                onClick={() => handleCancelOrder(order.id)}
                                className="text-xs text-ink-400 hover:text-red-600 px-2 py-1"
                              >
                                取消
                              </button>
                            </>
                          ) : order.status === 'PAID' ? (
                            <Link
                              to={`/learn/${order.courseId}`}
                              className="text-sm text-indigo-800 link-underline font-medium"
                            >
                              开始学习
                            </Link>
                          ) : (
                            <span className="text-sm text-ink-300">--</span>
                          )}
                        </div>
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
              <Link to="/courses" className="btn-primary mt-4 inline-block">去选课</Link>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
