import { useState } from 'react';
import { Save, CheckCircle2, AlertCircle, Loader2, Search, X, MessageSquareQuote, Paperclip, FileText, ExternalLink } from 'lucide-react';
import type { Submission } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

interface GradeSheetProps {
  submissions: Submission[];
  totalScore: number;
  selectedSubmissionId: string;
  onSelectSubmission: (submissionId: string) => void;
  onGrade: (submissionId: string, score: number, feedback: string) => Promise<void> | void;
}

export default function GradeSheet({
  submissions,
  totalScore,
  selectedSubmissionId,
  onSelectSubmission,
  onGrade,
}: GradeSheetProps) {
  const [scores, setScores] = useState<Record<string, string>>({});
  const [feedbacks, setFeedbacks] = useState<Record<string, string>>({});
  const [savedId, setSavedId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'PENDING' | 'GRADED'>('ALL');
  const [studentSearch, setStudentSearch] = useState('');

  const pendingCount = submissions.filter((s) => s.status !== 'GRADED').length;
  const gradedCount = submissions.filter((s) => s.status === 'GRADED').length;

  const filteredSubmissions = submissions.filter((sub) => {
    if (filterStatus === 'PENDING' && sub.status === 'GRADED') return false;
    if (filterStatus === 'GRADED' && sub.status !== 'GRADED') return false;
    if (studentSearch.trim()) {
      const q = studentSearch.toLowerCase().trim();
      return sub.studentName && sub.studentName.toLowerCase().includes(q);
    }
    return true;
  });

  const selected = submissions.find((submission) => submission.id === selectedSubmissionId);

  const handleSave = async (sub: Submission) => {
    const rawScore = scores[sub.id] !== undefined
      ? scores[sub.id]
      : (sub.score !== undefined ? String(sub.score) : '');

    if (!rawScore || rawScore.trim() === '') {
      setFormError('请输入有效的分数后再保存');
      return;
    }

    const numScore = Number(rawScore);
    if (isNaN(numScore) || numScore < 0 || numScore > totalScore) {
      setFormError(`评分必须为 0 至 ${totalScore} 之间的有效数值`);
      return;
    }

    const feedback = feedbacks[sub.id] ?? sub.feedback ?? '';
    setFormError(null);
    setSubmitting(true);

    try {
      await onGrade(sub.id, numScore, feedback);
      setSavedId(sub.id);
      setTimeout(() => setSavedId(null), 2500);
    } catch {
      setFormError('评分保存失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
      {/* Submission list */}
      <div className="lg:col-span-2 flex flex-col max-h-[calc(100vh-210px)] min-h-[460px]">
        <div className="flex items-center justify-between gap-2 mb-2">
          <h4 className="text-xs font-semibold text-ink-500 uppercase tracking-wider">
            提交列表
          </h4>
          <span className="text-xs text-ink-400">共 {submissions.length} 份</span>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-1 p-1 bg-slate-100/90 rounded-lg mb-2 text-xs">
          <button
            type="button"
            onClick={() => setFilterStatus('ALL')}
            className={cn(
              'flex-1 py-1 px-1.5 rounded-md font-medium transition-all text-center text-xs',
              filterStatus === 'ALL'
                ? 'bg-white text-indigo-900 shadow-xs font-semibold'
                : 'text-ink-500 hover:text-ink-800'
            )}
          >
            全部 ({submissions.length})
          </button>
          <button
            type="button"
            onClick={() => setFilterStatus('PENDING')}
            className={cn(
              'flex-1 py-1 px-1.5 rounded-md font-medium transition-all text-center text-xs',
              filterStatus === 'PENDING'
                ? 'bg-white text-amber-700 shadow-xs font-semibold'
                : 'text-ink-500 hover:text-amber-700'
            )}
          >
            待批 ({pendingCount})
          </button>
          <button
            type="button"
            onClick={() => setFilterStatus('GRADED')}
            className={cn(
              'flex-1 py-1 px-1.5 rounded-md font-medium transition-all text-center text-xs',
              filterStatus === 'GRADED'
                ? 'bg-white text-green-700 shadow-xs font-semibold'
                : 'text-ink-500 hover:text-green-700'
            )}
          >
            已评 ({gradedCount})
          </button>
        </div>

        {/* Search student if submissions > 3 */}
        {submissions.length > 3 && (
          <div className="relative mb-2">
            <Search className="w-3.5 h-3.5 text-ink-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={studentSearch}
              onChange={(e) => setStudentSearch(e.target.value)}
              placeholder="搜索学员姓名..."
              className="w-full pl-8 pr-7 py-1.5 text-xs bg-slate-50 border border-ink-200 rounded-lg focus:outline-none focus:border-indigo-600 focus:bg-white transition-colors"
            />
            {studentSearch && (
              <button
                type="button"
                onClick={() => setStudentSearch('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-700"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        )}

        <div className="flex-1 overflow-y-auto pr-1 space-y-2 custom-scrollbar min-h-0">
          {filteredSubmissions.length === 0 ? (
            <p className="text-xs text-ink-400 py-8 text-center bg-white border border-ink-100 rounded-xl">
              暂无匹配的学员提交
            </p>
          ) : (
            filteredSubmissions.map((sub) => (
              <button
                key={sub.id}
                onClick={() => {
                  onSelectSubmission(sub.id);
                  setFormError(null);
                }}
                className={cn(
                  'w-full flex items-center gap-3 p-3 border text-left transition-all rounded-xl',
                  selectedSubmissionId === sub.id
                    ? 'border-indigo-800 bg-indigo-50/60 shadow-xs'
                    : 'border-ink-100 bg-white hover:border-ink-300'
                )}
              >
                <img
                  src={sub.studentAvatar || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(sub.studentName)}&backgroundColor=1e1b4b,d97706,4f46e5&textColor=ffffff&fontWeight=500&fontSize=24`}
                  alt={sub.studentName}
                  className="w-9 h-9 rounded-full bg-ink-100 shrink-0 object-cover"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(sub.studentName)}&backgroundColor=1e1b4b,d97706,4f46e5&textColor=ffffff&fontWeight=500&fontSize=24`;
                  }}
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-ink-800 truncate">{sub.studentName}</p>
                  <p className="text-xs text-ink-400">
                    {dayjs(sub.submittedAt).format('MM-DD HH:mm')}
                  </p>
                </div>
                {sub.status === 'GRADED' ? (
                  <span className="badge-green text-xs px-2 py-0.5 shrink-0">
                    <CheckCircle2 className="w-3 h-3" />
                    {sub.score}
                  </span>
                ) : (
                  <span className="badge-amber text-xs px-2 py-0.5 shrink-0">待批</span>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Grade detail */}
      <div className="lg:col-span-3">
        {selected ? (
          <div className="card-editorial p-6 space-y-5 max-h-[calc(100vh-210px)] min-h-[460px] overflow-y-auto pr-2 custom-scrollbar">
            {/* Student info */}
            <div className="flex items-center gap-4 pb-4 border-b border-ink-100">
              <img
                src={selected.studentAvatar || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(selected.studentName)}&backgroundColor=1e1b4b,d97706,4f46e5&textColor=ffffff&fontWeight=500&fontSize=24`}
                alt={selected.studentName}
                className="w-14 h-14 rounded-full bg-ink-100 object-cover"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(selected.studentName)}&backgroundColor=1e1b4b,d97706,4f46e5&textColor=ffffff&fontWeight=500&fontSize=24`;
                }}
              />
              <div>
                <h3 className="font-display text-xl font-semibold text-ink-900">
                  {selected.studentName}
                </h3>
                <p className="text-sm text-ink-400">
                  提交时间：{dayjs(selected.submittedAt).format('YYYY年MM月DD日 HH:mm')}
                </p>
              </div>
              {selected.status === 'GRADED' && (
                <span className="ml-auto badge-green text-sm px-3 py-1">
                  已评分 {selected.score} / {totalScore}
                </span>
              )}
            </div>

            {/* Student Note (向授课教师留言) */}
            {selected.note && selected.note.trim() && (
              <div className="bg-amber-50/70 border border-amber-200/80 rounded-2xl p-4 space-y-2 animate-fade-in shadow-2xs">
                <div className="flex items-center gap-2 text-amber-800 font-semibold text-xs tracking-wide">
                  <MessageSquareQuote className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>学员留言 / 向授课教师留言</span>
                </div>
                <div className="text-sm text-amber-950 leading-relaxed whitespace-pre-wrap pl-6 bg-white/70 p-3 rounded-xl border border-amber-100/70">
                  {selected.note}
                </div>
              </div>
            )}

            {/* Submission content */}
            <div>
              <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
                作业内容
              </label>
              <div className="bg-ink-50/50 border border-ink-100 p-4 text-sm text-ink-700 leading-relaxed whitespace-pre-wrap rounded-xl">
                {selected.content}
              </div>
            </div>

            {/* Submission Files (作业附件) */}
            {selected.files && selected.files.length > 0 && (
              <div>
                <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <Paperclip className="w-3.5 h-3.5 text-ink-400" />
                  <span>提交附件 ({selected.files.length})</span>
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {selected.files.map((file, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-3 bg-white border border-ink-100 rounded-xl hover:border-indigo-200 transition-colors shadow-2xs text-xs"
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <FileText className="w-4 h-4 text-indigo-600 shrink-0" />
                        <span className="font-medium text-ink-800 truncate" title={file.name}>
                          {file.name}
                        </span>
                      </div>
                      {file.url ? (
                        <a
                          href={file.url}
                          target="_blank"
                          rel="noreferrer"
                          className="text-indigo-600 hover:text-indigo-800 ml-2 shrink-0 flex items-center gap-1"
                        >
                          下载
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      ) : (
                        <span className="text-ink-400 text-3xs shrink-0">
                          {file.size ? `${(file.size / 1024).toFixed(1)} KB` : '已上传'}
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Score */}
            <div>
              <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
                评分（满分 {totalScore} 分）
              </label>
              <div className="flex items-center gap-3">
                <input
                  type="number"
                  min="0"
                  max={totalScore}
                  value={scores[selected.id] ?? (selected.score !== undefined ? String(selected.score) : '')}
                  onChange={(e) => {
                    setScores((prev) => ({ ...prev, [selected.id]: e.target.value }));
                    if (formError) setFormError(null);
                  }}
                  className="input-field w-32 text-center text-2xl font-display font-semibold"
                  placeholder="--"
                />
                <span className="text-ink-400 text-lg">/ {totalScore}</span>
              </div>
            </div>

            {/* Feedback */}
            <div>
              <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
                批改评语
              </label>
              <textarea
                value={feedbacks[selected.id] ?? selected.feedback ?? ''}
                onChange={(e) =>
                  setFeedbacks((prev) => ({ ...prev, [selected.id]: e.target.value }))
                }
                rows={4}
                placeholder="请输入批改评语与改进建议……"
                className="input-field resize-none"
              />
            </div>

            {formError && (
              <div className="p-3 bg-red-50 text-red-600 rounded-xl text-xs flex items-center gap-2 border border-red-100">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            {/* Save */}
            <div className="flex items-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => void handleSave(selected)}
                disabled={submitting}
                className="btn-primary flex items-center gap-1.5 disabled:opacity-50"
              >
                {submitting ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                {savedId === selected.id ? '已保存' : '保存评分'}
              </button>
              {savedId === selected.id && (
                <span className="text-sm text-green-600 flex items-center gap-1 animate-fade-in">
                  <CheckCircle2 className="w-4 h-4" />
                  评分提交成功
                </span>
              )}
            </div>
          </div>
        ) : (
          <div className="card-editorial p-12 text-center text-ink-400">
            请从左侧选择一份提交进行批改
          </div>
        )}
      </div>
    </div>
  );
}
