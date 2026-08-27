import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  X,
  UploadCloud,
  FileText,
  Trash2,
  Send,
  AlertCircle,
  Clock,
  Award,
  BookOpen,
  CheckCircle,
} from 'lucide-react';
import type { Assignment } from '../../types';
import { cn } from '../../utils/cn';
import dayjs from 'dayjs';

interface AssignmentSubmitModalProps {
  assignment: Assignment;
  isOpen: boolean;
  onClose: () => void;
  onSubmitSuccess: (updated: Assignment) => void;
  onSubmitService: (
    assignmentId: string | number,
    payload: { content: string; files?: Array<{ name: string; size: number }>; note?: string }
  ) => Promise<Assignment>;
}

export default function AssignmentSubmitModal({
  assignment,
  isOpen,
  onClose,
  onSubmitSuccess,
  onSubmitService,
}: AssignmentSubmitModalProps) {
  const [content, setContent] = useState('');
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<Array<{ name: string; size: number }>>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setContent(assignment.submission?.content || '');
      setNote(assignment.submission?.note || '');
      setFiles(assignment.submission?.files || []);
      setError(null);
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen, assignment]);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const chosen = e.target.files;
    if (!chosen || chosen.length === 0) return;
    const newFiles: Array<{ name: string; size: number }> = [];
    for (let i = 0; i < chosen.length; i++) {
      const file = chosen[i];
      newFiles.push({ name: file.name, size: file.size });
    }
    setFiles((prev) => [...prev, ...newFiles]);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const removeFile = (idx: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== idx));
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim() && files.length === 0) {
      setError('请填写作业解答内容或上传相关作业附件');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const updated = await onSubmitService(assignment.id, {
        content: content.trim(),
        files,
        note: note.trim(),
      });
      onSubmitSuccess(updated);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交作业失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const isOverdue = dayjs(assignment.dueDate).isBefore(dayjs());

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
              <span className="px-2.5 py-0.5 text-xs font-semibold bg-indigo-50 text-indigo-800 rounded-full">
                作业提交
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

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Assignment Meta Card */}
          <div className="bg-indigo-50/60 border border-indigo-100 rounded-2xl p-4 space-y-3">
            <div className="flex items-center justify-between text-xs text-ink-600">
              <span className="flex items-center gap-1.5">
                <Clock size={14} className={isOverdue ? 'text-red-500' : 'text-indigo-600'} />
                截止时间：<b className={isOverdue ? 'text-red-600' : 'text-ink-900'}>
                  {dayjs(assignment.dueDate).isValid()
                    ? dayjs(assignment.dueDate).format('YYYY-MM-DD HH:mm')
                    : assignment.dueDate}
                </b>
                {isOverdue && <span className="text-red-600 font-semibold">(已逾期)</span>}
              </span>
              <span className="flex items-center gap-1">
                <Award size={14} className="text-amber-500" />
                作业满分：<b>{assignment.totalScore} 分</b>
              </span>
            </div>
            <div className="pt-2 border-t border-indigo-100/80 text-xs text-ink-700 leading-relaxed">
              <p className="font-semibold text-ink-900 mb-1">作业要求与说明：</p>
              <p>{assignment.description}</p>
            </div>
          </div>

          {error && (
            <div className="flex items-center gap-2 p-3.5 bg-red-50 text-red-700 text-xs rounded-xl border border-red-200">
              <AlertCircle size={16} className="shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Answer Input */}
          <div>
            <label className="block text-sm font-semibold text-ink-800 mb-2">
              作业解答内容 <span className="text-red-500">*</span>
            </label>
            <textarea
              rows={6}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="请在此输入您的作业解答、解题思路、核心代码或报告摘要..."
              className="w-full px-4 py-3 bg-white border border-ink-200 rounded-2xl text-sm text-ink-900 placeholder:text-ink-300 focus:outline-none focus:ring-2 focus:ring-indigo-800/20 focus:border-indigo-800 transition-all resize-y font-mono"
            />
            <div className="flex justify-between items-center text-xs text-ink-400 mt-1.5 px-1">
              <span>支持 Markdown 与代码排版</span>
              <span>{content.length} 字</span>
            </div>
          </div>

          {/* File Upload Section */}
          <div>
            <label className="block text-sm font-semibold text-ink-800 mb-2">
              上传作业附件 <span className="text-xs text-ink-400 font-normal">（支持 ZIP、PDF、Word、图片等）</span>
            </label>

            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              multiple
              className="hidden"
              id="assignment-file-input"
            />

            <div
              onClick={() => fileInputRef.current?.click()}
              className="border-2 border-dashed border-ink-200 hover:border-indigo-600 bg-slate-50/60 hover:bg-indigo-50/30 rounded-2xl p-6 text-center cursor-pointer transition-all flex flex-col items-center justify-center gap-2"
            >
              <div className="w-10 h-10 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center">
                <UploadCloud size={20} />
              </div>
              <p className="text-sm font-medium text-ink-800">
                点击选择文件，或将作业附件拖拽至此处
              </p>
              <p className="text-xs text-ink-400">单文件最大 50MB，支持多附件同时提交</p>
            </div>

            {/* Uploaded Files List */}
            {files.length > 0 && (
              <div className="mt-3 space-y-2">
                {files.map((f, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between p-3 bg-white border border-ink-100 rounded-xl shadow-xs text-xs"
                  >
                    <div className="flex items-center gap-2.5 truncate">
                      <FileText size={16} className="text-indigo-600 shrink-0" />
                      <span className="font-medium text-ink-900 truncate">{f.name}</span>
                      <span className="text-ink-400 shrink-0">({formatFileSize(f.size)})</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeFile(idx)}
                      className="text-ink-400 hover:text-red-600 p-1 transition-colors"
                      title="移除附件"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Teacher Note */}
          <div>
            <label className="block text-sm font-semibold text-ink-800 mb-2">
              向授课教师留言 <span className="text-xs text-ink-400 font-normal">（选填）</span>
            </label>
            <input
              type="text"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="如有疑问或特殊说明，可在此向老师留言..."
              className="w-full px-4 py-2.5 bg-white border border-ink-200 rounded-xl text-sm text-ink-900 placeholder:text-ink-300 focus:outline-none focus:ring-2 focus:ring-indigo-800/20 focus:border-indigo-800 transition-all"
            />
          </div>

          {/* Footer */}
          <div className="pt-4 border-t border-ink-100 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-5 py-2.5 rounded-xl border border-ink-200 text-sm font-medium text-ink-600 hover:bg-ink-50 transition-colors"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-6 py-2.5 rounded-xl bg-indigo-800 hover:bg-indigo-900 text-white text-sm font-semibold shadow-md shadow-indigo-900/10 flex items-center gap-2 transition-all disabled:opacity-50"
            >
              {submitting ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent animate-spin rounded-full" />
                  <span>正在提交...</span>
                </>
              ) : (
                <>
                  <Send size={15} />
                  <span>确认提交作业</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
