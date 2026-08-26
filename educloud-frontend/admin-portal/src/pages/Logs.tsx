import { useEffect, useState } from 'react';
import { Search, ChevronLeft, ChevronRight, Info, AlertTriangle, XCircle, FileText, X } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import { analyticsAdminApi } from '../services/analyticsAdminApi';
import type { AuditLog, LogLevel } from '../types';

const levelConfig: Record<LogLevel, { cls: string; icon: typeof Info; text: string }> = {
  INFO: { cls: 'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-600', icon: Info, text: 'INFO' },
  WARN: { cls: 'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-600', icon: AlertTriangle, text: 'WARN' },
  ERROR: { cls: 'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-50 text-red-600', icon: XCircle, text: 'ERROR' },
};

export default function Logs() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [level, setLevel] = useState('ALL');
  const [keyword, setKeyword] = useState('');
  const [sourceService, setSourceService] = useState('ALL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
  const pageSize = 15;

  const load = () => {
    setLoading(true);
    analyticsAdminApi.getAuditLogs({
      page,
      pageSize,
      level: level === 'ALL' ? undefined : level,
      keyword: keyword || undefined,
      sourceService: sourceService === 'ALL' ? undefined : sourceService,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
    }).then((res) => {
      setLogs(res.list);
      setTotal(res.total);
      setLoading(false);
    }).catch((e) => {
      console.warn('Failed to load audit logs from analytics API:', e);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, level, sourceService, startDate, endDate]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    load();
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const columns: Column<AuditLog>[] = [
    {
      key: 'timestamp',
      header: '发生时间',
      render: (l) => <span className="text-slate-500 text-xs font-mono">{l.timestamp}</span>,
    },
    {
      key: 'level',
      header: '级别',
      render: (l) => {
        const cfg = levelConfig[l.level] || levelConfig.INFO;
        const Icon = cfg.icon;
        return (
          <span className={cfg.cls}>
            <Icon size={12} />
            {cfg.text}
          </span>
        );
      },
    },
    {
      key: 'sourceService',
      header: '来源服务',
      render: (l) => (
        <span className="px-2 py-0.5 rounded text-xs font-mono bg-slate-100 text-slate-700">
          {(l as any).sourceService || 'educloud'}
        </span>
      ),
    },
    { key: 'operator', header: '操作人', render: (l) => <span className="font-semibold text-slate-900 text-xs">{l.operator}</span> },
    { key: 'action', header: '操作动作', render: (l) => <span className="text-slate-700 font-mono text-xs">{l.action}</span> },
    { key: 'target', header: '目标资源', render: (l) => <span className="text-slate-600 text-xs">{l.target}</span> },
    {
      key: 'ip',
      header: '客户端 IP',
      render: (l) => <span className="font-mono text-xs text-slate-500">{l.ip}</span>,
    },
    {
      key: 'detail',
      header: '详情',
      render: (l) => (
        <button
          onClick={() => setSelectedLog(l)}
          className="inline-flex items-center gap-1 text-xs text-blue-600 font-semibold hover:underline"
        >
          <FileText size={13} />
          查看
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-6 w-full max-w-full pb-10">
      {/* 顶部标题 */}
      <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 text-xs font-semibold bg-blue-50 text-blue-600 rounded-full">集中式审计中心</span>
            <span className="text-xs text-slate-400">实时事件汇聚</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 mt-1">全平台操作审计日志</h1>
          <p className="text-sm text-slate-500 mt-0.5">多维组合检索全微服务敏感操作、业务流转与异常事件记录</p>
        </div>
      </div>

      {/* 过滤器 */}
      <form onSubmit={handleSearch} className="bg-white p-5 rounded-2xl border border-slate-100 shadow-sm space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索动作、用户、IP..."
              className="w-full pl-9 pr-3 py-2 text-xs rounded-xl border border-slate-200 focus:outline-none focus:border-blue-600"
            />
          </div>

          <select
            value={level}
            onChange={(e) => { setLevel(e.target.value); setPage(1); }}
            className="px-3 py-2 text-xs rounded-xl border border-slate-200 focus:outline-none focus:border-blue-600 bg-white text-slate-700"
          >
            <option value="ALL">全部日志级别</option>
            <option value="INFO">INFO (正常)</option>
            <option value="WARN">WARN (警告)</option>
            <option value="ERROR">ERROR (异常)</option>
          </select>

          <select
            value={sourceService}
            onChange={(e) => { setSourceService(e.target.value); setPage(1); }}
            className="px-3 py-2 text-xs rounded-xl border border-slate-200 focus:outline-none focus:border-blue-600 bg-white text-slate-700"
          >
            <option value="ALL">全部来源服务</option>
            <option value="educloud-user">educloud-user (用户)</option>
            <option value="educloud-course">educloud-course (课程)</option>
            <option value="educloud-order">educloud-order (订单)</option>
            <option value="educloud-payment">educloud-payment (支付)</option>
            <option value="educloud-search">educloud-search (搜索)</option>
          </select>

          <input
            type="date"
            value={startDate}
            onChange={(e) => { setStartDate(e.target.value); setPage(1); }}
            className="px-3 py-2 text-xs rounded-xl border border-slate-200 focus:outline-none focus:border-blue-600 bg-white text-slate-700"
          />

          <button
            type="submit"
            className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-xl transition-colors shadow-sm"
          >
            查询日志
          </button>
        </div>
      </form>

      {/* 数据表格 */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden p-6">
        <DataTable
          columns={columns}
          data={logs}
          loading={loading}
          keyExtractor={(l) => String(l.id)}
          emptyText="暂无匹配的操作审计日志"
        />

        {/* 分页控制 */}
        <div className="mt-4 flex items-center justify-between text-xs text-slate-500 pt-4 border-t border-slate-100">
          <span>共 {total} 条记录 · 第 {page} / {totalPages} 页</span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page <= 1}
              className="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 disabled:opacity-40 transition-colors"
            >
              <ChevronLeft size={14} />
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              className="p-2 rounded-lg border border-slate-200 hover:bg-slate-50 disabled:opacity-40 transition-colors"
            >
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* JSON 详情抽屉/弹窗 */}
      {selectedLog && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-100 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-600" />
                <h3 className="font-bold text-slate-900 text-base">审计事件 Payload 详情</h3>
              </div>
              <button
                onClick={() => setSelectedLog(null)}
                className="text-slate-400 hover:text-slate-600"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">审计 ID</span>
                <span className="font-mono font-bold text-slate-800">{selectedLog.id}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">操作人</span>
                <span className="font-semibold text-slate-800">{selectedLog.operator}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">动作</span>
                <span className="font-mono font-semibold text-slate-800">{selectedLog.action}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">目标资源</span>
                <span className="font-mono text-slate-800">{selectedLog.target}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-100">
                <span className="text-slate-500">客户端 IP</span>
                <span className="font-mono text-slate-800">{selectedLog.ip}</span>
              </div>
              <div className="pt-2">
                <span className="text-slate-500 block mb-1">Payload JSON</span>
                <pre className="p-3 bg-slate-900 text-emerald-400 rounded-xl font-mono text-[11px] overflow-auto max-h-48 whitespace-pre-wrap">
                  {selectedLog.detail || '{}'}
                </pre>
              </div>
            </div>

            <div className="pt-2">
              <button
                onClick={() => setSelectedLog(null)}
                className="w-full py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 font-semibold text-xs rounded-xl transition-colors"
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
