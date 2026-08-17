import { useEffect, useState } from 'react';
import { ChevronDown, Video, FileText, Presentation, Clock, HardDrive } from 'lucide-react';
import { useCourseStore } from '../stores/useCourseStore';
import type { Course, CoursewareType } from '../types';
import { cn } from '../utils/cn';

const cwTypeConfig: Record<CoursewareType, { label: string; icon: typeof Video; color: string }> = {
  VIDEO: { label: '视频', icon: Video, color: 'text-indigo-600 bg-indigo-50' },
  PDF: { label: 'PDF', icon: FileText, color: 'text-red-600 bg-red-50' },
  PPT: { label: 'PPT', icon: Presentation, color: 'text-amber-600 bg-amber-50' },
};

export default function ContentManage() {
  const { courses, loading, fetchCourses } = useCourseStore();
  const [selectedId, setSelectedId] = useState<string>('');
  const [openChapters, setOpenChapters] = useState<Set<string>>(new Set());

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  useEffect(() => {
    if (courses.length > 0 && !selectedId) {
      setSelectedId(courses[0].id);
    }
  }, [courses, selectedId]);

  const selectedCourse: Course | undefined = courses.find((c) => c.id === selectedId);

  const toggleChapter = (id: string) => {
    setOpenChapters((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // Expand all by default when course changes
  useEffect(() => {
    if (selectedCourse) {
      setOpenChapters(new Set(selectedCourse.chapters.map((ch) => ch.id)));
    }
  }, [selectedId]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div>
        <p className="section-label mb-2">内容管理</p>
        <h1 className="display-heading text-3xl md:text-4xl">课程内容</h1>
        <p className="text-ink-500 mt-2 text-sm">管理章节结构与课件资源，支持视频、PDF 与 PPT</p>
      </div>

      {/* Course selector */}
      <div className="card-editorial p-4">
        <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
          选择课程
        </label>
        <div className="relative">
          <select
            value={selectedId}
            onChange={(e) => setSelectedId(e.target.value)}
            className="input-field appearance-none cursor-pointer font-display text-lg pr-10"
          >
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
          <ChevronDown className="absolute right-4 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-400 pointer-events-none" />
        </div>
      </div>

      {/* Content */}
      {loading && !selectedCourse ? (
        <div className="text-center py-16 text-ink-400">加载中…</div>
      ) : !selectedCourse ? (
        <div className="card-editorial p-12 text-center text-ink-400">请选择一门课程</div>
      ) : (
        <div className="space-y-3">
          {/* Course summary */}
          <div className="flex items-center gap-4 p-4 bg-white border border-ink-100">
            <img
              src={selectedCourse.cover}
              alt={selectedCourse.title}
              className="w-24 h-16 object-cover bg-ink-100"
            />
            <div className="flex-1">
              <h2 className="font-display text-lg font-semibold text-ink-900">
                {selectedCourse.title}
              </h2>
              <p className="text-sm text-ink-400 mt-0.5">
                共 {selectedCourse.chapters.length} 章节 ·{' '}
                {selectedCourse.chapters.reduce((acc, ch) => acc + ch.coursewares.length, 0)} 个课件
              </p>
            </div>
            <div className="text-right">
              <p className="text-2xl font-display font-bold text-indigo-800">
                {selectedCourse.studentCount.toLocaleString()}
              </p>
              <p className="text-xs text-ink-400">学员</p>
            </div>
          </div>

          {/* Chapter accordion */}
          {selectedCourse.chapters.length === 0 ? (
            <div className="card-editorial p-12 text-center">
              <p className="text-ink-500 mb-1">该课程暂无章节内容</p>
              <p className="text-sm text-ink-400">请前往「课程管理 → 编辑」添加章节与课件</p>
            </div>
          ) : (
            selectedCourse.chapters.map((chapter, idx) => {
              const isOpen = openChapters.has(chapter.id);
              return (
                <div key={chapter.id} className="card-editorial">
                  <button
                    onClick={() => toggleChapter(chapter.id)}
                    className="w-full flex items-center gap-4 p-4 text-left hover:bg-ink-50/40 transition-colors"
                  >
                    <span className="font-display text-2xl font-bold text-amber-600/60 w-10">
                      {String(idx + 1).padStart(2, '0')}
                    </span>
                    <span className="flex-1 font-display text-lg font-semibold text-ink-800">
                      {chapter.title}
                    </span>
                    <span className="text-xs text-ink-400">
                      {chapter.coursewares.length} 课件
                    </span>
                    <ChevronDown
                      className={cn(
                        'w-5 h-5 text-ink-400 transition-transform',
                        isOpen && 'rotate-180'
                      )}
                    />
                  </button>

                  {isOpen && (
                    <div className="border-t border-ink-100">
                      {chapter.coursewares.length === 0 ? (
                        <p className="px-4 py-6 text-sm text-ink-400 text-center bg-ink-50/30">
                          暂无课件
                        </p>
                      ) : (
                        chapter.coursewares.map((cw) => {
                          const cfg = cwTypeConfig[cw.type];
                          const Icon = cfg.icon;
                          return (
                            <div
                              key={cw.id}
                              className="flex items-center gap-4 px-4 py-3 border-b border-ink-50 last:border-b-0 hover:bg-indigo-50/20 transition-colors"
                            >
                              <div className={cn('w-9 h-9 flex items-center justify-center', cfg.color)}>
                                <Icon className="w-4 h-4" />
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium text-ink-800 truncate">
                                  {cw.title}
                                </p>
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
                              <button className="btn-ghost text-xs">预览</button>
                            </div>
                          );
                        })
                      )}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
