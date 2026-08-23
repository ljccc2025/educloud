import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { BookOpen, Menu, X } from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import VideoPlayer from '@/components/VideoPlayer';
import { useState } from 'react';

export default function Learning() {
  const { courseId } = useParams<{ courseId: string }>();
  const { currentCourse, loading, fetchCourse } = useCourseStore();
  const [sidebarOpen, setSidebarOpen] = useState(true);

  useEffect(() => {
    if (courseId) void fetchCourse(courseId);
  }, [courseId, fetchCourse]);

  if (loading || !currentCourse) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
      </div>
    );
  }

  const course = currentCourse;

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
                课程学习
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
            <VideoPlayer title={course.title} />
          </div>
        </div>

        {/* Courseware Info & Controls */}
        <div className="max-w-5xl mx-auto px-4 lg:px-8 py-8">
          <div>
            <h2 className="font-display text-2xl font-bold text-ink-900">课程学习</h2>
            <p className="text-sm text-ink-500 mt-1">
              视频内容将在章节/课件模块上线后提供，敬请期待
            </p>
          </div>

          {/* Course Description */}
          <div className="mt-10 p-6 bg-white border border-ink-100">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-3">课程简介</h3>
            <p className="text-sm text-ink-600 leading-relaxed">
              {course.description || '该课程暂未填写简介。'}
            </p>
          </div>
        </div>
      </div>

      {/* Chapter Sidebar */}
      <aside
        className={
          'w-full lg:w-80 bg-white border-l border-ink-100 flex-shrink-0 overflow-y-auto ' +
          'lg:block ' +
          (sidebarOpen ? 'block' : 'hidden') +
          ' lg:sticky lg:top-16 lg:h-[calc(100vh-4rem)]'
        }
      >
        <div className="p-4 border-b border-ink-100">
          <h3 className="font-display text-lg font-bold text-ink-900">课程目录</h3>
          <p className="text-xs text-ink-400 mt-1">章节内容即将上线</p>
        </div>
        <div className="p-6 text-center">
          <BookOpen size={32} className="mx-auto text-ink-200 mb-3" strokeWidth={1} />
          <p className="text-sm text-ink-400">目录即将上线</p>
        </div>
      </aside>
    </div>
  );
}
