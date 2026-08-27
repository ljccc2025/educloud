import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText, Clock, CheckCircle2, AlertCircle, Sparkles } from 'lucide-react';
import { studentAssignmentService } from '@/services/studentAssignmentService';
import AssignmentSubmitModal from '@/components/assignments/AssignmentSubmitModal';
import AssignmentViewModal from '@/components/assignments/AssignmentViewModal';
import type { Assignment, AssignmentStatus } from '@/types';
import { cn } from '@/utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<AssignmentStatus, { label: string; className: string; icon: typeof Clock }> = {
  PENDING: { label: '待提交', className: 'badge-amber', icon: Clock },
  SUBMITTED: { label: '已提交', className: 'badge-indigo', icon: CheckCircle2 },
  GRADED: { label: '已批改', className: 'badge-green', icon: AlertCircle },
  OVERDUE: { label: '已逾期', className: 'badge-red', icon: AlertCircle },
};

export default function Assignments() {
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<AssignmentStatus | 'all'>('all');
  const [activeSubmitAssignment, setActiveSubmitAssignment] = useState<Assignment | null>(null);
  const [activeViewAssignment, setActiveViewAssignment] = useState<Assignment | null>(null);

  const loadAssignments = async () => {
    try {
      const data = await studentAssignmentService.getAssignments();
      setAssignments(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadAssignments();
  }, []);

  const handleOpenSubmit = (a: Assignment) => {
    setActiveSubmitAssignment(a);
  };

  const handleOpenView = (a: Assignment) => {
    setActiveViewAssignment(a);
  };

  const handleSubmittedSuccess = (updated: Assignment) => {
    setAssignments((prev) =>
      prev.map((item) => (String(item.id) === String(updated.id) ? updated : item))
    );
  };

  const filtered = assignments.filter((a) => filter === 'all' || a.status === filter);

  const counts = {
    all: assignments.length,
    PENDING: assignments.filter((a) => a.status === 'PENDING').length,
    SUBMITTED: assignments.filter((a) => a.status === 'SUBMITTED').length,
    GRADED: assignments.filter((a) => a.status === 'GRADED').length,
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <span className="section-label mb-3">作业中心</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">我的作业</h1>
        <p className="text-ink-500 mt-3">按时完成作业，巩固所学知识</p>
      </div>

      {/* Filter Tabs */}
      <div className="flex flex-wrap gap-3 mb-8">
        {([
          { value: 'all', label: '全部' },
          { value: 'PENDING', label: '待提交' },
          { value: 'SUBMITTED', label: '已提交' },
          { value: 'GRADED', label: '已批改' },
        ] as const).map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setFilter(tab.value)}
            className={cn(
              'px-5 py-3 text-sm font-medium transition-colors border rounded-xl cursor-pointer',
              filter === tab.value
                ? 'bg-indigo-800 text-white border-indigo-800 shadow-sm'
                : 'bg-white text-ink-600 border-ink-200 hover:border-indigo-800 hover:text-indigo-800'
            )}
          >
            {tab.label}
            <span
              className={cn(
                'ml-2 text-xs',
                filter === tab.value ? 'text-white/70' : 'text-ink-400'
              )}
            >
              {counts[tab.value]}
            </span>
          </button>
        ))}
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
                <tr className="border-b border-ink-100 bg-slate-50/50">
                  <th className="py-4 px-4 text-left">作业名称</th>
                  <th className="py-4 px-4 text-left">所属课程</th>
                  <th className="py-4 px-4 text-center">截止时间</th>
                  <th className="py-4 px-4 text-center">状态</th>
                  <th className="py-4 px-4 text-center">得分</th>
                  <th className="py-4 px-4 text-center w-32 whitespace-nowrap">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((assignment) => {
                  const config = statusConfig[assignment.status];
                  const StatusIcon = config.icon;
                  const isOverdue =
                    (assignment.status === 'PENDING' || assignment.status === 'OVERDUE') &&
                    dayjs(assignment.dueDate).isBefore(dayjs());
                  return (
                    <tr key={assignment.id} className="hover:bg-slate-50/60 transition-colors">
                      <td className="py-4 px-4 align-middle">
                        <div className="flex items-center gap-3">
                          <FileText size={18} className="text-ink-300 flex-shrink-0" />
                          <div>
                            <p className="font-medium text-ink-900">{assignment.title}</p>
                            <p className="text-xs text-ink-400 mt-0.5 max-w-xs truncate">
                              {assignment.description}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="py-4 px-4 text-left align-middle">
                        <span className="font-medium text-ink-800">
                          {assignment.courseTitle}
                        </span>
                      </td>
                      <td className="py-4 px-4 text-center align-middle whitespace-nowrap">
                        <span
                          className={cn(
                            'text-sm',
                            isOverdue ? 'text-red-600 font-medium' : 'text-ink-600'
                          )}
                        >
                          {dayjs(assignment.dueDate).isValid()
                            ? dayjs(assignment.dueDate).format('YYYY-MM-DD HH:mm')
                            : assignment.dueDate}
                          {isOverdue && ' (已逾期)'}
                        </span>
                      </td>
                      <td className="py-4 px-4 text-center align-middle whitespace-nowrap">
                        <span className={cn(config.className, 'inline-flex items-center gap-1')}>
                          <StatusIcon size={12} />
                          {config.label}
                        </span>
                      </td>
                      <td className="py-4 px-4 text-center align-middle whitespace-nowrap">
                        {assignment.score !== undefined ? (
                          <span className="font-bold text-emerald-600">
                            {assignment.score}
                            <span className="text-ink-400 font-normal">/{assignment.totalScore}</span>
                          </span>
                        ) : (
                          <span className="text-ink-400">/{assignment.totalScore}</span>
                        )}
                      </td>
                      <td className="py-4 px-4 text-center align-middle whitespace-nowrap w-32">
                        {assignment.status === 'PENDING' || assignment.status === 'OVERDUE' ? (
                          <button
                            type="button"
                            onClick={() => handleOpenSubmit(assignment)}
                            className="btn-primary !px-3.5 !py-1.5 text-xs whitespace-nowrap min-w-[84px] inline-flex items-center justify-center cursor-pointer"
                          >
                            去提交
                          </button>
                        ) : assignment.status === 'SUBMITTED' ? (
                          <button
                            type="button"
                            onClick={() => handleOpenView(assignment)}
                            className="btn-outline !px-3.5 !py-1.5 text-xs whitespace-nowrap min-w-[84px] inline-flex items-center justify-center cursor-pointer"
                          >
                            查看提交
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleOpenView(assignment)}
                            className="btn-outline !px-3.5 !py-1.5 text-xs whitespace-nowrap min-w-[84px] inline-flex items-center justify-center cursor-pointer"
                          >
                            查看批改
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {filtered.length === 0 && (
            <div className="text-center py-16">
              <FileText size={40} className="mx-auto text-ink-200 mb-3" strokeWidth={1} />
              <p className="text-ink-400">暂无相关作业</p>
            </div>
          )}
        </div>
      )}

      {/* Assignment Submit Modal */}
      {activeSubmitAssignment && (
        <AssignmentSubmitModal
          assignment={activeSubmitAssignment}
          isOpen={Boolean(activeSubmitAssignment)}
          onClose={() => setActiveSubmitAssignment(null)}
          onSubmitSuccess={handleSubmittedSuccess}
          onSubmitService={studentAssignmentService.submitAssignment}
        />
      )}

      {/* Assignment View Modal */}
      {activeViewAssignment && (
        <AssignmentViewModal
          assignment={activeViewAssignment}
          isOpen={Boolean(activeViewAssignment)}
          onClose={() => setActiveViewAssignment(null)}
          onResubmit={(a) => {
            setActiveViewAssignment(null);
            setActiveSubmitAssignment(a);
          }}
        />
      )}
    </div>
  );
}
