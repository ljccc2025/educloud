import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ChevronLeft, ChevronRight, Play, FileText, HelpCircle,
  Check, Menu, X, BookOpen,
} from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import VideoPlayer from '@/components/VideoPlayer';
import ProgressBar from '@/components/ProgressBar';
import { cn } from '@/utils/cn';
import type { Courseware } from '@/types';

export default function Learning() {
  const { courseId } = useParams<{ courseId: string }>();
  const { currentCourse, loading, fetchCourse } = useCourseStore();
  const [activeCourseware, setActiveCourseware] = useState<Courseware | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(true);

  useEffect(() => {
    if (courseId) fetchCourse(courseId);
  }, [courseId, fetchCourse]);

  useEffect(() => {
    if (currentCourse && currentCourse.chapters.length > 0) {
      const firstChapter = currentCourse.chapters[0];
      if (firstChapter.coursewares.length > 0) {
        setActiveCourseware(firstChapter.coursewares[0]);
      }
    }
  }, [currentCourse]);

  if (loading || !currentCourse) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
      </div>
    );
  }

  const course = currentCourse;

  // Flatten coursewares for prev/next navigation
  const allCoursewares: { chapterId: number; chapterTitle: string; courseware: Courseware }[] = [];
  course.chapters.forEach((ch) => {
    ch.coursewares.forEach((cw) => {
      allCoursewares.push({ chapterId: ch.id, chapterTitle: ch.title, courseware: cw });
    });
  });

  const currentIndex = activeCourseware
    ? allCoursewares.findIndex((item) => item.courseware.id === activeCourseware.id)
    : -1;
  const prevItem = currentIndex > 0 ? allCoursewares[currentIndex - 1] : null;
  const nextItem = currentIndex < allCoursewares.length - 1 ? allCoursewares[currentIndex + 1] : null;

  const getCoursewareIcon = (type: string, completed: boolean) => {
    const cls = cn('w-5 h-5', completed ? 'text-green-600' : 'text-ink-400');
    switch (type) {
      case 'video': return <Play size={14} className={cls} />;
      case 'quiz': return <HelpCircle size={14} className={cls} />;
      default: return <FileText size={14} className={cls} />;
    }
  };

  return (
    <div className="flex flex-col lg:flex-row min-h-[calc(100vh-4rem)]">
      {/* Main Learning Area */}
      <div className="flex-1 min-w-0">
        {/* Top Bar */}
        <div className="bg-white border-b border-ink-100 px-4 lg:px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4 min-w-0">
            <button
              type="button"
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="p-2 text-ink-500 hover:text-indigo-800 hover:bg-ink-50 transition-colors lg:hidden"
            >
              {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
            <div className="min-w-0">
              <Link
                to={`/courses/${course.id}`}
                className="text-xs text-ink-400 hover:text-indigo-800 transition-colors"
              >
                {course.title}
              </Link>
              <h1 className="font-display text-lg font-bold text-ink-900 truncate">
                {activeCourseware?.title ?? '课程学习'}
              </h1>
            </div>
          </div>
          <Link
            to="/my-courses"
            className="hidden sm:flex items-center gap-2 text-sm text-ink-500 hover:text-indigo-800 transition-colors"
          >
            <BookOpen size={16} />
            我的课程
          </Link>
        </div>

        {/* Video Player */}
        <div className="bg-ink-900 p-4 lg:p-8">
          <div className="max-w-5xl mx-auto">
            <VideoPlayer title={activeCourseware?.title} />
          </div>
        </div>

        {/* Courseware Info & Controls */}
        <div className="max-w-5xl mx-auto px-4 lg:px-8 py-8">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="font-display text-2xl font-bold text-ink-900">
                {activeCourseware?.title}
              </h2>
              <p className="text-sm text-ink-500 mt-1">
                {allCoursewares[currentIndex]?.chapterTitle} · 时长 {activeCourseware?.duration} 分钟
              </p>
            </div>
            {activeCourseware?.completed && (
              <span className="badge-green">
                <Check size={12} />
                已完成
              </span>
            )}
          </div>

          {/* Progress */}
          <div className="mb-8">
            <div className="flex justify-between text-sm mb-2">
              <span className="text-ink-500">课程进度</span>
              <span className="font-semibold text-indigo-800">
                {currentIndex + 1} / {allCoursewares.length}
              </span>
            </div>
            <ProgressBar progress={((currentIndex + 1) / allCoursewares.length) * 100} />
          </div>

          {/* Navigation Buttons */}
          <div className="flex items-center justify-between gap-4 pt-6 border-t border-ink-100">
            <button
              type="button"
              disabled={!prevItem}
              onClick={() => prevItem && setActiveCourseware(prevItem.courseware)}
              className={cn(
                'btn-outline',
                !prevItem && 'opacity-40 cursor-not-allowed pointer-events-none'
              )}
            >
              <ChevronLeft size={16} />
              上一节
            </button>
            <button
              type="button"
              disabled={!nextItem}
              onClick={() => nextItem && setActiveCourseware(nextItem.courseware)}
              className={cn(
                'btn-primary',
                !nextItem && 'opacity-40 cursor-not-allowed pointer-events-none'
              )}
            >
              下一节
              <ChevronRight size={16} />
            </button>
          </div>

          {/* Course Description */}
          <div className="mt-10 p-6 bg-white border border-ink-100">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-3">本节简介</h3>
            <p className="text-sm text-ink-600 leading-relaxed">
              本节课是《{course.title}》课程的重要组成部分，由 {course.teacherName} 老师主讲。
              通过本节的学习，你将掌握相关核心概念与实践技巧，建议结合课程资料和源码进行同步练习。
              学习过程中如有疑问，可在课程讨论区提问或参加直播答疑。
            </p>
          </div>
        </div>
      </div>

      {/* Chapter Sidebar */}
      <aside
        className={cn(
          'w-full lg:w-80 bg-white border-l border-ink-100 flex-shrink-0 overflow-y-auto',
          'lg:block',
          sidebarOpen ? 'block' : 'hidden',
          'lg:sticky lg:top-16 lg:h-[calc(100vh-4rem)]'
        )}
      >
        <div className="p-4 border-b border-ink-100">
          <h3 className="font-display text-lg font-bold text-ink-900">课程目录</h3>
          <p className="text-xs text-ink-400 mt-1">
            {allCoursewares.length} 节 · {course.totalDuration}
          </p>
        </div>
        <div className="p-2">
          {course.chapters.map((chapter, chIdx) => (
            <div key={chapter.id} className="mb-2">
              <div className="px-3 py-2">
                <p className="text-xs font-semibold uppercase tracking-wider text-ink-400">
                  第 {chIdx + 1} 章
                </p>
                <p className="text-sm font-medium text-ink-700 mt-0.5">
                  {chapter.title.replace(/^第 \d+ 章 · /, '')}
                </p>
              </div>
              <div className="space-y-0.5">
                {chapter.coursewares.map((cw) => {
                  const isActive = activeCourseware?.id === cw.id;
                  return (
                    <button
                      key={cw.id}
                      type="button"
                      onClick={() => setActiveCourseware(cw)}
                      className={cn(
                        'w-full flex items-center gap-3 px-3 py-2.5 text-left text-sm transition-colors',
                        isActive
                          ? 'bg-indigo-50 text-indigo-800 border-l-2 border-amber-600'
                          : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent'
                      )}
                    >
                      {getCoursewareIcon(cw.type, cw.completed)}
                      <span className="flex-1 truncate">{cw.title}</span>
                      <span className="text-xs text-ink-400 flex-shrink-0">{cw.duration}分</span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </aside>
    </div>
  );
}
