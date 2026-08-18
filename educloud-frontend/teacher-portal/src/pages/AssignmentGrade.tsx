import { useEffect, useState } from 'react';
import { ClipboardCheck, Clock, CheckCircle2, AlertCircle } from 'lucide-react';
import { api } from '../services/api';
import GradeSheet from '../components/GradeSheet';
import type { Assignment } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

export default function AssignmentGrade() {
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [selectedId, setSelectedId] = useState<string>('');
  const [selectedSubmissionIds, setSelectedSubmissionIds] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getAssignments().then((data) => {
      setAssignments(data);
      if (data.length > 0) setSelectedId(data[0].id);
      setLoading(false);
    });
  }, []);

  const selected = assignments.find((a) => a.id === selectedId);
  const rememberedSubmissionId = selected ? selectedSubmissionIds[selected.id] : undefined;
  const selectedSubmissionId = selected
    ? selected.submissions.some((submission) => submission.id === rememberedSubmissionId)
      ? rememberedSubmissionId ?? ''
      : selected.submissions[0]?.id ?? ''
    : '';

  const handleGrade = async (submissionId: string, score: number, feedback: string) => {
    await api.gradeSubmission(submissionId, score, feedback);
    // Refresh
    const updated = await api.getAssignments();
    setAssignments(updated);
  };

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div>
        <p className="section-label mb-2">作业批改</p>
        <h1 className="display-heading text-3xl md:text-4xl">作业批改</h1>
        <p className="text-ink-500 mt-2 text-sm">批阅学员作业，给出评分与反馈</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Assignment list */}
        <div className="lg:col-span-1">
          <div className="card-editorial p-4 sticky top-24">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-400 mb-3">
              作业列表
            </h3>
            <div className="space-y-2">
              {loading ? (
                <p className="text-sm text-ink-400 py-4 text-center">加载中…</p>
              ) : (
                assignments.map((a) => {
                  const pending = a.submissionCount - a.gradedCount;
                  const isActive = selectedId === a.id;
                  return (
                    <button
                      key={a.id}
                      onClick={() => setSelectedId(a.id)}
                      className={cn(
                        'w-full text-left p-3 border transition-all',
                        isActive
                          ? 'border-indigo-800 bg-indigo-50/50'
                          : 'border-ink-100 hover:border-ink-300 bg-white'
                      )}
                    >
                      <p className={cn(
                        'text-sm font-medium line-clamp-2',
                        isActive ? 'text-indigo-800' : 'text-ink-800'
                      )}>
                        {a.title}
                      </p>
                      <p className="text-xs text-ink-400 mt-1">{a.courseName}</p>
                      <div className="flex items-center gap-3 mt-2 text-xs">
                        <span className="flex items-center gap-1 text-ink-500">
                          <ClipboardCheck className="w-3 h-3" />
                          {a.submissionCount}
                        </span>
                        {pending > 0 ? (
                          <span className="flex items-center gap-1 text-amber-600">
                            <AlertCircle className="w-3 h-3" />
                            {pending} 待批
                          </span>
                        ) : (
                          <span className="flex items-center gap-1 text-green-600">
                            <CheckCircle2 className="w-3 h-3" />
                            已批完
                          </span>
                        )}
                        <span className="flex items-center gap-1 text-ink-400 ml-auto">
                          <Clock className="w-3 h-3" />
                          {dayjs(a.dueDate).format('MM-DD')}
                        </span>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </div>

        {/* Grade sheet */}
        <div className="lg:col-span-3">
          {selected ? (
            <div className="space-y-4">
              {/* Assignment header */}
              <div className="card-editorial p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-xs text-amber-600 font-medium uppercase tracking-wider mb-1">
                      {selected.courseName}
                    </p>
                    <h2 className="font-display text-xl font-semibold text-ink-900">
                      {selected.title}
                    </h2>
                    <p className="text-sm text-ink-500 mt-1 max-w-2xl">{selected.description}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-ink-400">截止时间</p>
                    <p className="font-medium text-ink-700">
                      {dayjs(selected.dueDate).format('YYYY-MM-DD HH:mm')}
                    </p>
                  </div>
                </div>
                <div className="flex gap-6 mt-4 pt-4 border-t border-ink-100">
                  <div>
                    <p className="text-2xl font-display font-bold text-ink-900">
                      {selected.submissionCount}
                    </p>
                    <p className="text-xs text-ink-400">已提交</p>
                  </div>
                  <div>
                    <p className="text-2xl font-display font-bold text-green-600">
                      {selected.gradedCount}
                    </p>
                    <p className="text-xs text-ink-400">已批改</p>
                  </div>
                  <div>
                    <p className="text-2xl font-display font-bold text-amber-600">
                      {selected.submissionCount - selected.gradedCount}
                    </p>
                    <p className="text-xs text-ink-400">待批改</p>
                  </div>
                  <div>
                    <p className="text-2xl font-display font-bold text-indigo-800">
                      {selected.totalScore}
                    </p>
                    <p className="text-xs text-ink-400">满分</p>
                  </div>
                </div>
              </div>

              {/* Grade sheet */}
              <GradeSheet
                submissions={selected.submissions}
                totalScore={selected.totalScore}
                selectedSubmissionId={selectedSubmissionId}
                onSelectSubmission={(submissionId) =>
                  setSelectedSubmissionIds((current) => ({
                    ...current,
                    [selected.id]: submissionId,
                  }))
                }
                onGrade={handleGrade}
              />
            </div>
          ) : (
            <div className="card-editorial p-16 text-center text-ink-400">
              请从左侧选择一份作业
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
