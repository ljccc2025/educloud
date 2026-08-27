import { useCallback, useEffect, useRef, useState } from 'react';
import { CheckCircle, XCircle, RefreshCw, AlertCircle, Archive, RotateCcw, Ban } from 'lucide-react';
import dayjs from 'dayjs';
import { courseAuditApi, adminCourseApi, courseLifecycleApi } from '../services/courseAdminApi';
import { apiErrorText } from '../services/http';
import type { AdminCourse, CourseAuditItem, CourseLifecycleStatus } from '../types';
import { cn } from '../utils/cn';

type MainTab = 'AUDIT' | 'MANAGE';
type LifecycleTab = 'PUBLISHED' | 'OFFLINE' | 'ARCHIVED';

const mainTabs: { key: MainTab; label: string }[] = [
  { key: 'AUDIT', label: '待审核' },
  { key: 'MANAGE', label: '课程管理' },
];

const lifecycleTabs: { key: LifecycleTab; label: string }[] = [
  { key: 'PUBLISHED', label: '已发布' },
  { key: 'OFFLINE', label: '已下架' },
  { key: 'ARCHIVED', label: '已归档' },
];

const lifecycleBadge: Record<CourseLifecycleStatus, { cls: string; text: string }> = {
  DRAFT: { cls: 'badge-amber', text: '草稿' },
  PENDING_REVIEW: { cls: 'badge-indigo', text: '待审核' },
  PUBLISHED: { cls: 'badge-green', text: '已发布' },
  OFFLINE: { cls: 'badge-amber', text: '已下架' },
  ARCHIVED: { cls: 'badge-indigo', text: '已归档' },
};

const levelLabel: Record<string, string> = {
  BEGINNER: '入门',
  INTERMEDIATE: '进阶',
  ADVANCED: '高级',
};

function formatPrice(price: string | null | undefined): string {
  if (price == null || price === '' || Number(price) === 0) return '免费';
  return '¥' + price;
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—';
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

/** 审核操作错误文案：自审 403（COURSE_ACCESS_DENIED）给审核场景专属提示。 */
function auditActionError(e: unknown): string {
  const code = (e as { code?: string } | null)?.code;
  if (code === 'COURSE_ACCESS_DENIED') return '不能审核自己提交的课程';
  return apiErrorText(e);
}

const PAGE_SIZE = 20;

export default function CourseAudit() {
  const [mainTab, setMainTab] = useState<MainTab>('AUDIT');

  // ---- 待审核 -
  const [auditItems, setAuditItems] = useState<CourseAuditItem[]>([]);
  const [auditTotal, setAuditTotal] = useState(0);
  const [auditPage, setAuditPage] = useState(1);
  const [auditLoading, setAuditLoading] = useState(true);
  const [auditError, setAuditError] = useState<string | null>(null);
  const [auditTick, setAuditTick] = useState(0);

  // ---- 审核弹窗 -
  const [selectedAudit, setSelectedAudit] = useState<CourseAuditItem | null>(null);
  const [modalMode, setModalMode] = useState<'idle' | 'approve' | 'reject'>('idle');
  const [rejectReason, setRejectReason] = useState('');
  const [modalSubmitting, setModalSubmitting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);
  const auditModalOpenRef = useRef(false);

  // ---- 课程管理 -
  const [lifecycleTab, setLifecycleTab] = useState<LifecycleTab>('PUBLISHED');
  const [manageCourses, setManageCourses] = useState<AdminCourse[]>([]);
  const [manageTotal, setManageTotal] = useState(0);
  const [managePage, setManagePage] = useState(1);
  const [manageLoading, setManageLoading] = useState(true);
  const [manageError, setManageError] = useState<string | null>(null);
  const [manageTick, setManageTick] = useState(0);

  const loadAudit = useCallback(() => {
    setAuditLoading(true);
    setAuditError(null);
    courseAuditApi
      .listPending({ page: auditPage, pageSize: PAGE_SIZE })
      .then((page) => {
        setAuditItems(page.items);
        setAuditTotal(page.total);
      })
      .catch((e) => setAuditError(apiErrorText(e)))
      .finally(() => setAuditLoading(false));
  }, [auditPage, auditTick]);

  const loadManage = useCallback(() => {
    setManageLoading(true);
    setManageError(null);
    adminCourseApi
      .list({ page: managePage, pageSize: PAGE_SIZE, lifecycleStatus: lifecycleTab })
      .then((page) => {
        setManageCourses(page.items);
        setManageTotal(page.total);
      })
      .catch((e) => setManageError(apiErrorText(e)))
      .finally(() => setManageLoading(false));
  }, [managePage, lifecycleTab, manageTick]);

  useEffect(() => {
    if (mainTab === 'AUDIT') loadAudit();
  }, [mainTab, loadAudit]);

  useEffect(() => {
    if (mainTab === 'MANAGE') loadManage();
  }, [mainTab, loadManage]);

  const closeAuditModal = () => {
    auditModalOpenRef.current = false;
    setSelectedAudit(null);
  };

  const openAuditModal = (item: CourseAuditItem) => {
    auditModalOpenRef.current = true;
    setSelectedAudit(item);
    setModalMode('idle');
    setRejectReason('');
    setModalError(null);
    // 规格审查：打开弹窗时按 auditId 拉取最新详情，避免列表快照过期
    // （如已被并发审批/撤回）导致 approve 409 兜底才暴露；刷新失败静默
    // 保留快照，提交时错误由后端兜底提示。
    courseAuditApi
      .getDetail(item.auditId)
      .then((fresh) => {
        if (auditModalOpenRef.current) setSelectedAudit(fresh);
      })
      .catch(() => {
        // 忽略：快照仍可用。
      });
  };

  const handleApprove = async () => {
    if (!selectedAudit) return;
    setModalSubmitting(true);
    setModalError(null);
    try {
      await courseAuditApi.approve(selectedAudit.auditId);
      closeAuditModal();
      loadAudit();
    } catch (e) {
      setModalError(auditActionError(e));
    } finally {
      setModalSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (!selectedAudit || !rejectReason.trim()) return;
    setModalSubmitting(true);
    setModalError(null);
    try {
      await courseAuditApi.reject(selectedAudit.auditId, rejectReason.trim());
      closeAuditModal();
      loadAudit();
    } catch (e) {
      setModalError(auditActionError(e));
    } finally {
      setModalSubmitting(false);
    }
  };

  const runLifecycle = async (course: AdminCourse, action: 'offline' | 'republish' | 'archive') => {
    const label = action === 'offline' ? '下架' : action === 'republish' ? '重新上架' : '归档';
    const tip =
      action === 'archive'
        ? '归档后课程不可再销售（不可逆），确定归档《' + (course.title ?? '') + '》？'
        : action === 'offline'
          ? '确定下架《' + (course.title ?? '') + '》？下架后学生将无法购买。'
          : '确定重新上架《' + (course.title ?? '') + '》？';
    if (!window.confirm(tip)) return;
    setManageError(null);
    try {
      if (action === 'offline') await courseLifecycleApi.offline(course.courseId);
      if (action === 'republish') await courseLifecycleApi.republish(course.courseId);
      if (action === 'archive') await courseLifecycleApi.archive(course.courseId);
      loadManage();
    } catch (e) {
      setManageError(apiErrorText(e));
    }
  };

  const switchLifecycleTab = (tab: LifecycleTab) => {
    setLifecycleTab(tab);
    setManagePage(1);
  };

  const totalPages = (total: number) => Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="space-y-6">
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">内容审核</div>
        <h1 className="display-heading text-3xl md:text-4xl">课程审核</h1>
        <p className="text-ink-500 mt-2">审核教师提交的课程，并管理课程上下架与归档</p>
      </div>

      {/* 主 Tab */}
      <div className="flex items-center gap-1 border-b border-ink-200 animate-fade-up opacity-0 animation-delay-100">
        {mainTabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setMainTab(t.key)}
            className={cn(
              'px-5 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
              mainTab === t.key
                ? 'border-brand-500 text-brand-500 dark:text-brand-400'
                : 'border-transparent text-ink-500 hover:text-ink-800',
            )}
          >
            {t.label}
            {t.key === 'AUDIT' && auditTotal > 0 && (
              <span className="ml-2 px-1.5 py-0.5 text-xs bg-brand-500/15 text-brand-500 dark:text-brand-400 rounded-md">
                {auditTotal}
              </span>
            )}
          </button>
        ))}
      </div>

      {mainTab === 'AUDIT' && (
        <div className="animate-fade-up opacity-0 animation-delay-200 space-y-4">
          {auditError && (
            <div className="flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 rounded-xl">
              <AlertCircle size={18} className="shrink-0" />
              <span className="flex-1 text-sm">{auditError}</span>
              <button onClick={() => setAuditTick((t) => t + 1)} className="btn-outline py-1.5 px-3">
                <RefreshCw size={14} />
                重试
              </button>
            </div>
          )}
          <div className="card-editorial overflow-hidden">
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>课程（提交版本）</th>
                    <th>价格</th>
                    <th>难度</th>
                    <th>提交人</th>
                    <th>提交时间</th>
                    <th className="text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLoading ? (
                    <tr><td colSpan={6} className="text-center py-12 text-ink-400">加载中…</td></tr>
                  ) : auditItems.length === 0 ? (
                    <tr><td colSpan={6} className="text-center py-12 text-ink-400">暂无待审核课程</td></tr>
                  ) : (
                    auditItems.map((item) => (
                      <tr key={item.auditId}>
                        <td>
                          <div className="min-w-0">
                            <p className="font-medium text-ink-800 line-clamp-1">{item.title ?? '（未命名课程）'}</p>
                            <div className="flex flex-wrap items-center gap-1.5 mt-1">
                              <span className="text-xs text-ink-400">审核单 #{item.auditId} · v{item.versionNo ?? '--'}</span>
                              {(item.changes && item.changes.length > 0
                                ? item.changes
                                : (item.changeSummary ? [item.changeSummary] : ['✨ 课程审核'])
                              ).map((ch, idx) => (
                                <span
                                  key={idx}
                                  className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-brand-500/10 text-brand-600 dark:text-brand-400 border border-brand-500/20 whitespace-nowrap"
                                >
                                  {ch}
                                </span>
                              ))}
                            </div>
                          </div>
                        </td>
                        <td><span className="text-ink-700">{formatPrice(item.price)}</span></td>
                        <td><span className="text-ink-600">{levelLabel[item.level ?? ''] ?? item.level ?? '—'}</span></td>
                        <td><span className="text-ink-600">用户 {item.submittedBy}</span></td>
                        <td><span className="text-ink-500">{formatTime(item.submittedAt)}</span></td>
                        <td>
                          <div className="flex items-center justify-end gap-1">
                            <button onClick={() => openAuditModal(item)} className="btn-primary py-1.5 px-3">
                              <CheckCircle size={14} />
                              审核
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-5 py-3 border-t border-ink-100 text-sm text-ink-500">
              <span>共 {auditTotal} 条待审核</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setAuditPage((p) => Math.max(1, p - 1))}
                  disabled={auditPage <= 1 || auditLoading}
                  className="btn-outline py-1.5 px-3 disabled:opacity-40"
                >
                  上一页
                </button>
                <span>{auditPage} / {totalPages(auditTotal)}</span>
                <button
                  onClick={() => setAuditPage((p) => p + 1)}
                  disabled={auditPage >= totalPages(auditTotal) || auditLoading}
                  className="btn-outline py-1.5 px-3 disabled:opacity-40"
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {mainTab === 'MANAGE' && (
        <div className="animate-fade-up opacity-0 animation-delay-200 space-y-4">
          <div className="flex items-center gap-1 border-b border-ink-200">
            {lifecycleTabs.map((t) => (
              <button
                key={t.key}
                onClick={() => switchLifecycleTab(t.key)}
                className={cn(
                  'px-5 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
                  lifecycleTab === t.key
                    ? 'border-brand-500 text-brand-500 dark:text-brand-400'
                    : 'border-transparent text-ink-500 hover:text-ink-800',
                )}
              >
                {t.label}
              </button>
            ))}
          </div>
          {manageError && (
            <div className="flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 rounded-xl">
              <AlertCircle size={18} className="shrink-0" />
              <span className="flex-1 text-sm">{manageError}</span>
              <button onClick={() => setManageTick((t) => t + 1)} className="btn-outline py-1.5 px-3">
                <RefreshCw size={14} />
                重试
              </button>
            </div>
          )}
          <div className="card-editorial overflow-hidden">
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>课程</th>
                    <th>价格</th>
                    <th>学员数</th>
                    <th>状态</th>
                    <th className="text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {manageLoading ? (
                    <tr><td colSpan={5} className="text-center py-12 text-ink-400">加载中…</td></tr>
                  ) : manageCourses.length === 0 ? (
                    <tr><td colSpan={5} className="text-center py-12 text-ink-400">暂无{lifecycleTabs.find((t) => t.key === lifecycleTab)?.label}课程</td></tr>
                  ) : (
                    manageCourses.map((course) => (
                      <tr key={course.courseId}>
                        <td>
                          <div className="flex items-center gap-3">
                            {course.coverUrl ? (
                              <img
                                src={course.coverUrl}
                                alt={course.title ?? ''}
                                className="w-16 h-12 object-cover flex-shrink-0 bg-ink-100 rounded-md"
                              />
                            ) : (
                              <div className="w-16 h-12 flex-shrink-0 bg-ink-100 rounded-md flex items-center justify-center text-ink-300 text-xs">
                                无封面
                              </div>
                            )}
                            <div className="min-w-0">
                              <p className="font-medium text-ink-800 line-clamp-1">{course.title ?? '（未命名课程）'}</p>
                              <p className="text-xs text-ink-400 mt-0.5">课程 #{course.courseId} · 版本 v{course.versionNo ?? '--'}</p>
                            </div>
                          </div>
                        </td>
                        <td><span className="text-ink-700">{formatPrice(course.price)}</span></td>
                        <td><span className="text-ink-600">{(course.enrollmentCount ?? 0).toLocaleString()}</span></td>
                        <td>
                          <span className={lifecycleBadge[course.lifecycleStatus]?.cls ?? 'badge-amber'}>
                            {lifecycleBadge[course.lifecycleStatus]?.text ?? course.lifecycleStatus}
                          </span>
                        </td>
                        <td>
                          <div className="flex items-center justify-end gap-1">
                            {course.lifecycleStatus === 'PUBLISHED' && (
                              <>
                                <button
                                  onClick={() => void runLifecycle(course, 'offline')}
                                  className="btn-outline py-1.5 px-3 text-amber-600 border-amber-300 hover:border-amber-600"
                                  title="下架（学生将无法购买）"
                                >
                                  <Ban size={14} />
                                  下架
                                </button>
                                <button
                                  disabled
                                  title="归档需先下架"
                                  className="btn-outline py-1.5 px-3 opacity-40 cursor-not-allowed"
                                >
                                  <Archive size={14} />
                                  归档
                                </button>
                              </>
                            )}
                            {course.lifecycleStatus === 'OFFLINE' && (
                              <>
                                <button
                                  onClick={() => void runLifecycle(course, 'republish')}
                                  className="btn-outline py-1.5 px-3 text-green-600 border-green-300 hover:border-green-600"
                                  title="重新上架"
                                >
                                  <RotateCcw size={14} />
                                  重上架
                                </button>
                                <button
                                  onClick={() => void runLifecycle(course, 'archive')}
                                  className="btn-outline py-1.5 px-3 text-red-600 border-red-300 hover:border-red-600"
                                  title="归档（不可逆）"
                                >
                                  <Archive size={14} />
                                  归档
                                </button>
                              </>
                            )}
                            {course.lifecycleStatus === 'ARCHIVED' && (
                              <span className="text-xs text-ink-400">已归档，不可恢复</span>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-5 py-3 border-t border-ink-100 text-sm text-ink-500">
              <span>共 {manageTotal} 门课程</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setManagePage((p) => Math.max(1, p - 1))}
                  disabled={managePage <= 1 || manageLoading}
                  className="btn-outline py-1.5 px-3 disabled:opacity-40"
                >
                  上一页
                </button>
                <span>{managePage} / {totalPages(manageTotal)}</span>
                <button
                  onClick={() => setManagePage((p) => p + 1)}
                  disabled={managePage >= totalPages(manageTotal) || manageLoading}
                  className="btn-outline py-1.5 px-3 disabled:opacity-40"
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 审核弹窗 */}
      {selectedAudit && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/70 backdrop-blur-sm animate-fade-in" onClick={() => !modalSubmitting && closeAuditModal()} />
          <div className="relative bg-surface w-full max-w-2xl max-h-[90vh] overflow-y-auto border border-ink-200 shadow-2xl animate-fade-up rounded-2xl">
            <div className="flex items-center justify-between px-8 py-5 border-b border-ink-100 bg-surface-light">
              <div>
                <div className="section-label mb-1">审核详情</div>
                <h2 className="font-display text-2xl font-bold text-ink-900">{selectedAudit.title ?? '（未命名课程）'}</h2>
              </div>
              <button onClick={() => !modalSubmitting && closeAuditModal()} className="p-2 text-ink-400 hover:text-ink-800 transition-colors">
                <XCircle size={20} />
              </button>
            </div>
            <div className="p-8 space-y-6">
              {modalError && (
                <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 rounded-xl text-sm">
                  <AlertCircle size={15} className="shrink-0" />
                  <span>{modalError}</span>
                </div>
              )}
              {/* 智能变更识别与修改原因 */}
              <div className="rounded-xl border border-brand-500/20 bg-brand-50/40 dark:bg-brand-500/5 p-4 space-y-2">
                <div className="flex items-center gap-2">
                  <span className="px-2 py-0.5 text-xs font-bold bg-brand-500 text-white rounded-md">智能变更识别</span>
                  <span className="text-xs font-medium text-brand-700 dark:text-brand-300">
                    {selectedAudit.changeSummary || '检测到课程信息提交审核'}
                  </span>
                </div>
                {selectedAudit.changes && selectedAudit.changes.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {selectedAudit.changes.map((c, idx) => (
                      <span key={idx} className="text-xs px-2.5 py-1 rounded-lg bg-white dark:bg-ink-800 border border-brand-500/30 font-medium text-brand-800 dark:text-brand-200 shadow-sm">
                        {c}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <p className="text-sm text-ink-500 leading-relaxed whitespace-pre-line">
                {selectedAudit.description || '（无课程简介）'}
              </p>
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-ink-100">
                <DetailRow label="审核单号" value={selectedAudit.auditId} />
                <DetailRow label="版本号" value={'v' + (selectedAudit.versionNo ?? '--')} />
                <DetailRow label="课程价格" value={formatPrice(selectedAudit.price)} />
                <DetailRow label="难度" value={levelLabel[selectedAudit.level ?? ''] ?? selectedAudit.level ?? '—'} />
                <DetailRow label="提交人" value={'用户 ' + selectedAudit.submittedBy} />
                <DetailRow label="提交时间" value={formatTime(selectedAudit.submittedAt)} />
              </div>
              {modalMode === 'reject' && (
                <div>
                  <label className="block text-sm font-medium text-ink-700 mb-2">
                    驳回原因 <span className="text-red-500">*</span>
                  </label>
                  <textarea
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    rows={4}
                    placeholder="请详细说明驳回原因，以便提交者修改…"
                    className="input-field resize-none"
                    autoFocus
                  />
                </div>
              )}
            </div>
            <div className="flex items-center justify-end gap-3 px-8 py-5 border-t border-ink-100 bg-surface-light">
              {modalMode === 'idle' ? (
                <>
                  <button
                    onClick={() => setModalMode('reject')}
                    disabled={modalSubmitting}
                    className="btn-outline border-red-500/30 text-red-600 dark:text-red-400 hover:border-red-500/60 hover:text-red-700 dark:hover:text-red-300"
                  >
                    <XCircle size={16} />
                    驳回
                  </button>
                  <button
                    onClick={() => setModalMode('approve')}
                    disabled={modalSubmitting}
                    className="btn-primary bg-green-600 hover:bg-green-700"
                  >
                    <CheckCircle size={16} />
                    通过
                  </button>
                </>
              ) : (
                <>
                  <button
                    onClick={() => {
                      setModalMode('idle');
                      setRejectReason('');
                    }}
                    className="btn-ghost"
                  >
                    返回
                  </button>
                  <button
                    onClick={modalMode === 'approve' ? handleApprove : handleReject}
                    disabled={modalSubmitting || (modalMode === 'reject' && !rejectReason.trim())}
                    className={cn(
                      'btn-primary',
                      modalMode === 'reject' && 'bg-red-600 hover:bg-red-700',
                      modalMode === 'approve' && 'bg-green-600 hover:bg-green-700',
                      (modalSubmitting || (modalMode === 'reject' && !rejectReason.trim())) && 'opacity-50 cursor-not-allowed',
                    )}
                  >
                    {modalSubmitting
                      ? '提交中…'
                      : modalMode === 'reject' ? '确认驳回' : '确认通过（发布）'}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-widest text-ink-400 mb-1">{label}</div>
      <div className="text-sm font-medium text-ink-800 break-all">{value}</div>
    </div>
  );
}
