import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, CalendarDays, Check, Lock, Play, Sparkles } from 'lucide-react';
import dayjs from 'dayjs';
import { useCourseStore } from '@/stores/useCourseStore';
import { cover } from '@/services/api';
import { apiErrorText } from '@/services/http';
import type { Course, MyCourse } from '@/types';

/** 教师展示名：纯数字（占位的 teacherId）时掩码显示，与 CourseCard 一致。 */
const displayTeacher = (name?: string) => {
  if (!name) return '讲师';
  if (/^\d+$/.test(name)) return '讲师 ···' + name.slice(-6);
  return name;
};

function CoverImage({ src, title }: { src: string | null; title: string }) {
  const [imageFailed, setImageFailed] = useState(false);
  const resolved = imageFailed || !src ? cover(0) : src;
  return (
    <img
      src={resolved}
      alt=""
      loading="lazy"
      decoding="async"
      className="w-full h-full object-cover"
      onError={() => setImageFailed(true)}
    />
  );
}

export default function MyCourses() {
  const { myCourses, courses, loading, error, fetchMyCourses, fetchCourses, enroll } = useCourseStore();
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [enrollError, setEnrollError] = useState('');
  const [retryTick, setRetryTick] = useState(0);

  useEffect(() => {
    void fetchMyCourses();
  }, [fetchMyCourses, retryTick]);

  // 免费课程推荐（真实列表，供「免费选课」按钮使用）
  useEffect(() => {
    void fetchCourses({ priceRange: 'free', sort: 'popular', size: 6 });
  }, [fetchCourses]);

  const enrolledIds = useMemo(
    () => new Set(myCourses.map((m) => m.courseId)),
    [myCourses],
  );

  const freeRecommendations = useMemo(
    () => courses.filter((c) => !enrolledIds.has(c.id)).slice(0, 6),
    [courses, enrolledIds],
  );

  const handleEnroll = useCallback(async (courseId: string) => {
    if (enrollingId) return;
    setEnrollingId(courseId);
    setEnrollError('');
    try {
      await enroll(courseId);
      await fetchMyCourses();
    } catch (e) {
      setEnrollError(apiErrorText(e));
    } finally {
      setEnrollingId(null);
    }
  }, [enroll, enrollingId, fetchMyCourses]);

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
          <p className="font-display text-3xl font-bold text-ink-900">{myCourses.length}</p>
          <p className="text-sm text-ink-500">已选课程</p>
        </div>
        <div className="stat-card">
          <Check size={20} className="text-green-600 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">
            {myCourses.filter((m) => m.status === 'ACTIVE').length}
          </p>
          <p className="text-sm text-ink-500">学习中</p>
        </div>
        <div className="stat-card">
          <Sparkles size={20} className="text-amber-600 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">{freeRecommendations.length}</p>
          <p className="text-sm text-ink-500">可免费加入</p>
        </div>
        <div className="stat-card">
          <CalendarDays size={20} className="text-indigo-800 mb-2" strokeWidth={1.5} />
          <p className="font-display text-3xl font-bold text-ink-900">
            {myCourses.length > 0 ? dayjs(myCourses[0].enrolledAt).format('MM-DD') : '--'}
          </p>
          <p className="text-sm text-ink-500">最近选课</p>
        </div>
      </div>

      {loading && myCourses.length === 0 ? (
        <div className="flex items-center justify-center py-20">
          <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
        </div>
      ) : error && myCourses.length === 0 ? (
        <div className="text-center py-20 bg-white border border-ink-100">
          <p className="font-display text-xl text-ink-400 mb-2">加载失败</p>
          <p className="text-sm text-ink-400 mb-6">{error}</p>
          <button
            type="button"
            onClick={() => setRetryTick((tick) => tick + 1)}
            className="btn-primary"
          >
            重新加载
          </button>
        </div>
      ) : myCourses.length === 0 ? (
        <div className="text-center py-20 bg-white border border-ink-100">
          <BookOpen size={48} className="mx-auto text-ink-200 mb-4" strokeWidth={1} />
          <p className="font-display text-xl text-ink-400 mb-2">暂无课程</p>
          <p className="text-sm text-ink-400 mb-6">去课程中心发现感兴趣的内容吧</p>
          <Link to="/courses" className="btn-primary">浏览课程</Link>
        </div>
      ) : (
        <div className="space-y-4">
          {myCourses.map((course: MyCourse) => (
            <div
              key={course.courseId}
              className="card-editorial p-0 overflow-hidden flex flex-col sm:flex-row"
            >
              <Link
                to={`/courses/${course.courseId}`}
                className="relative sm:w-64 h-40 sm:h-auto bg-indigo-900 flex items-center justify-center p-6 flex-shrink-0 overflow-hidden"
              >
                <CoverImage src={course.coverUrl} title={course.title} />
                <div className="absolute inset-0 bg-gradient-to-t from-indigo-950/80 via-indigo-900/35 to-transparent" />
                <h3 className="relative font-display text-lg font-bold text-white text-center leading-tight">
                  {course.title}
                </h3>
              </Link>

              <div className="flex-1 p-6 flex flex-col">
                <div className="flex items-start justify-between gap-4 mb-2">
                  <div>
                    <Link
                      to={`/courses/${course.courseId}`}
                      className="font-display text-xl font-bold text-ink-900 hover:text-indigo-800 transition-colors"
                    >
                      {course.title}
                    </Link>
                    <p className="text-sm text-ink-500 mt-1 flex items-center gap-1.5">
                      <CalendarDays size={14} />
                      {dayjs(course.enrolledAt).format('YYYY-MM-DD HH:mm')} 加入学习
                    </p>
                  </div>
                  <span className="badge-green">
                    <Check size={12} />
                    {course.status === 'ACTIVE' ? '已加入' : course.status}
                  </span>
                </div>

                <div className="mt-auto pt-4 flex flex-wrap items-center justify-between gap-3">
                  <Link
                    to={`/courses/${course.courseId}`}
                    className="text-sm text-indigo-800 link-underline"
                  >
                    查看课程详情
                  </Link>
                  <Link
                    to={`/learn/${course.courseId}`}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-indigo-800 text-white text-sm font-medium hover:bg-indigo-900 transition-colors"
                  >
                    <Play size={14} fill="currentColor" />
                    继续学习
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 免费选课推荐（真实课程列表）+ 付费提示 */}
      {freeRecommendations.length > 0 && (
        <section className="mt-14">
          <div className="flex items-end justify-between mb-6">
            <div>
              <span className="section-label mb-3">发现课程</span>
              <h2 className="display-heading text-3xl mt-2">免费课程推荐</h2>
            </div>
            <Link to="/courses" className="text-sm text-indigo-800 link-underline">
              查看全部课程
            </Link>
          </div>

          {enrollError && (
            <p role="alert" className="text-sm text-red-600 mb-4">{enrollError}</p>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {freeRecommendations.map((course: Course) => (
              <div key={course.id} className="card-editorial p-5 flex flex-col">
                <div className="relative aspect-[16/9] overflow-hidden bg-ink-100 mb-4">
                  <CoverImage src={course.coverUrl} title={course.title} />
                </div>
                <Link
                  to={`/courses/${course.id}`}
                  className="font-display text-lg font-bold text-ink-900 hover:text-indigo-800 transition-colors line-clamp-2"
                >
                  {course.title}
                </Link>
                <p className="text-sm text-ink-500 mt-1 mb-4">{displayTeacher(course.teacherName)}</p>
                <div className="mt-auto">
                  {course.enrolled ? (
                    <span className="inline-flex items-center gap-1.5 text-sm text-green-600 font-medium">
                      <Check size={14} /> 已选课
                    </span>
                  ) : (
                    <button
                      type="button"
                      disabled={enrollingId === course.id}
                      onClick={() => void handleEnroll(course.id)}
                      className="btn-primary w-full disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      {enrollingId === course.id ? '正在加入…' : '免费选课'}
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>

          <p className="mt-6 flex items-center gap-2 text-sm text-ink-400">
            <Lock size={14} />
            付费课程可在课程中心查看详情并购买，购买后自动开通学习权限。
            <Link to="/courses" className="text-indigo-800 link-underline">前往课程中心</Link>
          </p>
        </section>
      )}
    </div>
  );
}
