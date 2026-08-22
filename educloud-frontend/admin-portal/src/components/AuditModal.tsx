import { useState, useEffect } from 'react';
import { X, Check, XCircle, FileText, Video, Presentation } from 'lucide-react';
import type { Course, ContentItem } from '../types';
import { cn } from '../utils/cn';

interface AuditModalProps {
  open: boolean;
  onClose: () => void;
  onApprove: (reason?: string) => void;
  onReject: (reason: string) => void;
  title: string;
  item?: Course | ContentItem | null;
  loading?: boolean;
}

function isCourse(item: Course | ContentItem): item is Course {
  return (item as Course).teacherName !== undefined;
}

const typeIcon = {
  VIDEO: Video,
  PDF: FileText,
  PPT: Presentation,
};

const typeLabel = {
  VIDEO: '视频',
  PDF: '文档',
  PPT: '演示文稿',
};

export default function AuditModal({
  open,
  onClose,
  onApprove,
  onReject,
  title,
  item,
  loading = false,
}: AuditModalProps) {
  const [mode, setMode] = useState<'idle' | 'approve' | 'reject'>('idle');
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (open) {
      setMode('idle');
      setReason('');
    }
  }, [open]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (open) document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, onClose]);

  if (!open || !item) return null;

  const course = isCourse(item) ? item : null;
  const content = !isCourse(item) ? item : null;
  const TypeIcon = content ? typeIcon[content.type] : FileText;

  const handleConfirm = () => {
    if (mode === 'approve') onApprove(reason || undefined);
    if (mode === 'reject') {
      if (!reason.trim()) return;
      onReject(reason);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-black/70 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
      />
      <div className="relative bg-surface w-full max-w-2xl max-h-[90vh] overflow-y-auto border border-ink-200 shadow-2xl animate-fade-up rounded-2xl">
        {/* Header */}
        <div className="flex items-center justify-between px-8 py-5 border-b border-ink-100 bg-surface-light">
          <div>
            <div className="section-label mb-1">审核详情</div>
            <h2 className="font-display text-2xl font-bold text-ink-900">{title}</h2>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-ink-400 hover:text-ink-800 transition-colors"
          >
            <X size={20} />
          </button>
        </div>

        {/* Body */}
        <div className="p-8 space-y-6">
          {course && (
            <>
              <div className="aspect-[16/9] overflow-hidden bg-ink-100 border border-ink-200 rounded-xl">
                <img
                  src={course.cover}
                  alt={course.title}
                  className="w-full h-full object-cover"
                />
              </div>
              <div>
                <h3 className="font-display text-xl font-bold text-ink-900 mb-2">
                  {course.title}
                </h3>
                <p className="text-sm text-ink-500 leading-relaxed">{course.description}</p>
              </div>
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-ink-100">
                <DetailRow label="授课教师" value={course.teacherName} />
                <DetailRow label="课程分类" value={course.category} />
                <DetailRow label="课程价格" value={course.price === 0 ? '免费' : `¥${course.price}`} />
                <DetailRow label="提交日期" value={course.submittedDate} />
              </div>
            </>
          )}

          {content && (
            <>
              <div className="flex items-start gap-4 p-5 bg-surface border border-ink-100 rounded-xl">
                <span className="flex items-center justify-center w-14 h-14 bg-indigo-50 text-indigo-800 shrink-0 rounded-xl">
                  <TypeIcon size={28} />
                </span>
                <div className="flex-1 min-w-0">
                  <h3 className="font-display text-lg font-bold text-ink-900 mb-1 truncate">
                    {content.title}
                  </h3>
                  <p className="text-sm text-ink-500 truncate">{content.courseName}</p>
                </div>
                <span className="badge-indigo shrink-0">{typeLabel[content.type]}</span>
              </div>
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-ink-100">
                <DetailRow label="上传者" value={content.uploader} />
                <DetailRow label="文件大小" value={content.fileSize} />
                <DetailRow label="上传时间" value={content.uploadDate} />
                <DetailRow label="当前状态" value={content.status === 'PENDING' ? '待审核' : content.status} />
              </div>
            </>
          )}

          {/* Reason textarea for reject */}
          {(mode === 'reject' || mode === 'approve') && (
            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">
                {mode === 'reject' ? '驳回原因' : '审核备注（可选）'}
                {mode === 'reject' && <span className="text-red-500 ml-1">*</span>}
              </label>
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={4}
                placeholder={
                  mode === 'reject'
                    ? '请详细说明驳回原因，以便提交者修改...'
                    : '可填写审核通过的备注信息...'
                }
                className="input-field resize-none"
                autoFocus
              />
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 px-8 py-5 border-t border-ink-100 bg-surface-light">
          {mode === 'idle' ? (
            <>
              <button
                onClick={() => setMode('reject')}
                disabled={loading}
                className="btn-outline border-red-500/30 text-red-600 dark:text-red-400 hover:border-red-500/60 hover:text-red-700 dark:hover:text-red-300"
              >
                <XCircle size={16} />
                驳回
              </button>
              <button
                onClick={() => setMode('approve')}
                disabled={loading}
                className="btn-primary bg-green-600 hover:bg-green-700"
              >
                <Check size={16} />
                通过
              </button>
            </>
          ) : (
            <>
              <button
                onClick={() => {
                  setMode('idle');
                  setReason('');
                }}
                className="btn-ghost"
              >
                返回
              </button>
              <button
                onClick={handleConfirm}
                disabled={loading || (mode === 'reject' && !reason.trim())}
                className={cn(
                  'btn-primary',
                  mode === 'reject' && 'bg-red-600 hover:bg-red-700',
                  mode === 'approve' && 'bg-green-600 hover:bg-green-700',
                  (loading || (mode === 'reject' && !reason.trim())) && 'opacity-50 cursor-not-allowed',
                )}
              >
                {loading ? '提交中...' : mode === 'reject' ? '确认驳回' : '确认通过'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-widest text-ink-400 mb-1">{label}</div>
      <div className="text-sm font-medium text-ink-800">{value}</div>
    </div>
  );
}
