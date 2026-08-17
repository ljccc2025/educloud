import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText, Clock, CheckCircle2, AlertCircle } from 'lucide-react';
import { assignmentApi } from '@/services/api';
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

  useEffect(() => {
    assignmentApi.getAll().then((data) => {
      setAssignments(data);
      setLoading(false);
    });
  }, []);

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
              'px-5 py-3 text-sm font-medium transition-colors border',
              filter === tab.value
                ? 'bg-indigo-800 text-white border-indigo-800'
                : 'bg-white text-ink-600 border-ink-200 hover:border-indigo-800 hover:text-indigo-800'
            )}
          >
            {tab.label}
            <span className={cn(
              'ml-2 text-xs',
              filter === tab.value ? 'text-white/70' : 'text-ink-400'
            )}>
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
                <tr>
                  <th>作业名称</th>
                  <th>所属课程</th>
                  <th>截止时间</th>
                  <th>状态</th>
                  <th>得分</th>
                  <th className="text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((assignment) => {
                  const config = statusConfig[assignment.status];
                  const StatusIcon = config.icon;
                  const isOverdue = (assignment.status === 'PENDING' || assignment.status === 'OVERDUE') &&
                    dayjs(assignment.dueDate).isBefore(dayjs());
                  return (
                    <tr key={assignment.id}>
                      <td>
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
                      <td>
                        <Link
                          to={`/courses/${assignment.courseId}`}
                          className="text-indigo-800 link-underline"
                        >
                          {assignment.courseTitle}
                        </Link>
                      </td>
                      <td>
                        <span className={cn(
                          'text-sm',
                          isOverdue ? 'text-red-600 font-medium' : 'text-ink-600'
                        )}>
                          {assignment.dueDate}
                          {isOverdue && ' (已逾期)'}
                        </span>
                      </td>
                      <td>
                        <span className={config.className}>
                          <StatusIcon size={12} />
                          {config.label}
                        </span>
                      </td>
                      <td>
                        {assignment.score !== undefined ? (
                          <span className="font-semibold text-ink-900">
                            {assignment.score}
                            <span className="text-ink-400 font-normal">/{assignment.totalScore}</span>
                          </span>
                        ) : (
                          <span className="text-ink-300">--</span>
                        )}
                      </td>
                      <td className="text-right">
                        {assignment.status === 'PENDING' || assignment.status === 'OVERDUE' ? (
                          <button type="button" className="btn-primary !px-4 !py-2 text-xs">
                            去提交
                          </button>
                        ) : assignment.status === 'SUBMITTED' ? (
                          <button type="button" className="btn-outline !px-4 !py-2 text-xs">
                            查看提交
                          </button>
                        ) : (
                          <button type="button" className="btn-outline !px-4 !py-2 text-xs">
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
    </div>
  );
}
