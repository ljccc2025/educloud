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
import {
  CircleDollarSign,
  TrendingUp,
  RefreshCw,
  Clock,
  CheckCircle,
  XCircle,
  FileCheck2,
  AlertTriangle,
  Play,
  Check,
  type LucideIcon,
} from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import { financeApi } from '../services/api';
import { paymentAdminApi, type RefundDetail, type ReconciliationBatch, type ReconciliationDiff } from '../services/paymentAdminApi';
import { analyticsAdminApi } from '../services/analyticsAdminApi';
import type { FinanceStats, MonthlyRevenue, Order } from '../types';
import { useChartColors } from '../hooks/useChartColors';

type FinanceTab = 'OVERVIEW' | 'REFUND_AUDIT' | 'RECONCILIATION';

export default function Finance() {
  const [activeTab, setActiveTab] = useState<FinanceTab>('OVERVIEW');
  const [stats, setStats] = useState<FinanceStats | null>(null);
  const [monthly, setMonthly] = useState<MonthlyRevenue[]>([]);
  const [transactions, setTransactions] = useState<Order[]>([]);
  const chartColors = useChartColors();

  // Refund audits state
  const [refunds, setRefunds] = useState<RefundDetail[]>([]);
  const [refundLoading, setRefundLoading] = useState(false);
  const [auditRemark, setAuditRemark] = useState('');
  const [selectedRefund, setSelectedRefund] = useState<RefundDetail | null>(null);
  const [isApproveAction, setIsApproveAction] = useState(true);

  // Reconciliation state
  const [batches, setBatches] = useState<ReconciliationBatch[]>([]);
  const [selectedBatch, setSelectedBatch] = useState<ReconciliationBatch | null>(null);
  const [diffs, setDiffs] = useState<ReconciliationDiff[]>([]);
  const [reconcileDate, setReconcileDate] = useState(new Date().toISOString().split('T')[0]);
  const [reconcileChannel, setReconcileChannel] = useState<'MOCK' | 'ALIPAY' | 'WECHAT'>('MOCK');
  const [reconcileLoading, setReconcileLoading] = useState(false);
  const [selectedDiff, setSelectedDiff] = useState<ReconciliationDiff | null>(null);
  const [resolveAction, setResolveAction] = useState('MANUAL_REPAIR');
  const [resolveRemark, setResolveRemark] = useState('');

  const loadOverview = () => {
    analyticsAdminApi.getFinanceOverview().then((res) => {
      if (res?.stats) setStats(res.stats);
      if (res?.monthly) setMonthly(res.monthly);
    }).catch((e) => {
      console.warn('Failed to load finance overview from analytics backend, using fallback:', e);
      void Promise.all([
        financeApi.getStats(),
        financeApi.getMonthlyRevenue(),
        financeApi.getTransactions(),
      ]).then(([s, m, t]) => {
        setStats(s);
        setMonthly(m);
        setTransactions(t);
      });
    });

    financeApi.getTransactions().then(setTransactions).catch(console.warn);
  };

  const loadRefunds = async () => {
    setRefundLoading(true);
    try {
      const res = await paymentAdminApi.listRefunds();
      setRefunds(res.items || []);
    } catch (e) {
      console.error('Failed to load refunds:', e);
    } finally {
      setRefundLoading(false);
    }
  };

  const loadBatches = async () => {
    try {
      const res = await paymentAdminApi.listBatches();
      setBatches(res.items || []);
      if (res.items && res.items.length > 0 && !selectedBatch) {
        setSelectedBatch(res.items[0]);
        loadDiffs(res.items[0].id);
      }
    } catch (e) {
      console.error('Failed to load reconciliation batches:', e);
    }
  };

  const loadDiffs = async (batchId: string) => {
    try {
      const res = await paymentAdminApi.listDiffs(batchId);
      setDiffs(res.items || []);
    } catch (e) {
      console.error('Failed to load diffs:', e);
    }
  };

  useEffect(() => {
    loadOverview();
  }, []);

  useEffect(() => {
    if (activeTab === 'REFUND_AUDIT') {
      void loadRefunds();
    } else if (activeTab === 'RECONCILIATION') {
      void loadBatches();
    }
  }, [activeTab]);

  const handleAuditSubmit = async () => {
    if (!selectedRefund) return;
    try {
      await paymentAdminApi.auditRefund(selectedRefund.refundId, isApproveAction, auditRemark);
      setSelectedRefund(null);
      setAuditRemark('');
      void loadRefunds();
    } catch (e) {
      alert('审核提交失败: ' + String(e));
    }
  };

  const handleTriggerReconciliation = async () => {
    setReconcileLoading(true);
    try {
      const batch = await paymentAdminApi.triggerReconciliation(reconcileDate, reconcileChannel);
      await loadBatches();
      setSelectedBatch(batch);
      await loadDiffs(batch.id);
    } catch (e) {
      alert('触发对账失败: ' + String(e));
    } finally {
      setReconcileLoading(false);
    }
  };

  const handleResolveDiff = async () => {
    if (!selectedDiff) return;
    try {
      await paymentAdminApi.resolveDiff(selectedDiff.id, resolveAction, resolveRemark);
      setSelectedDiff(null);
      setResolveRemark('');
      if (selectedBatch) {
        await loadDiffs(selectedBatch.id);
        await loadBatches();
      }
    } catch (e) {
      alert('平账操作失败: ' + String(e));
    }
  };

  const columns: Column<Order>[] = [
    {
      key: 'orderNo',
      header: '订单号',
      render: (o) => <span className="font-mono text-xs text-brand-500 dark:text-brand-400">{o.orderNo}</span>,
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
      render: (o) => <span className="font-display font-bold text-ink-900">¥{o.amount.toFixed(2)}</span>,
    },
    {
      key: 'status',
      header: '状态',
      width: '96px',
      render: (o) => (
        <span
          className={
            'whitespace-nowrap inline-flex items-center justify-center ' +
            (o.status === 'PAID'
              ? 'badge-green'
              : o.status === 'REFUNDED'
                ? 'badge-red'
                : 'badge-amber')
          }
        >
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
        <div className="section-label mb-2">支付与财务中心</div>
        <h1 className="display-heading text-3xl md:text-4xl">财务与对账管理</h1>
        <p className="text-ink-500 mt-2">收入概览、退款多级审核与日终渠道双向对账平账</p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-ink-100 dark:border-ink-800 gap-6">
        <button
          onClick={() => setActiveTab('OVERVIEW')}
          className={`pb-3 font-medium text-sm transition-colors border-b-2 ${
            activeTab === 'OVERVIEW'
              ? 'border-brand-500 text-brand-600 dark:text-brand-400'
              : 'border-transparent text-ink-500 hover:text-ink-900'
          }`}
        >
          收入与交易概览
        </button>
        <button
          onClick={() => setActiveTab('REFUND_AUDIT')}
          className={`pb-3 font-medium text-sm transition-colors border-b-2 flex items-center gap-1.5 ${
            activeTab === 'REFUND_AUDIT'
              ? 'border-brand-500 text-brand-600 dark:text-brand-400'
              : 'border-transparent text-ink-500 hover:text-ink-900'
          }`}
        >
          <RefreshCw size={14} />
          退款审核中心
        </button>
        <button
          onClick={() => setActiveTab('RECONCILIATION')}
          className={`pb-3 font-medium text-sm transition-colors border-b-2 flex items-center gap-1.5 ${
            activeTab === 'RECONCILIATION'
              ? 'border-brand-500 text-brand-600 dark:text-brand-400'
              : 'border-transparent text-ink-500 hover:text-ink-900'
          }`}
        >
          <FileCheck2 size={14} />
          日终对账平账
        </button>
      </div>

      {activeTab === 'OVERVIEW' && (
        <>
          {/* Stats */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <FinanceStat
              label="总收入"
              value={stats ? `¥${stats.totalRevenue.toLocaleString('zh-CN')}` : '—'}
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
                <h2 className="font-display text-xl font-bold text-ink-900">月度收入趋势</h2>
                <p className="text-sm text-ink-500">近 12 个月收入与退款对比（单位：元）</p>
              </div>
              <div className="flex items-center gap-4 text-sm">
                <span className="flex items-center gap-2">
                  <span className="w-3 h-3 bg-brand-500" />
                  <span className="text-ink-600">收入</span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="w-3 h-3 bg-amber-500" />
                  <span className="text-ink-600">退款</span>
                </span>
              </div>
            </div>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={monthly} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} vertical={false} />
                <XAxis dataKey="month" tick={{ fontSize: 12, fill: chartColors.text }} axisLine={{ stroke: chartColors.axis }} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: chartColors.text }} axisLine={false} tickLine={false} tickFormatter={(v: number) => `${v / 10000}万`} />
                <Tooltip
                  contentStyle={{
                    background: chartColors.tooltipBg,
                    border: `1px solid ${chartColors.tooltipBorder}`,
                    borderRadius: '12px',
                    fontSize: 13,
                    color: chartColors.tooltipText,
                  }}
                  formatter={(value: number) => `¥${value.toLocaleString('zh-CN')}`}
                  cursor={{ fill: chartColors.cursor }}
                />
                <Legend wrapperStyle={{ fontSize: 12, color: chartColors.legend }} />
                <Bar dataKey="revenue" name="收入" fill={chartColors.primary} radius={[4, 4, 0, 0]} barSize={18} />
                <Bar dataKey="refund" name="退款" fill={chartColors.secondary} radius={[4, 4, 0, 0]} barSize={18} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Transaction list */}
          <div className="card-editorial p-6">
            <h2 className="font-display text-xl font-bold text-ink-900 mb-5">最近交易</h2>
            <DataTable
              columns={columns}
              data={transactions.slice(0, 8)}
              keyExtractor={(o) => o.id}
              emptyText="暂无交易记录"
            />
          </div>
        </>
      )}

      {activeTab === 'REFUND_AUDIT' && (
        <div className="space-y-6">
          <div className="card-editorial p-6">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h2 className="font-display text-xl font-bold text-ink-900">退款申请审核</h2>
                <p className="text-sm text-ink-500">财务审核后将自动调用支付渠道原路退回款项，并撤销学员课程权益</p>
              </div>
              <button
                onClick={() => void loadRefunds()}
                className="btn-secondary text-xs py-1.5 px-3 flex items-center gap-1.5"
              >
                <RefreshCw size={12} />
                刷新列表
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-ink-100 dark:border-ink-800 text-ink-500 text-xs uppercase">
                    <th className="py-3 px-4">退款单号</th>
                    <th className="py-3 px-4">订单ID</th>
                    <th className="py-3 px-4">退款金额</th>
                    <th className="py-3 px-4">渠道</th>
                    <th className="py-3 px-4">退款原因</th>
                    <th className="py-3 px-4">状态</th>
                    <th className="py-3 px-4">申请时间</th>
                    <th className="py-3 px-4 text-right">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-ink-50 dark:divide-ink-800/50">
                  {refunds.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-ink-400">
                        {refundLoading ? '正在加载退款申请...' : '暂无待审核退款记录'}
                      </td>
                    </tr>
                  ) : (
                    refunds.map((r) => (
                      <tr key={r.refundId} className="hover:bg-ink-50/50 dark:hover:bg-ink-800/30">
                        <td className="py-3 px-4 font-mono text-xs text-brand-600 dark:text-brand-400">{r.refundId}</td>
                        <td className="py-3 px-4 font-mono text-xs text-ink-600">{r.orderId}</td>
                        <td className="py-3 px-4 font-bold text-ink-900">¥{(r.refundAmountCents / 100).toFixed(2)}</td>
                        <td className="py-3 px-4 text-xs">{r.channelCode}</td>
                        <td className="py-3 px-4 text-xs text-ink-600 truncate max-w-[150px]">{r.reason || '无'}</td>
                        <td className="py-3 px-4">
                          <span
                            className={
                              'badge ' +
                              (r.status === 'SUCCESS'
                                ? 'badge-green'
                                : r.status === 'APPLIED'
                                  ? 'badge-amber'
                                  : r.status === 'REJECTED' || r.status === 'FAILED'
                                    ? 'badge-red'
                                    : 'badge-blue')
                            }
                          >
                            {r.status === 'SUCCESS'
                              ? '退款成功'
                              : r.status === 'APPLIED'
                                ? '待审核'
                                : r.status === 'REJECTED'
                                  ? '已拒绝'
                                  : r.status === 'FAILED'
                                    ? '退款失败'
                                    : '处理中'}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-xs text-ink-400">{r.createdAt}</td>
                        <td className="py-3 px-4 text-right">
                          {r.status === 'APPLIED' && (
                            <div className="flex justify-end gap-2">
                              <button
                                onClick={() => {
                                  setSelectedRefund(r);
                                  setIsApproveAction(true);
                                }}
                                className="px-2.5 py-1 text-xs bg-green-500/15 text-green-600 dark:text-green-400 border border-green-500/20 hover:bg-green-500/25 transition-colors inline-flex items-center gap-1"
                              >
                                <CheckCircle size={12} />
                                同意
                              </button>
                              <button
                                onClick={() => {
                                  setSelectedRefund(r);
                                  setIsApproveAction(false);
                                }}
                                className="px-2.5 py-1 text-xs bg-red-500/15 text-red-600 dark:text-red-400 border border-red-500/20 hover:bg-red-500/25 transition-colors inline-flex items-center gap-1"
                              >
                                <XCircle size={12} />
                                拒绝
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'RECONCILIATION' && (
        <div className="space-y-6">
          {/* Trigger box */}
          <div className="card-editorial p-6">
            <h2 className="font-display text-xl font-bold text-ink-900 mb-2">日终对账比对</h2>
            <p className="text-sm text-ink-500 mb-4">比对本地交易流水与第三方渠道账单，自动识别本地单边、渠道单边、金额不符与状态不符差错</p>
            <div className="flex flex-wrap items-center gap-4">
              <div>
                <label className="block text-xs font-medium text-ink-600 mb-1">对账日期</label>
                <input
                  type="date"
                  value={reconcileDate}
                  onChange={(e) => setReconcileDate(e.target.value)}
                  className="input-base text-sm px-3 py-1.5"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-600 mb-1">支付渠道</label>
                <select
                  value={reconcileChannel}
                  onChange={(e) => setReconcileChannel(e.target.value as any)}
                  className="input-base text-sm px-3 py-1.5"
                >
                  <option value="MOCK">MOCK 沙箱</option>
                  <option value="ALIPAY">支付宝 (Alipay)</option>
                  <option value="WECHAT">微信支付 (WeChatPay)</option>
                </select>
              </div>
              <div className="self-end">
                <button
                  onClick={() => void handleTriggerReconciliation()}
                  disabled={reconcileLoading}
                  className="btn-primary text-sm py-2 px-4 flex items-center gap-2"
                >
                  <Play size={14} />
                  {reconcileLoading ? '正在对账比对...' : '开始执行对账'}
                </button>
              </div>
            </div>
          </div>

          {/* Batches and Diffs Split Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Batches list */}
            <div className="card-editorial p-6">
              <h3 className="font-display text-lg font-bold text-ink-900 mb-4">对账批次历史</h3>
              <div className="space-y-3">
                {batches.length === 0 ? (
                  <p className="text-sm text-ink-400 text-center py-6">暂无对账批次</p>
                ) : (
                  batches.map((b) => (
                    <div
                      key={b.id}
                      onClick={() => {
                        setSelectedBatch(b);
                        void loadDiffs(b.id);
                      }}
                      className={`p-3 border transition-colors cursor-pointer ${
                        selectedBatch?.id === b.id
                          ? 'border-brand-500 bg-brand-50/50 dark:bg-brand-900/10'
                          : 'border-ink-100 hover:border-ink-200'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-mono text-xs font-bold text-ink-700">{b.batchNo}</span>
                        <span
                          className={
                            b.status === 'MATCHED'
                              ? 'badge-green'
                              : b.status === 'DIFF_FOUND'
                                ? 'badge-red'
                                : 'badge-blue'
                          }
                        >
                          {b.status === 'MATCHED'
                            ? '完全平齐'
                            : b.status === 'DIFF_FOUND'
                              ? `存在 ${b.diffCount} 项差错`
                              : '已平账'}
                        </span>
                      </div>
                      <div className="flex justify-between text-xs text-ink-500">
                        <span>日期: {b.reconcileDate} ({b.channelCode})</span>
                        <span>总金额: ¥{(b.totalAmountCents / 100).toFixed(2)}</span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Diffs list */}
            <div className="lg:col-span-2 card-editorial p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-display text-lg font-bold text-ink-900">
                  差错单明细 {selectedBatch ? `(${selectedBatch.batchNo})` : ''}
                </h3>
                <span className="text-xs text-ink-500">
                  差错总数: {diffs.length}
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-ink-100 dark:border-ink-800 text-ink-500 text-xs">
                      <th className="py-2.5 px-3">差错类型</th>
                      <th className="py-2.5 px-3">本地/渠道单号</th>
                      <th className="py-2.5 px-3">本地金额</th>
                      <th className="py-2.5 px-3">渠道金额</th>
                      <th className="py-2.5 px-3">平账状态</th>
                      <th className="py-2.5 px-3 text-right">操作</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-50 dark:divide-ink-800/50">
                    {diffs.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-ink-400">
                          当前批次无差错记录，账目完全一致
                        </td>
                      </tr>
                    ) : (
                      diffs.map((d) => (
                        <tr key={d.id} className="hover:bg-ink-50/50 dark:hover:bg-ink-800/30">
                          <td className="py-2.5 px-3">
                            <span className="inline-flex items-center gap-1 text-xs font-semibold text-amber-600 dark:text-amber-400">
                              <AlertTriangle size={12} />
                              {d.diffType === 'AMOUNT_MISMATCH'
                                ? '金额不一致'
                                : d.diffType === 'LOCAL_MORE'
                                  ? '本地单边'
                                  : d.diffType === 'CHANNEL_MORE'
                                    ? '渠道单边'
                                    : '状态不符'}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 font-mono text-xs text-ink-600">
                            {d.paymentOrderId || d.channelTradeNo || '—'}
                          </td>
                          <td className="py-2.5 px-3 font-mono text-xs">
                            ¥{((d.localAmountCents || 0) / 100).toFixed(2)}
                          </td>
                          <td className="py-2.5 px-3 font-mono text-xs">
                            ¥{((d.channelAmountCents || 0) / 100).toFixed(2)}
                          </td>
                          <td className="py-2.5 px-3">
                            <span className={d.resolveStatus === 'UNRESOLVED' ? 'badge-amber' : 'badge-green'}>
                              {d.resolveStatus === 'UNRESOLVED' ? '未平账' : '已平账'}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-right">
                            {d.resolveStatus === 'UNRESOLVED' && (
                              <button
                                onClick={() => setSelectedDiff(d)}
                                className="px-2.5 py-1 text-xs btn-primary inline-flex items-center gap-1"
                              >
                                平账
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Audit Modal */}
      {selectedRefund && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white dark:bg-ink-900 border border-ink-200 dark:border-ink-700 p-6 max-w-md w-full shadow-2xl">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-2">
              {isApproveAction ? '同意退款申请' : '拒绝退款申请'}
            </h3>
            <p className="text-xs text-ink-500 mb-4">
              退款单号: {selectedRefund.refundId}，退款金额: ¥{(selectedRefund.refundAmountCents / 100).toFixed(2)}
            </p>
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-ink-600 mb-1">审核备注 / 批语</label>
                <textarea
                  value={auditRemark}
                  onChange={(e) => setAuditRemark(e.target.value)}
                  placeholder="请输入审核备注（可选）"
                  className="input-base text-sm w-full p-2 h-20"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setSelectedRefund(null)}
                  className="btn-secondary text-xs py-1.5 px-3"
                >
                  取消
                </button>
                <button
                  onClick={() => void handleAuditSubmit()}
                  className={`text-xs py-1.5 px-4 text-white font-medium ${
                    isApproveAction ? 'bg-green-600 hover:bg-green-700' : 'bg-red-600 hover:bg-red-700'
                  }`}
                >
                  确认{isApproveAction ? '同意并原路退款' : '拒绝'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Diff Resolve Modal */}
      {selectedDiff && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white dark:bg-ink-900 border border-ink-200 dark:border-ink-700 p-6 max-w-md w-full shadow-2xl">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-2">差错单人工平账</h3>
            <p className="text-xs text-ink-500 mb-4">
              差错类型: {selectedDiff.diffType}，本地金额: ¥{((selectedDiff.localAmountCents || 0) / 100).toFixed(2)}，渠道金额: ¥{((selectedDiff.channelAmountCents || 0) / 100).toFixed(2)}
            </p>
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-ink-600 mb-1">平账动作</label>
                <select
                  value={resolveAction}
                  onChange={(e) => setResolveAction(e.target.value)}
                  className="input-base text-sm w-full p-2"
                >
                  <option value="MANUAL_REPAIR">人工补录/同步单据 (MANUAL_REPAIR)</option>
                  <option value="REFUND_OFFLINE">线下原路退款 (REFUND_OFFLINE)</option>
                  <option value="ADJUST_AMOUNT">账务差额调账 (ADJUST_AMOUNT)</option>
                  <option value="MANUAL_SYNC">强制渠道重查同步 (MANUAL_SYNC)</option>
                  <option value="IGNORE">忽略差错 (IGNORE)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-600 mb-1">平账说明备注</label>
                <textarea
                  value={resolveRemark}
                  onChange={(e) => setResolveRemark(e.target.value)}
                  placeholder="请输入平账说明与核对依据"
                  className="input-base text-sm w-full p-2 h-20"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setSelectedDiff(null)}
                  className="btn-secondary text-xs py-1.5 px-3"
                >
                  取消
                </button>
                <button
                  onClick={() => void handleResolveDiff()}
                  className="btn-primary text-xs py-1.5 px-4"
                >
                  确认平账
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
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
    <div className={`bg-white rounded-2xl p-5 border border-slate-100 shadow-sm hover:shadow-md transition-shadow flex items-center justify-between ${delay ?? ''}`}>
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-slate-900 text-white flex items-center justify-center flex-shrink-0 shadow-sm">
          <Icon className="w-6 h-6" />
        </div>
        <div>
          <p className="text-xs font-medium text-slate-500">{label}</p>
          <h3 className="text-2xl font-bold text-slate-900 mt-0.5">{value}</h3>
        </div>
      </div>
      {trend && (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-600">
          {trend}
        </span>
      )}
    </div>
  );
}
