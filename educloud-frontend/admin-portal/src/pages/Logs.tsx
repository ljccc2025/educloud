import { useEffect, useState } from 'react';
import { Search, ChevronLeft, ChevronRight, Info, AlertTriangle, XCircle } from 'lucide-react';
import DataTable, { type Column } from '../components/DataTable';
import { logApi } from '../services/api';
import type { AuditLog, LogLevel } from '../types';
import { cn } from '../utils/cn';

const levelConfig: Record<LogLevel, { cls: string; icon: typeof Info; text: string }> = {
  INFO: { cls: 'badge-indigo', icon: Info, text: 'INFO' },
  WARN: { cls: 'badge-amber', icon: AlertTriangle, text: 'WARN' },
  ERROR: { cls: 'badge-red', icon: XCircle, text: 'ERROR' },
};

export default function Logs() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [level, setLevel] = useState('ALL');
  const [keyword, setKeyword] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const pageSize = 15;

  const load = () => {
    setLoading(true);
    logApi.getLogs({ page, pageSize, level, startDate, endDate }).then((res) => {
      let list = res.list;
      if (keyword) {
        const kw = keyword.toLowerCase();
        list = list.filter(
          (l) =>
            l.action.toLowerCase().includes(kw) ||
            l.operator.toLowerCase().includes(kw) ||
            l.target.toLowerCase().includes(kw) ||
            l.ip.includes(kw),
        );
      }
      setLogs(list);
      setTotal(res.total);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, level, startDate, endDate]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const columns: Column<AuditLog>[] = [
    {
      key: 'timestamp',
      header: '时间',
      sortable: true,
      sortValue: (l) => l.timestamp,
      render: (l) => <span className="text-ink-500 text-xs font-mono">{l.timestamp}</span>,
    },
    {
      key: 'level',
      header: '级别',
      render: (l) => {
        const cfg = levelConfig[l.level];
        const Icon = cfg.icon;
        return (
          <span className={cfg.cls}>
            <Icon size={11} />
            {cfg.text}
          </span>
        );
      },
    },
    { key: 'operator', header: '操作人', render: (l) => <span className="font-medium text-ink-800">{l.operator}</span> },
    { key: 'action', header: '操作', render: (l) => <span className="text-ink-700">{l.action}</span> },
    { key: 'target', header: '目标', render: (l) => <span className="text-ink-600">{l.target}</span> },
    {
      key: 'ip',
      header: 'IP 地址',
      render: (l) => <span className="font-mono text-xs text-ink-500">{l.ip}</span>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">系统</div>
        <h1 className="display-heading text-3xl md:text-4xl">操作日志</h1>
        <p className="text-ink-500 mt-2">记录平台所有管理操作，便于审计与追溯</p>
      </div>

      {/* Filters */}
      <div className="card-editorial p-4 md:p-5 animate-fade-up opacity-0 animation-delay-100">
        <div className="flex flex-col lg:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
              placeholder="搜索操作人、动作、目标或 IP..."
              className="input-field pl-9"
            />
          </div>
          <div className="flex flex-wrap gap-3">
            <input
              type="date"
              value={startDate}
              onChange={(e) => { setStartDate(e.target.value); setPage(1); }}
              className="input-field"
            />
            <span className="self-center text-ink-400">至</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => { setEndDate(e.target.value); setPage(1); }}
              className="input-field"
            />
            <select
              value={level}
              onChange={(e) => { setLevel(e.target.value); setPage(1); }}
              className="input-field appearance-none cursor-pointer min-w-[120px]"
            >
              <option value="ALL">全部级别</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
            </select>
          </div>
        </div>
      </div>

      {/* Summary badges */}
      <div className="grid grid-cols-3 gap-4 animate-fade-up opacity-0 animation-delay-200">
        <div className="stat-card flex items-center gap-3">
          <span className="flex items-center justify-center w-10 h-10 bg-indigo-50 text-indigo-800">
            <Info size={18} />
          </span>
          <div>
            <div className="font-display text-2xl font-700 text-ink-900">
              {logs.filter((l) => l.level === 'INFO').length}
            </div>
            <div className="text-xs text-ink-400 uppercase tracking-wider">信息</div>
          </div>
        </div>
        <div className="stat-card flex items-center gap-3">
          <span className="flex items-center justify-center w-10 h-10 bg-amber-50 text-amber-700">
            <AlertTriangle size={18} />
          </span>
          <div>
            <div className="font-display text-2xl font-700 text-ink-900">
              {logs.filter((l) => l.level === 'WARN').length}
            </div>
            <div className="text-xs text-ink-400 uppercase tracking-wider">警告</div>
          </div>
        </div>
        <div className="stat-card flex items-center gap-3">
          <span className="flex items-center justify-center w-10 h-10 bg-red-50 text-red-700">
            <XCircle size={18} />
          </span>
          <div>
            <div className="font-display text-2xl font-700 text-ink-900">
              {logs.filter((l) => l.level === 'ERROR').length}
            </div>
            <div className="text-xs text-ink-400 uppercase tracking-wider">错误</div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="animate-fade-up opacity-0 animation-delay-300">
        <DataTable
          columns={columns}
          data={logs}
          keyExtractor={(l) => l.id}
          loading={loading}
          emptyText="暂无日志记录"
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
            <span className="px-4 py-2 text-sm text-ink-600">{page} / {totalPages}</span>
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
