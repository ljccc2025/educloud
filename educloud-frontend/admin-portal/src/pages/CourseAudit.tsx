import { useEffect, useState } from 'react';
import { CheckCircle, XCircle, Clock, Eye } from 'lucide-react';
import AuditModal from '../components/AuditModal';
import { courseApi } from '../services/api';
import type { Course, CourseStatus } from '../types';
import { cn } from '../utils/cn';

type Tab = 'PENDING' | 'APPROVED' | 'REJECTED';

const tabConfig: { key: Tab; label: string }[] = [
  { key: 'PENDING', label: '待审核' },
  { key: 'APPROVED', label: '已通过' },
  { key: 'REJECTED', label: '已驳回' },
];

const statusBadge: Record<CourseStatus, { cls: string; text: string }> = {
  PENDING: { cls: 'badge-amber', text: '待审核' },
  APPROVED: { cls: 'badge-green', text: '已通过' },
  REJECTED: { cls: 'badge-red', text: '已驳回' },
};

export default function CourseAudit() {
  const [tab, setTab] = useState<Tab>('PENDING');
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Course | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const load = (status: Tab) => {
    setLoading(true);
    courseApi.getCourses(status).then((data) => {
      setCourses(data);
      setLoading(false);
    });
  };

  useEffect(() => {
    load(tab);
  }, [tab]);

  const openAudit = (course: Course) => {
    setSelected(course);
    setModalOpen(true);
  };

  const handleApprove = async () => {
    if (selected) {
      await courseApi.audit(selected.id, true);
      setModalOpen(false);
      load(tab);
    }
  };

  const handleReject = async (reason: string) => {
    if (selected) {
      await courseApi.audit(selected.id, false, reason);
      setModalOpen(false);
      load(tab);
    }
  };

  const pendingCount = courses.length;

  return (
    <div className="space-y-6">
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">内容审核</div>
        <h1 className="display-heading text-3xl md:text-4xl">课程审核</h1>
        <p className="text-ink-500 mt-2">审核教师提交的课程，确保内容质量与合规性</p>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-ink-200 animate-fade-up opacity-0 animation-delay-100">
        {tabConfig.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'px-5 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
              tab === t.key
                ? 'border-amber-600 text-indigo-800'
                : 'border-transparent text-ink-500 hover:text-ink-800',
            )}
          >
            {t.label}
            {t.key === 'PENDING' && (
              <span className="ml-2 px-1.5 py-0.5 text-xs bg-amber-100 text-amber-700">
                {pendingCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Course list */}
      {loading ? (
        <div className="text-center py-16 text-ink-400">加载中...</div>
      ) : courses.length === 0 ? (
        <div className="text-center py-16 text-ink-400 card-editorial">
          暂无{tabConfig.find((t) => t.key === tab)?.label}课程
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5 animate-fade-up opacity-0 animation-delay-200">
          {courses.map((course) => (
            <div key={course.id} className="card-editorial overflow-hidden flex flex-col">
              <div className="aspect-[16/9] bg-ink-100 overflow-hidden relative">
                <img
                  src={course.cover}
                  alt={course.title}
                  className="w-full h-full object-cover"
                />
                <div className="absolute top-3 left-3">
                  <span className={statusBadge[course.status].cls}>
                    {statusBadge[course.status].text}
                  </span>
                </div>
                {course.price === 0 && (
                  <div className="absolute top-3 right-3">
                    <span className="badge bg-green-700 text-paper border-green-700">免费</span>
                  </div>
                )}
              </div>
              <div className="p-5 flex flex-col flex-1">
                <h3 className="font-display text-lg font-700 text-ink-900 mb-2 line-clamp-2 leading-snug">
                  {course.title}
                </h3>
                <p className="text-sm text-ink-500 line-clamp-2 mb-4 flex-1">
                  {course.description}
                </p>
                <div className="flex items-center justify-between text-sm text-ink-500 mb-4 pt-4 border-t border-ink-50">
                  <span>{course.teacherName}</span>
                  <span className="text-ink-400">{course.submittedDate}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="badge-indigo">{course.category}</span>
                  <span className="font-display text-lg font-700 text-ink-900">
                    {course.price === 0 ? '免费' : `¥${course.price}`}
                  </span>
                </div>
                <div className="flex gap-2 mt-4 pt-4 border-t border-ink-50">
                  {course.status === 'PENDING' ? (
                    <>
                      <button
                        onClick={() => openAudit(course)}
                        className="btn-primary flex-1 py-2.5"
                      >
                        <CheckCircle size={15} />
                        审核
                      </button>
                      <button
                        onClick={() => openAudit(course)}
                        className="btn-outline px-3 py-2.5 text-red-600 border-red-200 hover:border-red-600 hover:text-red-600"
                        title="驳回"
                      >
                        <XCircle size={15} />
                      </button>
                    </>
                  ) : (
                    <button onClick={() => openAudit(course)} className="btn-outline w-full py-2.5">
                      <Eye size={15} />
                      查看详情
                    </button>
                  )}
                </div>
                {course.status === 'REJECTED' && course.rejectReason && (
                  <div className="mt-3 p-3 bg-red-50 border border-red-100 text-xs text-red-700 flex gap-2">
                    <XCircle size={14} className="shrink-0 mt-0.5" />
                    <span>{course.rejectReason}</span>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <AuditModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onApprove={handleApprove}
        onReject={handleReject}
        title={selected?.title ?? ''}
        item={selected}
      />
    </div>
  );
}
