import { useEffect, useState } from 'react';
import { Search, Filter, ChevronLeft, ChevronRight, Download } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import { orderApi } from '../services/api';
import type { Order, OrderStatus } from '../types';
import { cn } from '../utils/cn';

const statusConfig: Record<OrderStatus, { cls: string; text: string }> = {
  PAID: { cls: 'badge-green', text: '已支付' },
  PENDING: { cls: 'badge-amber', text: '待支付' },
  REFUNDED: { cls: 'badge-red', text: '已退款' },
  CANCELLED: { cls: 'badge', text: '已取消' },
};

const paymentLabel: Record<Order['paymentMethod'], string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
};

export default function OrderManage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('ALL');
  const [keyword, setKeyword] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const pageSize = 15;

  const load = () => {
    setLoading(true);
    orderApi
      .getOrders({ page, pageSize, status, startDate, endDate })
      .then((res) => {
        let list = res.list;
        if (keyword) {
          const kw = keyword.toLowerCase();
          list = list.filter(
            (o) =>
              o.orderNo.toLowerCase().includes(kw) ||
              o.userName.toLowerCase().includes(kw) ||
              o.courseName.toLowerCase().includes(kw),
          );
        }
        setOrders(list);
        setTotal(res.total);
        setLoading(false);
      });
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, status, startDate, endDate]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const columns: Column<Order>[] = [
    {
      key: 'orderNo',
      header: '订单号',
      render: (o) => <span className="font-mono text-xs text-brand-500 dark:text-brand-400">{o.orderNo}</span>,
    },
    {
      key: 'user',
      header: '用户',
      render: (o) => (
        <div>
          <div className="font-medium text-ink-900">{o.userName}</div>
          <div className="text-xs text-ink-400">{o.userEmail}</div>
        </div>
      ),
    },
    {
      key: 'courseName',
      header: '课程',
      render: (o) => <span className="text-ink-600 max-w-[220px] truncate block">{o.courseName}</span>,
    },
    {
      key: 'amount',
      header: '金额',
      align: 'right',
      sortable: true,
      sortValue: (o) => o.amount,
      render: (o) => (
        <span className="font-display font-bold text-ink-900">¥{o.amount.toFixed(2)}</span>
      ),
    },
    {
      key: 'paymentMethod',
      header: '支付方式',
      render: (o) => (
        <span className={cn('badge', o.paymentMethod === 'ALIPAY' ? 'bg-blue-500/15 text-blue-600 dark:text-blue-400 border border-blue-500/20' : 'bg-green-500/15 text-green-600 dark:text-green-400 border border-green-500/20')}>
          {paymentLabel[o.paymentMethod]}
        </span>
      ),
    },
    {
      key: 'status',
      header: '状态',
      render: (o) => <span className={statusConfig[o.status].cls}>{statusConfig[o.status].text}</span>,
    },
    {
      key: 'createdAt',
      header: '创建时间',
      sortable: true,
      sortValue: (o) => o.createdAt,
      render: (o) => <span className="text-ink-500 text-xs">{o.createdAt}</span>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 animate-fade-up opacity-0">
        <div>
          <div className="section-label mb-2">交易中心</div>
          <h1 className="display-heading text-3xl md:text-4xl">订单管理</h1>
          <p className="text-ink-500 mt-2">查看与管理平台所有订单交易记录</p>
        </div>
        <button className="btn-outline self-start">
          <Download size={15} />
          导出订单
        </button>
      </div>

      {/* Filters */}
      <div className="card-editorial p-4 md:p-5 animate-fade-up opacity-0 animation-delay-100">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <div className="relative min-w-0 flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
              placeholder="搜索订单号、用户名或课程名..."
              className="input-field pl-9"
            />
          </div>
          <div className="flex flex-wrap items-center gap-3 lg:flex-nowrap lg:shrink-0">
            <input
              type="date"
              value={startDate}
              onChange={(e) => { setStartDate(e.target.value); setPage(1); }}
              className="input-field h-[46px] w-full shrink-0 sm:w-[180px] lg:w-[180px]"
            />
            <span className="shrink-0 text-ink-400">至</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => { setEndDate(e.target.value); setPage(1); }}
              className="input-field h-[46px] w-full shrink-0 sm:w-[180px] lg:w-[180px]"
            />
            <div className="relative w-full shrink-0 lg:w-[130px]">
              <Filter size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400 pointer-events-none" />
              <select
                value={status}
                onChange={(e) => { setStatus(e.target.value); setPage(1); }}
                className="input-field w-full pl-9 pr-8 appearance-none cursor-pointer"
              >
                <option value="ALL">全部状态</option>
                <option value="PAID">已支付</option>
                <option value="PENDING">待支付</option>
                <option value="REFUNDED">已退款</option>
                <option value="CANCELLED">已取消</option>
              </select>
            </div>
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
    </div>
  );
}
