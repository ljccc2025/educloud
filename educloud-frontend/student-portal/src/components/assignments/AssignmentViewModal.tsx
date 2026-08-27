import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  X,
  CheckCircle2,
  Clock,
  Award,
  FileText,
  MessageSquare,
  Paperclip,
  Download,
  AlertCircle,
  Sparkles,
} from 'lucide-react';
import type { Assignment } from '../../types';
import { cn } from '../../utils/cn';
import dayjs from 'dayjs';

interface AssignmentViewModalProps {
  assignment: Assignment | null;
  isOpen: boolean;
  onClose: () => void;
  onResubmit?: (assignment: Assignment) => void;
}

export default function AssignmentViewModal({
  assignment,
  isOpen,
  onClose,
  onResubmit,
}: AssignmentViewModalProps) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen]);

  if (!isOpen || !assignment) return null;

  const isGraded = assignment.status === 'GRADED' && assignment.score !== undefined;
  const isSubmitted = assignment.status === 'SUBMITTED';

  const formatFileSize = (bytes?: number) => {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return createPortal(
    <div className="fixed inset-0 z-[100] overflow-y-auto bg-indigo-950/40 backdrop-blur-md flex items-center justify-center p-4 sm:p-6 animate-fade-in">
      <div
        className="relative w-full max-w-2xl bg-white rounded-3xl shadow-2xl border border-ink-100 overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-ink-100 bg-slate-50/50">
          <div>
            <div className="flex items-center gap-2">
              <span
                className={cn(
                  'px-2.5 py-0.5 text-xs font-semibold rounded-full',
                  isGraded ? 'bg-emerald-50 text-emerald-700' : 'bg-indigo-50 text-indigo-800'
                )}
              >
                {isGraded ? '作业已批改' : '作业已提交'}
              </span>
              <span className="text-xs text-ink-400">所属课程：{assignment.courseTitle}</span>
            </div>
            <h2 className="text-xl font-bold text-ink-900 mt-1">{assignment.title}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-white border border-ink-200 text-ink-500 hover:text-ink-900 hover:bg-ink-50 flex items-center justify-center transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Status Banner */}
          {isGraded ? (
            <div className="bg-gradient-to-r from-emerald-50 via-teal-50 to-emerald-50 border border-emerald-200/80 rounded-2xl p-5 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-emerald-600 text-white flex items-center justify-center shadow-md shadow-emerald-600/20">
                  <Award size={24} />
                </div>
                <div>
                  <p className="text-xs font-semibold text-emerald-800 uppercase tracking-wider">
                    本次作业考核得分
                  </p>
                  <div className="flex items-baseline gap-1 mt-0.5">
                    <span className="text-3xl font-extrabold text-emerald-700">{assignment.score}</span>
                    <span className="text-xs text-emerald-600 font-medium">/ {assignment.totalScore} 分</span>
                  </div>
                </div>
              </div>
              <span className="px-3 py-1 bg-white/80 border border-emerald-200 text-emerald-800 font-semibold text-xs rounded-full">
                批改完成
              </span>
            </div>
          ) : (
            <div className="bg-indigo-50/60 border border-indigo-100 rounded-2xl p-5 flex items-center justify-between">
              <div className="flex items-center gap-3.5">
                <div className="w-10 h-10 rounded-full bg-indigo-600 text-white flex items-center justify-center">
                  <CheckCircle2 size={20} />
                </div>
                <div>
                  <p className="text-sm font-bold text-ink-900">作业提交成功，等待教师批改</p>
                  <p className="text-xs text-ink-500 mt-0.5">
                    提交时间：{assignment.submitDate || assignment.submission?.submittedAt || '已提交'}
                  </p>
                </div>
              </div>
              <span className="px-3 py-1 bg-white border border-indigo-200 text-indigo-700 font-semibold text-xs rounded-full">
                待批阅
              </span>
            </div>
          )}

          {/* Teacher Feedback if Graded */}
          {isGraded && (
            <div className="bg-slate-50 border border-slate-200 rounded-2xl p-5 space-y-2">
              <div className="flex items-center gap-2 text-xs font-bold text-ink-800">
                <Sparkles size={16} className="text-amber-500" />
                <span>教师评语与指导建议</span>
              </div>
              <p className="text-xs text-ink-700 leading-relaxed pl-6">
                {assignment.feedback || '作业完成度高，逻辑清晰，代码规范性良好！继续保持！'}
              </p>
            </div>
          )}

          {/* Assignment Prompt */}
          <div className="bg-ink-50/60 border border-ink-100 rounded-2xl p-4 space-y-1 text-xs">
            <span className="font-semibold text-ink-800">作业要求：</span>
            <p className="text-ink-600 leading-relaxed">{assignment.description}</p>
          </div>

          {/* Student Submitted Answer */}
          <div>
            <h3 className="text-sm font-bold text-ink-900 mb-2 flex items-center gap-2">
              <FileText size={16} className="text-indigo-600" />
              我的作答内容
            </h3>
            <div className="p-4 bg-slate-50 border border-ink-100 rounded-2xl text-xs text-ink-800 leading-relaxed font-mono whitespace-pre-wrap">
              {assignment.submission?.content || '（暂无文字作答记录）'}
            </div>
          </div>

          {/* Submitted Files */}
          {assignment.submission?.files && assignment.submission.files.length > 0 && (
            <div>
              <h3 className="text-sm font-bold text-ink-900 mb-2 flex items-center gap-2">
                <Paperclip size={16} className="text-indigo-600" />
                提交的附件 ({assignment.submission.files.length})
              </h3>
              <div className="space-y-2">
                {assignment.submission.files.map((file, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between p-3.5 bg-white border border-ink-100 rounded-2xl text-xs"
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <FileText size={16} className="text-indigo-600 shrink-0" />
                      <span className="font-medium text-ink-900 truncate">{file.name}</span>
                      <span className="text-ink-400 shrink-0">({formatFileSize(file.size)})</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => alert(`模拟下载附件：${file.name}`)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-ink-700 font-medium rounded-lg transition-colors"
                    >
                      <Download size={13} />
                      下载
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Student Note */}
          {assignment.submission?.note && (
            <div>
              <h3 className="text-sm font-bold text-ink-900 mb-1 flex items-center gap-2">
                <MessageSquare size={16} className="text-indigo-600" />
                给老师的留言
              </h3>
              <p className="text-xs text-ink-600 bg-slate-50 p-3 rounded-xl border border-ink-100">
                {assignment.submission.note}
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-ink-100 flex items-center justify-between bg-slate-50/50">
          <span className="text-xs text-ink-400">
            截止时间：{dayjs(assignment.dueDate).isValid() ? dayjs(assignment.dueDate).format('YYYY-MM-DD HH:mm') : assignment.dueDate}
          </span>
          <div className="flex items-center gap-3">
            {onResubmit && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onResubmit(assignment);
                }}
                className="px-4 py-2 rounded-xl border border-indigo-200 text-indigo-700 hover:bg-indigo-50 text-xs font-semibold transition-colors"
              >
                重新编辑提交
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2 rounded-xl bg-ink-900 hover:bg-black text-white text-xs font-semibold transition-colors"
            >
              关闭
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}
