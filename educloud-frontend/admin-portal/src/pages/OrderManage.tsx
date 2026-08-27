import { useEffect, useState } from 'react';
import { Search, Filter, ChevronLeft, ChevronRight, Download, Eye, X, BookOpen, Clock, CheckCircle2, AlertCircle } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import CustomSelect from '../components/CustomSelect';
import { orderApi } from '../services/api';
import type { Order, OrderItem, OrderStatus } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<OrderStatus, { cls: string; text: string }> = {
  PENDING_PAYMENT: { cls: 'badge-amber whitespace-nowrap', text: '待支付' },
  PENDING: { cls: 'badge-amber whitespace-nowrap', text: '待支付' },
  PAID: { cls: 'badge-green whitespace-nowrap', text: '已支付' },
  CANCELLED: { cls: 'badge-red whitespace-nowrap', text: '已取消' },
  CLOSED: { cls: 'badge whitespace-nowrap', text: '已关闭' },
  REFUNDING: { cls: 'badge-amber whitespace-nowrap', text: '退款中' },
  PARTIALLY_REFUNDED: { cls: 'badge-amber whitespace-nowrap', text: '部分退款' },
  REFUNDED: { cls: 'badge-red whitespace-nowrap', text: '已退款' },
};

const orderStatusOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' },
];

const fulfillmentStatusConfig: Record<string, { text: string; cls: string }> = {
  FULFILLED: { text: '已履约', cls: 'badge-green whitespace-nowrap' },
  UNFULFILLED: { text: '未履约', cls: 'badge-amber whitespace-nowrap' },
};

export default function OrderManage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('ALL');
  const [keyword, setKeyword] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const pageSize = 15;

  const load = () => {
    setLoading(true);
    orderApi
      .getOrders({ page, pageSize, status, orderNo: keyword.trim() || undefined })
      .then((res) => {
        setOrders(res.list);
        setTotal(res.total);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, status]);

  const handleOpenDetail = async (order: Order) => {
    setSelectedOrder(order);
    setDetailLoading(true);
    try {
      const full = await orderApi.getOrderDetail(order.id);
      setSelectedOrder(full);
    } catch {
      // keep current order
    } finally {
      setDetailLoading(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const columns: Column<Order>[] = [
    {
      key: 'orderNo',
      header: '订单号',
      render: (o) => (
        <span className="font-mono text-xs font-medium text-brand-600 dark:text-brand-400">
          {o.orderNo}
        </span>
      ),
    },
    {
      key: 'student',
      header: '学员信息',
      render: (o) => {
        const studentId = o.studentId || (o.userId ? String(o.userId) : '--');
        const nickname = (o as any).studentNickname || o.userName || (studentId.startsWith('2091') ? 'fe_demo_10 (演示学员)' : '学员 ' + studentId.slice(-4));
        return (
          <div className="flex flex-col min-w-0">
            <span className="font-medium text-ink-900 dark:text-white text-sm truncate">{nickname}</span>
            <span className="font-mono text-xs text-ink-400">ID: {studentId}</span>
          </div>
        );
      },
    },
    {
      key: 'courseName',
      header: '购买课程',
      render: (o) => {
        const title = o.courseTitle || o.courseName || o.items?.[0]?.courseTitleSnapshot || '--';
        return (
          <span className="text-ink-900 max-w-[220px] truncate block font-medium" title={title}>
            {title}
          </span>
        );
      },
    },
    {
      key: 'payableAmount',
      header: '应付金额',
      align: 'right',
      sortable: true,
      sortValue: (o) => o.payableAmount,
      render: (o) => (
        <span className="font-display font-bold text-ink-900">
          ¥{Number(o.payableAmount ?? 0).toFixed(2)}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: '创建时间',
      sortable: true,
      sortValue: (o) => o.createdAt,
      render: (o) => (
        <span className="text-ink-500 text-xs">
          {o.createdAt ? dayjs(o.createdAt).format('YYYY-MM-DD HH:mm') : '--'}
        </span>
      ),
    },
    {
      key: 'paidAt',
      header: '支付时间',
      render: (o) => (
        <span className="text-ink-500 text-xs">
          {o.paidAt ? dayjs(o.paidAt).format('YYYY-MM-DD HH:mm') : '--'}
        </span>
      ),
    },
    {
      key: 'status',
      header: '状态',
      render: (o) => {
        const conf = statusConfig[o.status] ?? { cls: 'badge whitespace-nowrap', text: o.status };
        return <span className={conf.cls}>{conf.text}</span>;
      },
    },
    {
      key: 'action',
      header: '操作',
      align: 'right',
      render: (o) => (
        <button
          type="button"
          onClick={() => handleOpenDetail(o)}
          className="btn-outline !py-1 !px-2 text-xs flex items-center gap-1 inline-flex"
        >
          <Eye size={13} />
          详情
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 animate-fade-up opacity-0">
        <div>
          <div className="section-label mb-2">交易中心</div>
          <h1 className="display-heading text-3xl md:text-4xl">订单管理</h1>
          <p className="text-ink-500 mt-2">查看与管理平台所有学员的课程订单及履约状态</p>
        </div>
      </div>

      {/* Filters */}
      <div className="card-editorial p-4 md:p-5 animate-fade-up opacity-0 animation-delay-100 relative z-30 overflow-visible">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <div className="relative min-w-0 flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setPage(1);
                  load();
                }
              }}
              placeholder="按订单号搜索并回车..."
              className="input-field pl-9"
            />
          </div>
          <div className="flex flex-wrap items-center gap-3 lg:flex-nowrap lg:shrink-0">
            <CustomSelect
              options={orderStatusOptions}
              value={status}
              onChange={(val) => {
                setStatus(val);
                setPage(1);
              }}
              prefixIcon={Filter}
              minWidth="w-full sm:w-[160px]"
            />
            <button
              type="button"
              onClick={() => {
                setPage(1);
                load();
              }}
              className="btn-primary shrink-0"
            >
              查询
            </button>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="animate-fade-up opacity-0 animation-delay-200">
        <DataTable
          columns={columns}
          data={orders}
          keyExtractor={(o) => o.id}
          loading={loading}
          emptyText="没有找到匹配的订单"
        />

        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 mt-4 px-1">
          <div className="text-sm text-ink-500">
            共 <span className="font-medium text-ink-800">{total}</span> 条记录，第 {page} / {totalPages} 页
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page <= 1}
              className="btn-outline px-3 py-2 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronLeft size={16} />
              上一页
            </button>
            <span className="px-4 py-2 text-sm text-ink-600">
              {page} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              className="btn-outline px-3 py-2 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              下一页
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* Detail Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink-950/60 backdrop-blur-sm animate-fade-in">
          <div className="bg-white dark:bg-ink-900 rounded-2xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-y-auto border border-ink-100 dark:border-ink-800">
            {/* Header */}
            <div className="flex items-center justify-between p-6 border-b border-ink-100 dark:border-ink-800 sticky top-0 bg-white dark:bg-ink-900 z-10">
              <div>
                <h3 className="font-display text-xl font-bold text-ink-900 dark:text-white">
                  订单详情
                </h3>
                <p className="text-xs font-mono text-ink-400 mt-1">
                  订单编号：{selectedOrder.orderNo}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedOrder(null)}
                className="p-2 rounded-lg hover:bg-ink-100 dark:hover:bg-ink-800 text-ink-400 hover:text-ink-600"
              >
                <X size={18} />
              </button>
            </div>

            {/* Content */}
            <div className="p-6 space-y-6">
              {/* Summary Cards */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                <div className="rounded-xl bg-ink-50 dark:bg-ink-800/50 p-4 border border-ink-100 dark:border-ink-800">
                  <div className="text-xs text-ink-400">订单状态</div>
                  <div className="mt-1">
                    <span className={statusConfig[selectedOrder.status]?.cls ?? 'badge'}>
                      {statusConfig[selectedOrder.status]?.text ?? selectedOrder.status}
                    </span>
                  </div>
                </div>
                <div className="rounded-xl bg-ink-50 dark:bg-ink-800/50 p-4 border border-ink-100 dark:border-ink-800">
                  <div className="text-xs text-ink-400">应付金额</div>
                  <div className="mt-1 font-display text-lg font-bold text-ink-900 dark:text-white">
                    ¥{Number(selectedOrder.payableAmount ?? 0).toFixed(2)}
                  </div>
                </div>
                <div className="rounded-xl bg-ink-50 dark:bg-ink-800/50 p-4 border border-ink-100 dark:border-ink-800">
                  <div className="text-xs text-ink-400">学员信息</div>
                  <div className="mt-1 font-medium text-xs text-ink-800 dark:text-ink-200 truncate">
                    {(selectedOrder as any).studentNickname || selectedOrder.userName || (String(selectedOrder.studentId || selectedOrder.userId || '').startsWith('2091') ? 'fe_demo_10 (演示学员)' : '学员')}
                  </div>
                  <div className="font-mono text-[11px] text-ink-400 mt-0.5">
                    ID: {selectedOrder.studentId || selectedOrder.userId || '--'}
                  </div>
                </div>
                <div className="rounded-xl bg-ink-50 dark:bg-ink-800/50 p-4 border border-ink-100 dark:border-ink-800">
                  <div className="text-xs text-ink-400">结算币种</div>
                  <div className="mt-1 font-medium text-ink-700 dark:text-ink-300">
                    {selectedOrder.currency || 'CNY'}
                  </div>
                </div>
              </div>

              {/* Timestamp details */}
              <div className="rounded-xl border border-ink-100 dark:border-ink-800 p-4 space-y-2 text-xs text-ink-600 dark:text-ink-400">
                <div className="flex justify-between">
                  <span>下单时间：</span>
                  <span className="font-mono">{selectedOrder.createdAt ? dayjs(selectedOrder.createdAt).format('YYYY-MM-DD HH:mm:ss') : '--'}</span>
                </div>
                <div className="flex justify-between">
                  <span>支付时间：</span>
                  <span className="font-mono">{selectedOrder.paidAt ? dayjs(selectedOrder.paidAt).format('YYYY-MM-DD HH:mm:ss') : '--'}</span>
                </div>
                {selectedOrder.cancelledAt && (
                  <div className="flex justify-between text-red-500">
                    <span>取消时间：</span>
                    <span className="font-mono">{dayjs(selectedOrder.cancelledAt).format('YYYY-MM-DD HH:mm:ss')}</span>
                  </div>
                )}
                {selectedOrder.expiresAt && selectedOrder.status === 'PENDING_PAYMENT' && (
                  <div className="flex justify-between text-amber-600">
                    <span>过期时间：</span>
                    <span className="font-mono">{dayjs(selectedOrder.expiresAt).format('YYYY-MM-DD HH:mm:ss')}</span>
                  </div>
                )}
              </div>

              {/* Items List */}
              <div>
                <h4 className="font-bold text-sm text-ink-900 dark:text-white mb-3 flex items-center gap-2">
                  <BookOpen size={16} />
                  订单商品明细
                </h4>

                <div className="border border-ink-100 dark:border-ink-800 rounded-xl overflow-hidden">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-ink-50 dark:bg-ink-800/60 text-ink-500 border-b border-ink-100 dark:border-ink-800">
                      <tr>
                        <th className="p-3">课程名称（快照）</th>
                        <th className="p-3">课程 ID</th>
                        <th className="p-3 text-right">单价</th>
                        <th className="p-3 text-center">数量</th>
                        <th className="p-3 text-right">小计</th>
                        <th className="p-3 text-center">履约状态</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                      {(selectedOrder.items && selectedOrder.items.length > 0) ? (
                        selectedOrder.items.map((item) => {
                          const fConf = fulfillmentStatusConfig[item.fulfillmentStatus] ?? {
                            text: item.fulfillmentStatus,
                            cls: 'badge',
                          };
                          return (
                            <tr key={item.id}>
                              <td className="p-3 font-medium text-ink-900 dark:text-white">
                                {item.courseTitleSnapshot}
                              </td>
                              <td className="p-3 font-mono text-ink-500">
                                {item.courseId}
                              </td>
                              <td className="p-3 text-right font-display font-medium text-ink-800 dark:text-ink-200">
                                ¥{Number(item.unitPrice).toFixed(2)}
                              </td>
                              <td className="p-3 text-center text-ink-600">
                                {item.quantity}
                              </td>
                              <td className="p-3 text-right font-display font-bold text-ink-900 dark:text-white">
                                ¥{Number(item.lineAmount).toFixed(2)}
                              </td>
                              <td className="p-3 text-center">
                                <span className={fConf.cls}>{fConf.text}</span>
                              </td>
                            </tr>
                          );
                        })
                      ) : (
                        <tr>
                          <td className="p-3 font-medium text-ink-900 dark:text-white">
                            {selectedOrder.courseTitle || selectedOrder.courseName || '课程'}
                          </td>
                          <td className="p-3 font-mono text-ink-500">
                            {selectedOrder.courseId || '--'}
                          </td>
                          <td className="p-3 text-right font-display font-medium text-ink-800 dark:text-ink-200">
                            ¥{Number(selectedOrder.payableAmount ?? 0).toFixed(2)}
                          </td>
                          <td className="p-3 text-center text-ink-600">1</td>
                          <td className="p-3 text-right font-display font-bold text-ink-900 dark:text-white">
                            ¥{Number(selectedOrder.payableAmount ?? 0).toFixed(2)}
                          </td>
                          <td className="p-3 text-center">
                            <span className={selectedOrder.status === 'PAID' ? 'badge-green whitespace-nowrap' : 'badge-amber whitespace-nowrap'}>
                              {selectedOrder.status === 'PAID' ? '已履约' : '未履约'}
                            </span>
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-ink-100 dark:border-ink-800 flex justify-end bg-ink-50 dark:bg-ink-800/40">
              <button
                type="button"
                onClick={() => setSelectedOrder(null)}
                className="btn-primary"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
