import { memo, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Play, Clock, Award, BookOpen } from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import ProgressBar from '@/components/ProgressBar';
import { cn } from '@/utils/cn';
import type { Course, CourseStatus } from '@/types';

type Tab = 'all' | 'in_progress' | 'completed';

const tabs: { value: Tab; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'in_progress', label: '进行中' },
  { value: 'completed', label: '已完成' },
];

const getCourseStatus = (course: Course): CourseStatus => {
  if (course.progress >= 100) return 'COMPLETED';
  if (course.progress > 0) return 'IN_PROGRESS';
  return 'NOT_STARTED';
};

const CourseCover = memo(function CourseCover({ course }: { course: Course }) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <Link
      to={`/courses/${course.id}`}
      className="relative sm:w-64 h-40 sm:h-auto bg-indigo-900 flex items-center justify-center p-6 flex-shrink-0 overflow-hidden"
    >
      {!imageFailed && (
        <img
          src={course.cover}
          alt=""
          loading="lazy"
          decoding="async"
          className="absolute inset-0 w-full h-full object-cover"
          onError={() => setImageFailed(true)}
        />
      )}
      <div className="absolute inset-0 bg-gradient-to-t from-indigo-950/80 via-indigo-900/35 to-transparent" />
      <h3 className="relative font-display text-lg font-bold text-white text-center leading-tight">
        {course.title}
      </h3>
    </Link>
  );
});

export default function MyCourses() {
  const { courses, loading, fetchCourses } = useCourseStore();
  const [activeTab, setActiveTab] = useState<Tab>('all');

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  const enrolledCourses = useMemo(() => courses.filter((c) => c.enrolled), [courses]);

  const filteredCourses = useMemo(() => enrolledCourses.filter((course) => {
    if (activeTab === 'all') return true;
    return getCourseStatus(course) === activeTab.toUpperCase();
  }), [activeTab, enrolledCourses]);

  const { completedCount, inProgressCount, totalHours } = useMemo(() => ({
    completedCount: enrolledCourses.filter((course) => getCourseStatus(course) === 'COMPLETED').length,
    inProgressCount: enrolledCourses.filter((course) => getCourseStatus(course) === 'IN_PROGRESS').length,
    totalHours: enrolledCourses.reduce((sum, course) => sum + Number.parseInt(course.totalDuration, 10), 0),
  }), [enrolledCourses]);

  const getStatusBadge = (status: CourseStatus) => {
    switch (status) {
      case 'COMPLETED':
        return <span className="badge-green">已完成</span>;
      case 'IN_PROGRESS':
        return <span className="badge-amber">进行中</span>;
      default:
        return <span className="badge-indigo">未开始</span>;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      {/* Header */}
      <div className="mb-10">
        <span className="section-label mb-3">我的学习</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">我的课程</h1>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-10">
        <div className="stat-card">
          <BookOpen size={20} className="text-indigo-800 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">{enrolledCourses.length}</p>
          <p className="text-sm text-ink-500">已购课程</p>
        </div>
        <div className="stat-card">
          <Clock size={20} className="text-amber-600 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">{inProgressCount}</p>
          <p className="text-sm text-ink-500">进行中</p>
        </div>
        <div className="stat-card">
          <Award size={20} className="text-green-600 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">{completedCount}</p>
          <p className="text-sm text-ink-500">已完成</p>
        </div>
        <div className="stat-card">
          <Play size={20} className="text-indigo-800 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">{totalHours}</p>
          <p className="text-sm text-ink-500">总学时</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-ink-200 mb-8">
        <div className="flex gap-8">
          {tabs.map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => setActiveTab(tab.value)}
              className={cn(
                'pb-3 text-sm font-medium transition-colors relative',
                activeTab === tab.value
                  ? 'text-indigo-800'
                  : 'text-ink-400 hover:text-ink-600'
              )}
            >
              {tab.label}
              {activeTab === tab.value && (
                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-amber-600" />
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Course List */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
        </div>
      ) : filteredCourses.length === 0 ? (
        <div className="text-center py-20 bg-white border border-ink-100">
          <BookOpen size={48} className="mx-auto text-ink-200 mb-4" strokeWidth={1} />
          <p className="font-display text-xl text-ink-400 mb-2">暂无课程</p>
          <p className="text-sm text-ink-400 mb-6">去课程中心发现感兴趣的内容吧</p>
          <Link to="/courses" className="btn-primary">浏览课程</Link>
        </div>
      ) : (
        <div className="space-y-4">
          {filteredCourses.map((course) => (
            <div
              key={course.id}
              className="card-editorial p-0 overflow-hidden flex flex-col sm:flex-row"
            >
              <CourseCover course={course} />

              <div className="flex-1 p-6 flex flex-col">
                <div className="flex items-start justify-between gap-4 mb-2">
                  <div>
                    <Link
                      to={`/courses/${course.id}`}
                      className="font-display text-xl font-bold text-ink-900 hover:text-indigo-800 transition-colors"
                    >
                      {course.title}
                    </Link>
                    <p className="text-sm text-ink-500 mt-1">{course.description}</p>
                  </div>
                  {getStatusBadge(getCourseStatus(course))}
                </div>

                <div className="flex items-center gap-4 text-xs text-ink-400 mb-4">
                  <span>讲师：{course.teacherName}</span>
                  <span>{course.chapters.length} 章</span>
                  <span>{course.totalDuration}</span>
                </div>

                <div className="mt-auto">
                  <ProgressBar progress={course.progress} showLabel className="mb-4" />
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-ink-400">
                      上次学习：2025-08-{10 + course.id}
                    </span>
                    <Link
                      to={`/learn/${course.id}`}
                      className="inline-flex items-center gap-2 px-5 py-2 bg-indigo-800 text-white text-sm font-medium hover:bg-indigo-900 transition-colors"
                    >
                      <Play size={14} fill="currentColor" />
                      {getCourseStatus(course) === 'COMPLETED' ? '复习课程' : '继续学习'}
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
