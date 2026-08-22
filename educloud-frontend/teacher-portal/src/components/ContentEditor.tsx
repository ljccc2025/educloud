import { useState } from 'react';
import {
  GripVertical,
  Plus,
  Trash2,
  ChevronDown,
  ChevronRight,
  Video,
  FileText,
  Presentation,
  Upload,
  Clock,
  HardDrive,
} from 'lucide-react';
import type { Course, Chapter, CoursewareType } from '../types';
import { cn } from '../utils/cn';

interface ContentEditorProps {
  course: Course;
  onAddChapter: (title: string) => void;
  onRemoveChapter: (chapterId: string) => void;
  onReorderChapters: (chapters: Chapter[]) => void;
  onAddCourseware: (chapterId: string, type: CoursewareType, title: string) => void;
  onRemoveCourseware: (chapterId: string, coursewareId: string) => void;
}

const cwTypeConfig: Record<CoursewareType, { label: string; icon: typeof Video; color: string }> = {
  VIDEO: { label: '视频', icon: Video, color: 'text-indigo-600 bg-indigo-50' },
  PDF: { label: 'PDF 文档', icon: FileText, color: 'text-red-600 bg-red-50' },
  PPT: { label: 'PPT 课件', icon: Presentation, color: 'text-amber-600 bg-amber-50' },
};

export default function ContentEditor({
  course,
  onAddChapter,
  onRemoveChapter,
  onReorderChapters,
  onAddCourseware,
  onRemoveCourseware,
}: ContentEditorProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set(course.chapters.map((c) => c.id)));
  const [newChapterTitle, setNewChapterTitle] = useState('');
  const [showAddFor, setShowAddFor] = useState<string | null>(null);
  const [newCwTitle, setNewCwTitle] = useState('');
  const [newCwType, setNewCwType] = useState<CoursewareType>('VIDEO');
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  const toggle = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleAddChapter = () => {
    if (!newChapterTitle.trim()) return;
    onAddChapter(newChapterTitle.trim());
    setNewChapterTitle('');
  };

  const handleAddCw = (chapterId: string) => {
    if (!newCwTitle.trim()) return;
    onAddCourseware(chapterId, newCwType, newCwTitle.trim());
    setNewCwTitle('');
    setShowAddFor(null);
  };

  const moveChapter = (from: number, to: number) => {
    if (to < 0 || to >= course.chapters.length) return;
    const updated = [...course.chapters];
    const [moved] = updated.splice(from, 1);
    updated.splice(to, 0, moved);
    onReorderChapters(updated);
  };

  return (
    <div className="space-y-4">
      {/* Chapter list */}
      {course.chapters.map((chapter, idx) => {
        const isOpen = expanded.has(chapter.id);
        return (
          <div key={chapter.id} className="card-editorial">
            {/* Chapter header */}
            <div className="flex items-center gap-3 p-4">
              {/* Drag handle */}
              <div
                draggable
                onDragStart={() => setDragIdx(idx)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => {
                  if (dragIdx !== null && dragIdx !== idx) moveChapter(dragIdx, idx);
                  setDragIdx(null);
                }}
                className="cursor-grab active:cursor-grabbing text-ink-300 hover:text-ink-500 transition-colors"
                title="拖拽排序"
              >
                <GripVertical className="w-5 h-5" />
              </div>

              <button
                onClick={() => toggle(chapter.id)}
                className="text-ink-400 hover:text-ink-700 transition-colors"
              >
                {isOpen ? <ChevronDown className="w-5 h-5" /> : <ChevronRight className="w-5 h-5" />}
              </button>

              <span className="font-display text-lg font-semibold text-ink-800 flex-1">
                <span className="text-amber-600 mr-2">{String(idx + 1).padStart(2, '0')}</span>
                {chapter.title}
              </span>

              <span className="text-xs text-ink-400">
                {chapter.coursewares.length} 个课件
              </span>

              <button
                onClick={() => setShowAddFor(showAddFor === chapter.id ? null : chapter.id)}
                className="btn-ghost"
              >
                <Plus className="w-4 h-4" />
                添加课件
              </button>

              <button
                onClick={() => onRemoveChapter(chapter.id)}
                className="btn-ghost text-red-500 hover:text-red-700"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>

            {/* Courseware list */}
            {isOpen && (
              <div className="border-t border-ink-100">
                {chapter.coursewares.length === 0 && (
                  <p className="px-4 py-6 text-sm text-ink-400 text-center bg-ink-50/30">
                    暂无课件，点击「添加课件」上传视频或文档
                  </p>
                )}
                {chapter.coursewares.map((cw) => {
                  const cfg = cwTypeConfig[cw.type];
                  const Icon = cfg.icon;
                  return (
                    <div
                      key={cw.id}
                      className="flex items-center gap-3 px-4 py-3 border-b border-ink-50 last:border-b-0 hover:bg-ink-50/40 transition-colors"
                    >
                      <div className={cn('w-9 h-9 flex items-center justify-center rounded-lg', cfg.color)}>
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-ink-800 truncate">{cw.title}</p>
                        <p className="text-xs text-ink-400 flex items-center gap-3 mt-0.5">
                          <span>{cfg.label}</span>
                          {cw.type === 'VIDEO' && cw.duration && (
                            <span className="flex items-center gap-1">
                              <Clock className="w-3 h-3" />
                              {cw.duration} 分钟
                            </span>
                          )}
                          {cw.type !== 'VIDEO' && cw.size && (
                            <span className="flex items-center gap-1">
                              <HardDrive className="w-3 h-3" />
                              {cw.size} MB
                            </span>
                          )}
                        </p>
                      </div>
                      <button
                        onClick={() => onRemoveCourseware(chapter.id, cw.id)}
                        className="text-ink-300 hover:text-red-600 transition-colors rounded-lg p-1"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  );
                })}

                {/* Add courseware form */}
                {showAddFor === chapter.id && (
                  <div className="p-4 bg-indigo-50/30 border-t border-ink-100 space-y-3 rounded-b-2xl">
                    <div className="flex gap-2">
                      {(Object.keys(cwTypeConfig) as CoursewareType[]).map((t) => {
                        const cfg = cwTypeConfig[t];
                        const Icon = cfg.icon;
                        return (
                          <button
                            key={t}
                            type="button"
                            onClick={() => setNewCwType(t)}
                            className={cn(
                              'flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border transition-all rounded-lg',
                              newCwType === t
                                ? 'border-indigo-800 bg-indigo-800 text-white'
                                : 'border-ink-200 bg-white text-ink-600 hover:border-ink-400'
                            )}
                          >
                            <Icon className="w-3.5 h-3.5" />
                            {cfg.label}
                          </button>
                        );
                      })}
                    </div>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={newCwTitle}
                        onChange={(e) => setNewCwTitle(e.target.value)}
                        placeholder={`输入${cwTypeConfig[newCwType].label}标题`}
                        className="input-field flex-1"
                        autoFocus
                        onKeyDown={(e) => e.key === 'Enter' && handleAddCw(chapter.id)}
                      />
                      <button
                        type="button"
                        onClick={() => handleAddCw(chapter.id)}
                        className="btn-primary"
                      >
                        <Upload className="w-4 h-4" />
                        上传
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}

      {/* Add chapter */}
      <div className="card-editorial p-4">
        <div className="flex gap-3">
          <input
            type="text"
            value={newChapterTitle}
            onChange={(e) => setNewChapterTitle(e.target.value)}
            placeholder="输入新章节标题，例如：第四章：Spring Security 安全框架"
            className="input-field flex-1"
            onKeyDown={(e) => e.key === 'Enter' && handleAddChapter()}
          />
          <button type="button" onClick={handleAddChapter} className="btn-primary whitespace-nowrap">
            <Plus className="w-4 h-4" />
            添加章节
          </button>
        </div>
      </div>
    </div>
  );
}
