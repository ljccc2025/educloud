import { useEffect, useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { CircleDollarSign, TrendingUp, RefreshCw, Clock, CheckCircle, XCircle, type LucideIcon } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import { financeApi } from '../services/api';
import type { FinanceStats, MonthlyRevenue, Order } from '../types';

export default function Finance() {
  const [stats, setStats] = useState<FinanceStats | null>(null);
  const [monthly, setMonthly] = useState<MonthlyRevenue[]>([]);
  const [transactions, setTransactions] = useState<Order[]>([]);

  useEffect(() => {
    void Promise.all([
      financeApi.getStats(),
      financeApi.getMonthlyRevenue(),
      financeApi.getTransactions(),
    ]).then(([s, m, t]) => {
      setStats(s);
      setMonthly(m);
      setTransactions(t);
    });
  }, []);

  const refundRequests = transactions
    .filter((o) => o.status === 'REFUNDED' || o.status === 'PENDING')
    .slice(0, 6);

  const columns: Column<Order>[] = [
    {
      key: 'orderNo',
      header: '订单号',
      render: (o) => <span className="font-mono text-xs text-indigo-800">{o.orderNo}</span>,
    },
    { key: 'userName', header: '用户', render: (o) => <span className="text-ink-700">{o.userName}</span> },
    {
      key: 'courseName',
      header: '课程',
      render: (o) => <span className="text-ink-600 max-w-[200px] truncate block">{o.courseName}</span>,
    },
    {
      key: 'amount',
      header: '金额',
      align: 'right',
      render: (o) => <span className="font-display font-700 text-ink-900">¥{o.amount.toFixed(2)}</span>,
    },
    {
      key: 'status',
      header: '状态',
      render: (o) => (
        <span className={o.status === 'PAID' ? 'badge-green' : o.status === 'REFUNDED' ? 'badge-red' : 'badge-amber'}>
          {o.status === 'PAID' ? '已支付' : o.status === 'REFUNDED' ? '已退款' : '待支付'}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: '时间',
      render: (o) => <span className="text-ink-500 text-xs">{o.createdAt}</span>,
    },
  ];

  return (
    <div className="space-y-8">
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">财务中心</div>
        <h1 className="display-heading text-3xl md:text-4xl">财务管理</h1>
        <p className="text-ink-500 mt-2">收入概览、交易明细与退款管理</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <FinanceStat
          label="总收入"
          value={stats ? `¥${(stats.totalRevenue).toLocaleString('zh-CN')}` : '—'}
          icon={CircleDollarSign}
          delay="animation-delay-100"
        />
        <FinanceStat
          label="本月收入"
          value={stats ? `¥${stats.monthlyRevenue.toLocaleString('zh-CN')}` : '—'}
          trend="+23.8%"
          icon={TrendingUp}
          delay="animation-delay-200"
        />
        <FinanceStat
          label="退款金额"
          value={stats ? `¥${stats.refundAmount.toLocaleString('zh-CN')}` : '—'}
          icon={RefreshCw}
          delay="animation-delay-300"
        />
        <FinanceStat
          label="待结算"
          value={stats ? `¥${stats.pendingSettlement.toLocaleString('zh-CN')}` : '—'}
          icon={Clock}
          delay="animation-delay-500"
        />
      </div>

      {/* Monthly revenue chart */}
      <div className="card-editorial p-6 md:p-8 animate-fade-up opacity-0 animation-delay-100">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="font-display text-xl font-700 text-ink-900">月度收入趋势</h2>
            <p className="text-sm text-ink-500">近 12 个月收入与退款对比（单位：元）</p>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <span className="flex items-center gap-2">
              <span className="w-3 h-3 bg-indigo-800" />
              <span className="text-ink-600">收入</span>
            </span>
            <span className="flex items-center gap-2">
              <span className="w-3 h-3 bg-amber-600" />
              <span className="text-ink-600">退款</span>
            </span>
          </div>
        </div>
        <ResponsiveContainer width="100%" height={320}>
          <BarChart data={monthly} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" vertical={false} />
            <XAxis
              dataKey="month"
              tick={{ fontSize: 12, fill: '#78716c' }}
              axisLine={{ stroke: '#d6d3d1' }}
              tickLine={false}
            />
            <YAxis
              tick={{ fontSize: 12, fill: '#78716c' }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v: number) => `${v / 10000}万`}
            />
            <Tooltip
              contentStyle={{
                background: '#fff',
                border: '1px solid #e7e5e4',
                borderRadius: 0,
                fontSize: 13,
              }}
              formatter={(value: number) => `¥${value.toLocaleString('zh-CN')}`}
              cursor={{ fill: 'rgba(30,27,75,0.04)' }}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Bar dataKey="revenue" name="收入" fill="#1e1b4b" radius={[0, 0, 0, 0]} barSize={18} />
            <Bar dataKey="refund" name="退款" fill="#d97706" radius={[0, 0, 0, 0]} barSize={18} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Transaction list */}
        <div className="lg:col-span-2 animate-fade-up opacity-0 animation-delay-200">
          <div className="card-editorial p-6">
            <h2 className="font-display text-xl font-700 text-ink-900 mb-5">最近交易</h2>
            <DataTable
              columns={columns}
              data={transactions.slice(0, 8)}
              keyExtractor={(o) => o.id}
              emptyText="暂无交易记录"
            />
          </div>
        </div>

        {/* Refund management */}
        <div className="animate-fade-up opacity-0 animation-delay-300">
          <div className="card-editorial p-6">
            <div className="flex items-center gap-2 mb-5">
              <RefreshCw size={18} className="text-amber-600" />
              <h2 className="font-display text-xl font-700 text-ink-900">退款管理</h2>
            </div>
            <div className="space-y-3">
              {refundRequests.length === 0 ? (
                <p className="text-sm text-ink-400 text-center py-8">暂无退款申请</p>
              ) : (
                refundRequests.map((o) => (
                  <div key={o.id} className="p-3 border border-ink-100 hover:border-ink-200 transition-colors">
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <span className="font-mono text-xs text-ink-500 truncate">{o.orderNo}</span>
                      <span className={o.status === 'REFUNDED' ? 'badge-red' : 'badge-amber'}>
                        {o.status === 'REFUNDED' ? '已退款' : '处理中'}
                      </span>
                    </div>
                    <div className="text-sm text-ink-700 truncate mb-1">{o.courseName}</div>
                    <div className="flex items-center justify-between">
                      <span className="font-display font-700 text-ink-900">¥{o.amount.toFixed(2)}</span>
                      <span className="text-xs text-ink-400">{o.userName}</span>
                    </div>
                    {o.status === 'PENDING' && (
                      <div className="flex gap-2 mt-3 pt-3 border-t border-ink-50">
                        <button className="flex-1 text-xs py-1.5 bg-green-50 text-green-700 border border-green-200 hover:bg-green-100 transition-colors inline-flex items-center justify-center gap-1">
                          <CheckCircle size={12} />
                          同意
                        </button>
                        <button className="flex-1 text-xs py-1.5 bg-red-50 text-red-700 border border-red-200 hover:bg-red-100 transition-colors inline-flex items-center justify-center gap-1">
                          <XCircle size={12} />
                          拒绝
                        </button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function FinanceStat({
  label,
  value,
  trend,
  icon: Icon,
  delay,
}: {
  label: string;
  value: string;
  trend?: string;
  icon: LucideIcon;
  delay?: string;
}) {
  return (
    <div className={`stat-card animate-fade-up opacity-0 ${delay ?? ''}`}>
      <div className="flex items-start justify-between mb-4">
        <span className="text-xs font-medium uppercase tracking-widest text-ink-400">{label}</span>
        <span className="flex items-center justify-center w-9 h-9 bg-indigo-50 text-indigo-800">
          <Icon size={18} />
        </span>
      </div>
      <div className="font-display text-2xl md:text-3xl font-700 text-ink-900 leading-none mb-2">
        {value}
      </div>
      {trend && <span className="text-sm text-green-600 font-medium">{trend} 较上月</span>}
    </div>
  );
}
