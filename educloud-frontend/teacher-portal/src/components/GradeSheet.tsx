import { useState } from 'react';
import { Save, CheckCircle2 } from 'lucide-react';
import type { Submission } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

interface GradeSheetProps {
  submissions: Submission[];
  totalScore: number;
  onGrade: (submissionId: string, score: number, feedback: string) => void;
}

export default function GradeSheet({ submissions, totalScore, onGrade }: GradeSheetProps) {
  const [selectedId, setSelectedId] = useState<string>(submissions[0]?.id ?? '');
  const [scores, setScores] = useState<Record<string, string>>({});
  const [feedbacks, setFeedbacks] = useState<Record<string, string>>({});
  const [savedId, setSavedId] = useState<string | null>(null);

  const selected = submissions.find((s) => s.id === selectedId);

  const handleSave = (sub: Submission) => {
    const score = Number(scores[sub.id] ?? sub.score ?? '');
    const feedback = feedbacks[sub.id] ?? sub.feedback ?? '';
    if (score >= 0 && score <= totalScore) {
      onGrade(sub.id, score, feedback);
      setSavedId(sub.id);
      setTimeout(() => setSavedId(null), 2000);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
      {/* Submission list */}
      <div className="lg:col-span-2 space-y-2">
        <div className="flex items-center justify-between mb-3">
          <h4 className="text-sm font-semibold text-ink-700 uppercase tracking-wider">
            提交列表
          </h4>
          <span className="text-xs text-ink-400">{submissions.length} 份</span>
        </div>
        <div className="space-y-2 max-h-[600px] overflow-y-auto">
          {submissions.map((sub) => (
            <button
              key={sub.id}
              onClick={() => setSelectedId(sub.id)}
              className={cn(
                'w-full flex items-center gap-3 p-3 border text-left transition-all',
                selectedId === sub.id
                  ? 'border-indigo-800 bg-indigo-50/50'
                  : 'border-ink-100 bg-white hover:border-ink-300'
              )}
            >
              <img
                src={sub.studentAvatar}
                alt={sub.studentName}
                className="w-10 h-10 rounded-full bg-ink-100"
              />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-ink-800">{sub.studentName}</p>
                <p className="text-xs text-ink-400">
                  {dayjs(sub.submittedAt).format('MM-DD HH:mm')}
                </p>
              </div>
              {sub.status === 'GRADED' ? (
                <span className="badge-green">
                  <CheckCircle2 className="w-3 h-3" />
                  {sub.score}
                </span>
              ) : (
                <span className="badge-amber">待批</span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Grade detail */}
      <div className="lg:col-span-3">
        {selected ? (
          <div className="card-editorial p-6 space-y-5">
            {/* Student info */}
            <div className="flex items-center gap-4 pb-4 border-b border-ink-100">
              <img
                src={selected.studentAvatar}
                alt={selected.studentName}
                className="w-14 h-14 rounded-full bg-ink-100"
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

            {/* Submission content */}
            <div>
              <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
                作业内容
              </label>
              <div className="bg-ink-50/50 border border-ink-100 p-4 text-sm text-ink-700 leading-relaxed whitespace-pre-wrap">
                {selected.content}
              </div>
            </div>

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
                  value={scores[selected.id] ?? selected.score ?? ''}
                  onChange={(e) =>
                    setScores((prev) => ({ ...prev, [selected.id]: e.target.value }))
                  }
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

            {/* Save */}
            <div className="flex items-center gap-3 pt-2">
              <button
                onClick={() => handleSave(selected)}
                className="btn-primary"
              >
                <Save className="w-4 h-4" />
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
