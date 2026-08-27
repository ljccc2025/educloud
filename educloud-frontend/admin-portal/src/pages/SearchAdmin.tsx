import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Search,
  RefreshCw,
  Layers,
  Activity,
  CheckCircle2,
  XCircle,
  Clock,
  Database,
  ShieldCheck,
  AlertTriangle,
  Play,
  ArrowRight,
  Info,
} from 'lucide-react';
import dayjs from 'dayjs';
import {
  searchAdminApi,
  type IndexTaskProgressResponse,
  type TaskStatus,
} from '../services/searchAdminApi';
import { apiErrorText } from '../services/http';
import { cn } from '../utils/cn';

const statusBadge: Record<TaskStatus, { cls: string; icon: typeof CheckCircle2; text: string }> = {
  PENDING: { cls: 'badge-amber', icon: Clock, text: '排队等待' },
  RUNNING: { cls: 'badge-indigo', icon: RefreshCw, text: '正在同步' },
  SUCCESS: { cls: 'badge-green', icon: CheckCircle2, text: '重建成功' },
  FAILED: { cls: 'badge-red', icon: XCircle, text: '构建失败' },
};

function formatDuration(startedAt?: string, finishedAt?: string): string {
  if (!startedAt) return '—';
  const start = dayjs(startedAt);
  const end = finishedAt ? dayjs(finishedAt) : dayjs();
  const diffSec = end.diff(start, 'second');
  if (diffSec < 60) return `${diffSec} 秒`;
  const min = Math.floor(diffSec / 60);
  const sec = diffSec % 60;
  return `${min}分${sec}秒`;
}

function formatTime(value?: string | null): string {
  if (!value) return '—';
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
}

export default function SearchAdmin() {
  const [tasks, setTasks] = useState<IndexTaskProgressResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTask, setActiveTask] = useState<IndexTaskProgressResponse | null>(null);

  // 重建确认弹窗
  const [confirmModalOpen, setConfirmModalOpen] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [triggerError, setTriggerError] = useState<string | null>(null);

  // 轮询定时器
  const pollTimerRef = useRef<number | null>(null);

  const loadTasks = useCallback(async () => {
    try {
      const list = await searchAdminApi.fetchRecentTasks(20);
      setTasks(list || []);

      // 检查是否有正在运行或等待的任务
      const runningTask = list?.find(
        (t) => t.status === 'RUNNING' || t.status === 'PENDING',
      );
      if (runningTask) {
        setActiveTask(runningTask);
      } else if (activeTask && (activeTask.status === 'RUNNING' || activeTask.status === 'PENDING')) {
        // 更新最后的状态
        const updated = list?.find((t) => t.taskNo === activeTask.taskNo);
        if (updated) setActiveTask(updated);
      }
      setError(null);
    } catch (e) {
      setError(apiErrorText(e));
    } finally {
      setLoading(false);
    }
  }, [activeTask]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  // 活跃任务轮询逻辑（每 1.5 秒更新一次进度）
  useEffect(() => {
    if (activeTask && (activeTask.status === 'RUNNING' || activeTask.status === 'PENDING')) {
      pollTimerRef.current = window.setInterval(async () => {
        try {
          const progress = await searchAdminApi.fetchTaskProgress(activeTask.taskNo);
          setActiveTask(progress);
          if (progress.status === 'SUCCESS' || progress.status === 'FAILED') {
            // 任务结束，重新刷新列表
            void loadTasks();
          }
        } catch {
          // 忽略轮询单次偶发失败
        }
      }, 1500);
    } else {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    }

    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [activeTask, loadTasks]);

  // 触发全量重建
  const handleTriggerRebuild = async () => {
    setTriggering(true);
    setTriggerError(null);
    try {
      const initialTask = await searchAdminApi.triggerIndexRebuild();
      setConfirmModalOpen(false);
      setActiveTask(initialTask);
      void loadTasks();
    } catch (e) {
      setTriggerError(apiErrorText(e));
    } finally {
      setTriggering(false);
    }
  };

  const runningCount = tasks.filter((t) => t.status === 'RUNNING' || t.status === 'PENDING').length;
  const lastSuccessTask = tasks.find((t) => t.status === 'SUCCESS');

  return (
    <div className="space-y-6">
      {/* 页面顶栏 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-semibold uppercase tracking-widest text-brand-500 bg-brand-500/10 px-2 py-0.5 rounded border border-brand-500/20">
              Elasticsearch 8.x
            </span>
            <span className="text-xs text-ink-500">双别名原子热切架构</span>
          </div>
          <h1 className="text-2xl lg:text-3xl font-bold text-ink-900 tracking-tight">
            搜索与索引运维看板
          </h1>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => void loadTasks()}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm text-ink-700 bg-surface border border-ink-300 rounded-xl hover:bg-ink-100 transition-colors shadow-sm disabled:opacity-50"
          >
            <RefreshCw size={15} className={cn(loading && 'animate-spin')} />
            刷新状态
          </button>
          <button
            onClick={() => {
              setTriggerError(null);
              setConfirmModalOpen(true);
            }}
            disabled={runningCount > 0}
            className="btn-primary inline-flex items-center gap-2 shadow-glow-purple disabled:opacity-50"
          >
            <Play size={16} />
            一键全量重建索引
          </button>
        </div>
      </div>

      {/* 4 个概览数据卡片 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* 搜索引擎状态 */}
        <div className="stat-card">
          <div className="flex items-start justify-between mb-3">
            <span className="text-xs font-medium text-ink-500 uppercase tracking-wider">
              搜索引擎集群
            </span>
            <span className="p-2 rounded-xl bg-green-500/10 text-green-600 dark:text-green-400">
              <ShieldCheck size={18} />
            </span>
          </div>
          <div className="text-xl font-bold text-ink-900 mb-1 flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse" />
            集群健康 (Green)
          </div>
          <p className="text-xs text-ink-500">Elasticsearch 8.x 分布式引擎就绪</p>
        </div>

        {/* 检索路由别名 */}
        <div className="stat-card">
          <div className="flex items-start justify-between mb-3">
            <span className="text-xs font-medium text-ink-500 uppercase tracking-wider">
              搜索对外别名
            </span>
            <span className="p-2 rounded-xl bg-brand-500/10 text-brand-500 dark:text-brand-400">
              <Layers size={18} />
            </span>
          </div>
          <div className="text-lg font-mono font-bold text-brand-600 dark:text-brand-400 mb-1 truncate">
            educloud_course_search
          </div>
          <p className="text-xs text-ink-500">零停机别名平滑切换机制</p>
        </div>

        {/* 正在运行的任务 */}
        <div className="stat-card">
          <div className="flex items-start justify-between mb-3">
            <span className="text-xs font-medium text-ink-500 uppercase tracking-wider">
              活跃同步任务
            </span>
            <span className="p-2 rounded-xl bg-indigo-500/10 text-indigo-500">
              <Activity size={18} />
            </span>
          </div>
          <div className="text-2xl font-bold text-ink-900 mb-1">
            {runningCount} <span className="text-xs text-ink-500 font-normal">个进行中</span>
          </div>
          <p className="text-xs text-ink-500">
            {runningCount > 0 ? '全量索引数据同步中...' : '无排队中的重建任务'}
          </p>
        </div>

        {/* 最近成功时间 */}
        <div className="stat-card">
          <div className="flex items-start justify-between mb-3">
            <span className="text-xs font-medium text-ink-500 uppercase tracking-wider">
              最近重建完成
            </span>
            <span className="p-2 rounded-xl bg-amber-500/10 text-amber-500">
              <Database size={18} />
            </span>
          </div>
          <div className="text-sm font-semibold text-ink-800 mb-1">
            {lastSuccessTask?.finishedAt
              ? dayjs(lastSuccessTask.finishedAt).format('MM-DD HH:mm:ss')
              : '暂无历史'}
          </div>
          <p className="text-xs text-ink-500 truncate">
            {lastSuccessTask ? `处理 ${lastSuccessTask.processedRecords} 条数据` : '等待触发首次全量构建'}
          </p>
        </div>
      </div>

      {/* 实时活跃任务卡片 (若有 activeTask) */}
      {activeTask && (
        <div className="bg-surface border-2 border-brand-500/30 rounded-2xl p-5 shadow-lg shadow-brand-500/5 space-y-4 animate-fade-in">
          <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-ink-200/60">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-brand-500/10 text-brand-500 flex items-center justify-center">
                <RefreshCw
                  size={18}
                  className={cn(
                    activeTask.status === 'RUNNING' && 'animate-spin',
                  )}
                />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-ink-900 font-mono">
                    {activeTask.taskNo}
                  </span>
                  <span
                    className={cn(
                      'text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full border',
                      statusBadge[activeTask.status]?.cls,
                    )}
                  >
                    {statusBadge[activeTask.status]?.text}
                  </span>
                </div>
                <div className="text-xs text-ink-500 flex items-center gap-2 mt-0.5">
                  <span>目标物理索引: <code className="font-mono text-ink-700">{activeTask.indexName}</code></span>
                  <span>·</span>
                  <span>操作人: {activeTask.createdBy || 'admin'}</span>
                </div>
              </div>
            </div>

            <div className="text-right text-xs">
              <span className="text-ink-500">耗时: </span>
              <span className="font-semibold text-ink-800 font-mono">
                {formatDuration(activeTask.startedAt, activeTask.finishedAt)}
              </span>
            </div>
          </div>

          {/* 进度条与数字 */}
          <div className="space-y-2">
            <div className="flex justify-between items-center text-xs">
              <span className="font-medium text-ink-700">
                数据导入与索引构建进度
              </span>
              <span className="font-bold text-brand-600 font-mono">
                {activeTask.progressPercent}% ({activeTask.processedRecords} / {activeTask.totalRecords} 条)
              </span>
            </div>
            <div className="w-full h-3 bg-ink-100 rounded-full overflow-hidden border border-ink-200/60">
              <div
                className={cn(
                  'h-full transition-all duration-500 rounded-full',
                  activeTask.status === 'FAILED'
                    ? 'bg-red-500'
                    : activeTask.status === 'SUCCESS'
                      ? 'bg-green-500'
                      : 'bg-gradient-to-r from-brand-500 to-indigo-600',
                )}
                style={{ width: `${Math.max(activeTask.progressPercent, 4)}%` }}
              />
            </div>
          </div>

          {activeTask.errorMessage && (
            <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 dark:bg-red-950/30 p-2.5 rounded-xl border border-red-200 dark:border-red-900">
              <AlertTriangle size={15} className="shrink-0" />
              <span>失败原因: {activeTask.errorMessage}</span>
            </div>
          )}

          {activeTask.status === 'SUCCESS' && (
            <div className="flex items-center gap-2 text-xs text-green-700 bg-green-50 dark:bg-green-950/30 p-2.5 rounded-xl border border-green-200 dark:border-green-900">
              <CheckCircle2 size={15} className="shrink-0" />
              <span>索引全量构建完成，别名 <code>{activeTask.aliasName}</code> 已原子平滑切换至新物理索引！</span>
            </div>
          )}
        </div>
      )}

      {/* 历史索引重建任务表格 */}
      <div className="table-container">
        <div className="p-4 sm:p-5 border-b border-ink-200/60 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-bold text-ink-900">索引重建与修复历史任务</h2>
            <p className="text-xs text-ink-500 mt-0.5">
              记录所有全量重建与增量修复的操作记录与执行指标
            </p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="border-b border-ink-200/60 bg-ink-50/50 text-ink-600 uppercase font-semibold">
                <th className="py-3.5 px-4 whitespace-nowrap">任务编号</th>
                <th className="py-3.5 px-4 whitespace-nowrap">任务类型</th>
                <th className="py-3.5 px-4 whitespace-nowrap">物理索引名</th>
                <th className="py-3.5 px-4 whitespace-nowrap">状态</th>
                <th className="py-3.5 px-4 whitespace-nowrap">处理进度 (已完成/总量)</th>
                <th className="py-3.5 px-4 whitespace-nowrap">触发人</th>
                <th className="py-3.5 px-4 whitespace-nowrap">耗时</th>
                <th className="py-3.5 px-4 whitespace-nowrap">创建时间</th>
                <th className="py-3.5 px-4 whitespace-nowrap">完成时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-200/60">
              {loading ? (
                <tr>
                  <td colSpan={9} className="py-12 text-center text-ink-400">
                    <div className="inline-flex items-center gap-2">
                      <RefreshCw size={16} className="animate-spin text-brand-500" />
                      <span>正在加载索引任务历史...</span>
                    </div>
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={9} className="py-12 text-center text-red-500">
                    <div className="inline-flex items-center gap-2">
                      <AlertTriangle size={16} />
                      <span>{error}</span>
                    </div>
                  </td>
                </tr>
              ) : tasks.length === 0 ? (
                <tr>
                  <td colSpan={9} className="py-12 text-center text-ink-400">
                    暂无历史索引任务，点击右上角「一键全量重建索引」即可开始首次构建
                  </td>
                </tr>
              ) : (
                tasks.map((task) => {
                  const badge = statusBadge[task.status] || statusBadge.PENDING;
                  const Icon = badge.icon;
                  const isRunning = task.status === 'RUNNING' || task.status === 'PENDING';

                  return (
                    <tr
                      key={task.taskNo}
                      className={cn(
                        'hover:bg-ink-50/70 transition-colors',
                        activeTask?.taskNo === task.taskNo && 'bg-brand-500/5',
                      )}
                    >
                      {/* 任务编号 */}
                      <td className="py-3.5 px-4 font-mono font-semibold text-ink-900">
                        {task.taskNo}
                      </td>

                      {/* 任务类型 */}
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        <span className="text-[11px] font-medium text-ink-700 bg-ink-100 dark:bg-ink-800 px-2.5 py-1 rounded-md whitespace-nowrap inline-block">
                          {task.taskType === 'FULL_REBUILD' ? '全量重建' : '增量修复'}
                        </span>
                      </td>

                      {/* 目标物理索引 */}
                      <td className="py-3.5 px-4 font-mono text-ink-700 whitespace-nowrap">
                        {task.indexName}
                      </td>

                      {/* 状态 */}
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        <span
                          className={cn(
                            'inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-medium border whitespace-nowrap',
                            badge.cls,
                          )}
                        >
                          <Icon size={11} className={cn(isRunning && 'animate-spin')} />
                          {badge.text}
                        </span>
                      </td>

                      {/* 进度 */}
                      <td className="py-3.5 px-4">
                        <div className="w-32 space-y-1">
                          <div className="flex justify-between text-[11px] font-mono">
                            <span>{task.processedRecords} / {task.totalRecords}</span>
                            <span className="font-semibold">{task.progressPercent}%</span>
                          </div>
                          <div className="w-full h-1.5 bg-ink-200/60 rounded-full overflow-hidden">
                            <div
                              className={cn(
                                'h-full rounded-full',
                                task.status === 'FAILED'
                                  ? 'bg-red-500'
                                  : task.status === 'SUCCESS'
                                    ? 'bg-green-500'
                                    : 'bg-brand-500',
                              )}
                              style={{ width: `${task.progressPercent}%` }}
                            />
                          </div>
                        </div>
                      </td>

                      {/* 触发人 */}
                      <td className="py-3.5 px-4 text-ink-700">
                        {task.createdBy || '系统/admin'}
                      </td>

                      {/* 耗时 */}
                      <td className="py-3.5 px-4 font-mono text-ink-600">
                        {formatDuration(task.startedAt, task.finishedAt)}
                      </td>

                      {/* 创建时间 */}
                      <td className="py-3.5 px-4 text-ink-500 whitespace-nowrap">
                        {formatTime(task.createdAt)}
                      </td>

                      {/* 完成时间 */}
                      <td className="py-3.5 px-4 text-ink-500 whitespace-nowrap">
                        {formatTime(task.finishedAt)}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 全量重建确认 Modal */}
      {confirmModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-black/50 backdrop-blur-sm animate-fade-in"
            onClick={() => !triggering && setConfirmModalOpen(false)}
          />
          <div className="relative bg-surface rounded-2xl p-6 max-w-lg w-full shadow-2xl border border-ink-200/60 space-y-5 animate-scale-in">
            <div className="flex items-start gap-4">
              <div className="w-12 h-12 rounded-2xl bg-brand-500/10 text-brand-500 flex items-center justify-center shrink-0">
                <Database size={24} />
              </div>
              <div className="flex-1">
                <h3 className="text-lg font-bold text-ink-900">
                  确认执行全量索引平滑重建？
                </h3>
                <p className="text-xs text-ink-500 mt-1">
                  全量重建课程检索数据，重塑倒排索引与拼音补全字典。
                </p>
              </div>
            </div>

            <div className="bg-ink-50 dark:bg-ink-900/50 p-4 rounded-xl space-y-2 text-xs text-ink-600 border border-ink-200/60">
              <div className="font-semibold text-ink-800 flex items-center gap-1.5">
                <Info size={14} className="text-brand-500" />
                零停机平滑切换流程说明：
              </div>
              <ul className="list-disc list-inside space-y-1 pl-1 text-[11px] leading-relaxed">
                <li>自动创建带最新时间戳物理索引（如 <code>educloud_course_2026xxxx</code>）；</li>
                <li>分批异步全量拉取已发布的全部课程并写入新索引；</li>
                <li>验证数据完整性后，将别名 <code>educloud_course_search</code> 原子切换；</li>
                <li>学生端与对外搜索服务全程<b>零中断、无感知</b>。</li>
              </ul>
            </div>

            {triggerError && (
              <div className="text-xs text-red-600 bg-red-50 dark:bg-red-950/30 p-3 rounded-xl border border-red-200 dark:border-red-900 flex items-center gap-2">
                <AlertTriangle size={15} className="shrink-0" />
                <span>{triggerError}</span>
              </div>
            )}

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                disabled={triggering}
                onClick={() => setConfirmModalOpen(false)}
                className="btn-secondary text-xs px-4 py-2.5 disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="button"
                disabled={triggering}
                onClick={() => void handleTriggerRebuild()}
                className="btn-primary text-xs px-4 py-2.5 inline-flex items-center gap-2 disabled:opacity-50"
              >
                {triggering ? (
                  <>
                    <RefreshCw size={14} className="animate-spin" />
                    正在触发...
                  </>
                ) : (
                  <>
                    <Play size={14} />
                    确认开始重建
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
