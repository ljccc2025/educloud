import { useCallback, useEffect, useState } from 'react';
import {
  useParams,
  Link,
  useNavigate,
  useSearchParams,
} from 'react-router-dom';
import {
  Star, Users, Play, ShoppingCart, Check, BookOpen, Send, AlertCircle, Lock, FileText,
} from 'lucide-react';
import dayjs from 'dayjs';
import { useCourseStore } from '@/stores/useCourseStore';
import { useCartStore } from '@/stores/useCartStore';
import { useAuthStore } from '@/stores/useAuthStore';
import { courseApi } from '@/services/courseApi';
import { cartApi } from '@/services/api';
import { recommendationApi } from '@/services/recommendationApi';
import { getCourseCover, getCourseTeacher } from '@/utils/courseHelper';
import { apiErrorText } from '@/services/http';
import { cn } from '@/utils/cn';
import CourseCard from '@/components/CourseCard';
import type { Course, CourseDetail as CourseDetailType, RecommendationItem } from '@/types';

const roleLabel: Record<string, string> = {
  OWNER: '主讲教师',
  CO_TEACHER: '助教',
};

function maskedId(id: string | undefined): string {
  if (!id) return '讲师';
  if (id.length <= 6) return id;
  return `...${id.slice(-6)}`;
}

export default function CourseDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentCourse, chapters, loading, error, fetchCourse, fetchChapters } = useCourseStore();
  const { addToCart, isInCart } = useCartStore();
  const token = useAuthStore((state) => state.token);
  const [added, setAdded] = useState(false);
  const [enrolling, setEnrolling] = useState(false);
  const [purchaseError, setPurchaseError] = useState('');

  // 评价表单
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState('');
  const [reviewSuccess, setReviewSuccess] = useState(false);

  // 相关课程（M13 推荐：course 上下文；接口失败则保持空数组隐藏区块）
  const [related, setRelated] = useState<RecommendationItem[]>([]);
  const isLoggedIn = useAuthStore((s) => s.user !== null);

  useEffect(() => {
    if (!currentCourse?.id) return;
    let cancelled = false;
    setRelated([]); // 课程切换时清空上一门课程的推荐；失败时保持空数组 → 区块隐藏
    recommendationApi
      .getRecommendations('course', currentCourse.id, 6)
      .then((resp) => {
        if (!cancelled) setRelated(resp.items);
      })
      .catch(() => {
        /* 降级：隐藏区块 */
      });
    return () => {
      cancelled = true;
    };
  }, [currentCourse?.id]);

  const handleDislike = (courseId: string) => {
    recommendationApi.dislikeCourse(courseId).catch(() => {});
    setRelated((prev) => prev.filter((c) => c.courseId !== courseId));
  };

  /** 推荐项 → Course 形状（CourseCard 兼容；无 store 时补齐全部字段默认值） */
  const toCourseShape = (item: RecommendationItem): Course => ({
    id: item.courseId,
    title: item.title,
    coverUrl: item.coverUrl || '',
    price: item.price || '0.00',
    teacherName: '',
    categoryName: item.categoryName ?? '',
    level: 'BEGINNER',
    ratingAvg: 0,
    ratingCount: 0,
    enrollmentCount: 0,
    enrolled: false,
  });

  useEffect(() => {
    if (id) {
      void fetchCourse(id);
      void fetchChapters(id);
    }
  }, [id, fetchCourse, fetchChapters]);

  const enrollFreeCourse = useCallback(async () => {
    const course = currentCourse;
    if (!course || Number(course.price) !== 0 || course.enrolled || enrolling) return;

    if (!token) {
      const redirect = `/courses/${course.id}?intent=enroll`;
      navigate(`/login?redirect=${encodeURIComponent(redirect)}`);
      return;
    }

    setEnrolling(true);
    setPurchaseError('');
    try {
      await courseApi.enroll(course.id);
      await fetchCourse(String(course.id));
      navigate(`/learn/${course.id}`);
    } catch (e) {
      setPurchaseError(apiErrorText(e));
    } finally {
      setEnrolling(false);
    }
  }, [currentCourse, enrolling, fetchCourse, navigate, token]);

  useEffect(() => {
    if (
      searchParams.get('intent') === 'enroll' &&
      token &&
      currentCourse &&
      Number(currentCourse.price) === 0 &&
      !currentCourse.enrolled
    ) {
      void enrollFreeCourse();
    }
  }, [currentCourse, enrollFreeCourse, searchParams, token]);

  const submitReview = useCallback(async () => {
    if (!currentCourse || reviewSubmitting) return;
    setReviewSubmitting(true);
    setReviewError('');
    setReviewSuccess(false);
    try {
      await courseApi.submitReview(currentCourse.id, reviewRating, reviewContent.trim());
      setReviewContent('');
      setReviewSuccess(true);
      await fetchCourse(currentCourse.id);
      window.setTimeout(() => setReviewSuccess(false), 2500);
    } catch (e) {
      setReviewError(apiErrorText(e));
    } finally {
      setReviewSubmitting(false);
    }
  }, [currentCourse, fetchCourse, reviewContent, reviewRating, reviewSubmitting]);

  if (loading && !currentCourse) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
      </div>
    );
  }

  if (!currentCourse) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <AlertCircle size={40} className="mx-auto text-red-400 mb-4" />
        <p className="font-display text-xl text-ink-600 mb-2">课程加载失败</p>
        <p className="text-sm text-ink-400 mb-6">{error ?? '课程不存在或已下架'}</p>
        <Link to="/courses" className="btn-primary">返回课程列表</Link>
      </div>
    );
  }

  const course: CourseDetailType = currentCourse;
  const inCart = isInCart(course.id);
  const isFree = Number(course.price) === 0;
  const coverSrc = course.coverUrl || getCourseCover(course.title, 0);
  const mainTeacher = course.teachers?.[0];
  const teacher = getCourseTeacher(course.title, mainTeacher?.teacherId);

  const handleAddToCart = async () => {
    addToCart({
      courseId: course.id,
      title: course.title,
      price: Number(course.price),
      cover: coverSrc,
      teacherName: teacher.name,
    });
    setAdded(true);
    try {
      await cartApi.addToCart(course.id);
    } catch {
      // client store already updated
    }
    window.setTimeout(() => setAdded(false), 2000);
  };

  return (
    <div>
      {/* Course Hero */}
      <section
        className="bg-gradient-to-br from-indigo-900 via-indigo-800 to-ink-900 py-16"
        style={{ backgroundImage: `linear-gradient(rgba(30, 27, 75, 0.82), rgba(30, 27, 75, 0.92)), url(${coverSrc})` }}
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid md:grid-cols-3 gap-10">
            <div className="md:col-span-2 text-white">
              <div className="flex items-center gap-2 mb-4">
                <span className="badge bg-white/20 text-white backdrop-blur-sm">{course.categoryName}</span>
                <span className="badge bg-amber-500 text-white">{course.level}</span>
              </div>
              <h1 className="font-display text-3xl md:text-5xl font-bold leading-tight mb-4">
                {course.title}
              </h1>
              {course.subtitle && (
                <p className="text-base text-white/70 mb-2">{course.subtitle}</p>
              )}
              <p className="text-lg text-white/80 mb-6 max-w-2xl">{course.description}</p>

              <div className="flex flex-wrap items-center gap-6 text-sm text-white/90">
                <span className="flex items-center gap-1.5">
                  <Star size={16} className="fill-amber-400 text-amber-400" />
                  <span className="font-bold">{course.ratingAvg != null ? Number(course.ratingAvg).toFixed(1) : '暂无'}</span>
                  <span className="text-white/60">({course.ratingCount} 评价)</span>
                </span>
                <span className="flex items-center gap-1.5">
                  <Users size={16} />
                  {course.enrollmentCount.toLocaleString()} 名学员
                </span>
                <span className="flex items-center gap-1.5">
                  <BookOpen size={16} />
                  {course.lifecycleStatus === 'PUBLISHED' ? '已发布' : course.lifecycleStatus}
                </span>
              </div>
            </div>

            {/* Price Card */}
            <div className="bg-white p-6 self-start shadow-2xl">
              <div className="flex items-baseline gap-3 mb-1">
                <span className="font-display text-4xl font-bold text-indigo-800">
                  {isFree ? '免费' : `¥${course.price}`}
                </span>
              </div>
              <div className="space-y-3 mb-5 mt-4">
                {course.enrolled ? (
                  <Link
                    to={`/learn/${course.id}`}
                    className="btn-primary w-full"
                  >
                    <Play size={16} />
                    继续学习
                  </Link>
                ) : isFree ? (
                  <button
                    type="button"
                    disabled={enrolling}
                    onClick={enrollFreeCourse}
                    className="btn-primary w-full disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {enrolling ? '正在加入…' : '免费加入学习'}
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={handleAddToCart}
                      className={cn(
                        'w-full py-3 font-medium text-sm transition-all duration-300 flex items-center justify-center gap-2',
                        added || inCart
                          ? 'bg-green-600 text-white'
                          : 'bg-amber-600 text-white hover:bg-amber-500'
                      )}
                    >
                      {added || inCart ? (
                        <><Check size={16} /> 已加入购物车</>
                      ) : (
                        <><ShoppingCart size={16} /> 加入购物车</>
                      )}
                    </button>
                    <button
                      type="button"
                      onClick={() => navigate(`/checkout/${course.id}`)}
                      className="btn-outline w-full"
                    >
                      立即购买
                    </button>
                  </>
                )}
                {purchaseError && (
                  <p role="alert" className="text-sm text-red-600">
                    {purchaseError}
                  </p>
                )}
              </div>
              <div className="border-t border-ink-100 pt-4 space-y-2 text-xs text-ink-500">
                <p className="flex items-center gap-2"><Check size={14} className="text-green-600" /> 永久访问课程内容</p>
                <p className="flex items-center gap-2"><Check size={14} className="text-green-600" /> 完结后获得结业证书</p>
                <p className="flex items-center gap-2"><Check size={14} className="text-green-600" /> 30 天无理由退款</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid md:grid-cols-3 gap-10">
          {/* Left Content */}
          <div className="md:col-span-2 space-y-12">
            {/* Description */}
            <section>
              <h2 className="font-display text-2xl font-bold text-ink-900 mb-5 flex items-center gap-3">
                <span className="w-1 h-6 bg-amber-600" />
                课程简介
              </h2>
              <p className="text-ink-600 leading-relaxed">{course.description}</p>
              <p className="text-xs text-ink-400 mt-4">
                课程编号：{course.id} · 分类：{course.categoryName} · 币种：{course.currency}
              </p>
            </section>

            {/* Chapters & Syllabus */}
            <section>
              <h2 className="font-display text-2xl font-bold text-ink-900 mb-5 flex items-center gap-3">
                <span className="w-1 h-6 bg-amber-600" />
                课程大纲
              </h2>
              {chapters.length > 0 ? (
                <div className="border border-ink-100 divide-y divide-ink-100 bg-white shadow-sm">
                  {chapters.map((ch, chIdx) => (
                    <div key={ch.id} className="p-5">
                      <div className="flex items-center justify-between mb-3">
                        <div>
                          <span className="text-xs font-bold text-indigo-900 block mb-0.5">第 {chIdx + 1} 章</span>
                          <h3 className="font-bold text-ink-900 text-sm">{ch.title}</h3>
                          {ch.description && (
                            <p className="text-xs text-ink-400 mt-0.5">{ch.description}</p>
                          )}
                        </div>
                        <span className="text-xs text-ink-400 font-medium">
                          {ch.coursewares.length} 个课件
                        </span>
                      </div>

                      <div className="space-y-2 pl-2">
                        {ch.coursewares.map((cw) => {
                          const isLocked = !isFree && !course.enrolled && !cw.freePreview;
                          return (
                            <div
                              key={cw.id}
                              className="flex items-center justify-between p-3 rounded-lg bg-ink-50/70 border border-ink-100 text-xs"
                            >
                              <div className="flex items-center gap-2.5 min-w-0 pr-3">
                                <div className="w-6 h-6 rounded flex items-center justify-center bg-ink-100 text-ink-600 flex-shrink-0">
                                  {isLocked ? (
                                    <Lock size={12} className="text-amber-700" />
                                  ) : cw.coursewareType === 'VIDEO' ? (
                                    <Play size={12} />
                                  ) : (
                                    <FileText size={12} />
                                  )}
                                </div>
                                <span className="text-ink-800 font-medium truncate">{cw.title}</span>
                              </div>

                              <div className="flex items-center gap-2 flex-shrink-0">
                                {cw.durationSeconds ? (
                                  <span className="text-ink-400 text-[11px]">
                                    {Math.ceil(cw.durationSeconds / 60)} 分钟
                                  </span>
                                ) : null}

                                {cw.freePreview ? (
                                  <Link
                                    to={`/learn/${course.id}?cw=${cw.id}`}
                                    className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded text-[11px] transition-colors flex items-center gap-1 shadow-sm"
                                  >
                                    <Play size={10} fill="currentColor" />
                                    免费试看
                                  </Link>
                                ) : isLocked ? (
                                  <span className="px-2 py-0.5 bg-amber-100 text-amber-800 rounded font-medium text-[11px] flex items-center gap-1">
                                    <Lock size={10} />
                                    需购买
                                  </span>
                                ) : (
                                  <Link
                                    to={`/learn/${course.id}?cw=${cw.id}`}
                                    className="px-2.5 py-1 bg-indigo-900 hover:bg-indigo-800 text-white font-medium rounded text-[11px] transition-colors"
                                  >
                                    开始学习
                                  </Link>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="border border-ink-100 bg-ink-50/40 px-6 py-14 text-center">
                  <BookOpen size={36} className="mx-auto text-ink-200 mb-3" strokeWidth={1} />
                  <div className="whitespace-pre-line text-sm text-ink-700 leading-relaxed">
                    {course.description || '课程大纲整理中，敬请期待'}
                  </div>
                  <p className="text-sm text-ink-400 mt-2">
                    章节与课件内容正在整理中
                  </p>
                </div>
              )}
            </section>

            {/* Reviews */}
            <section>
              <h2 className="font-display text-2xl font-bold text-ink-900 mb-5 flex items-center gap-3">
                <span className="w-1 h-6 bg-amber-600" />
                学员评价
              </h2>

              {/* Review Form (enrolled only) */}
              {course.enrolled && (
                <div className="card-editorial p-5 mb-6">
                  <h3 className="font-display text-lg font-bold text-ink-900 mb-3">写下你的评价</h3>
                  <div className="flex items-center gap-1 mb-3">
                    {Array.from({ length: 5 }).map((_, i) => (
                      <button
                        key={i}
                        type="button"
                        aria-label={`${i + 1} 星`}
                        onClick={() => setReviewRating(i + 1)}
                        className="p-0.5"
                      >
                        <Star
                          size={22}
                          className={i < reviewRating
                            ? 'fill-amber-500 text-amber-500'
                            : 'text-ink-200 hover:text-amber-300'}
                        />
                      </button>
                    ))}
                    <span className="ml-2 text-sm text-ink-500">{reviewRating} 星</span>
                  </div>
                  <textarea
                    value={reviewContent}
                    onChange={(e) => setReviewContent(e.target.value)}
                    rows={3}
                    maxLength={500}
                    placeholder="分享你的学习体验（选填，最多 500 字）"
                    className="w-full border border-ink-200 p-3 text-sm text-ink-800 placeholder:text-ink-400 focus:outline-none focus:border-indigo-800 resize-y"
                  />
                  <div className="mt-3 flex items-center justify-between">
                    {reviewError && <p role="alert" className="text-sm text-red-600">{reviewError}</p>}
                    {reviewSuccess && <p className="text-sm text-green-600">评价已提交，感谢反馈！</p>}
                    <button
                      type="button"
                      disabled={reviewSubmitting || reviewRating < 1}
                      onClick={submitReview}
                      className="btn-primary !px-5 !py-2 text-sm ml-auto disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      <Send size={14} />
                      {reviewSubmitting ? '提交中…' : '提交评价'}
                    </button>
                  </div>
                </div>
              )}

              {course.reviews.length === 0 ? (
                <div className="card-editorial p-8 text-center text-ink-400">
                  <p>暂无评价，快来抢沙发～</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {course.reviews.map((review) => (
                    <div key={review.id} className="card-editorial p-5">
                      <div className="flex items-start gap-4">
                        <div className="w-10 h-10 rounded-full bg-indigo-100 flex items-center justify-center flex-shrink-0">
                          <span className="text-sm font-semibold text-indigo-800">学</span>
                        </div>
                        <div className="flex-1">
                          <div className="flex items-center justify-between mb-1">
                            <span className="font-medium text-ink-900 text-sm">
                              学员 {maskedId(review.studentId)}
                            </span>
                            <span className="text-xs text-ink-400">
                              {dayjs(review.createdAt).format('YYYY-MM-DD')}
                            </span>
                          </div>
                          <div className="flex items-center gap-0.5 mb-2">
                            {Array.from({ length: 5 }).map((_, i) => (
                              <Star
                                key={i}
                                size={14}
                                className={i < review.rating
                                  ? 'fill-amber-500 text-amber-500'
                                  : 'text-ink-200'}
                              />
                            ))}
                          </div>
                          <p className="text-sm text-ink-600 leading-relaxed">{review.content || '（无文字评价）'}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* Right Sidebar */}
          <aside className="space-y-6">
            {/* Teacher Card */}
            <div className="card-editorial p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-display text-lg font-bold text-ink-900">主讲讲师</h3>
                <span className="text-xs font-semibold text-amber-800 bg-amber-50 px-2.5 py-0.5 rounded-full border border-amber-200/80">
                  {course.enrollmentCount.toLocaleString()} 人已加入
                </span>
              </div>
              <div className="flex items-center gap-4 mb-4">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-indigo-700 to-indigo-900 flex items-center justify-center shadow-md flex-shrink-0">
                  <span className="text-xl font-bold text-white">{teacher.name.charAt(0)}</span>
                </div>
                <div className="min-w-0">
                  <p className="font-bold text-ink-900 text-base">{teacher.name}</p>
                  <p className="text-xs text-ink-500 mt-0.5 leading-tight">{teacher.title}</p>
                </div>
              </div>
              <p className="text-xs text-ink-600 leading-relaxed bg-ink-50 p-3 rounded-xl border border-ink-100">
                资深架构师与名师团队，主导过多款高并发分布式系统与企业级全栈产品架构研发。
              </p>
            </div>

            {/* Enrolled note */}
            {course.enrolled && (
              <div className="card-editorial p-6">
                <h3 className="font-display text-lg font-bold text-ink-900 mb-3">学习进度</h3>
                <p className="text-sm text-ink-500 flex items-start gap-2">
                  <Lock size={14} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  学习进度将在内容模块（章节/课件）上线后展示
                </p>
              </div>
            )}

            {/* Features */}
            <div className="card-editorial p-6">
              <h3 className="font-display text-lg font-bold text-ink-900 mb-4">课程特色</h3>
              <ul className="space-y-3 text-sm text-ink-600">
                <li className="flex items-start gap-3">
                  <Check size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  完成课程获得认证证书
                </li>
                <li className="flex items-start gap-3">
                  <BookOpen size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  {course.enrollmentCount.toLocaleString()} 名学员共同学习
                </li>
                <li className="flex items-start gap-3">
                  <Users size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  社群答疑与学习交流
                </li>
              </ul>
            </div>
          </aside>
        </div>
      </div>

      {/* Related Courses（M13 推荐：仅在有推荐结果时渲染） */}
      {related.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <h2 className="display-heading text-3xl mb-8">相关课程</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {related.map((item, i) => (
              <div key={item.courseId} className="group relative">
                {isLoggedIn && (
                  <button
                    onClick={() => handleDislike(item.courseId)}
                    title="不感兴趣"
                    aria-label="不感兴趣"
                    className="absolute -top-2 -right-2 z-10 w-7 h-7 rounded-full bg-white shadow border border-ink-200 text-ink-400 hover:text-red-500 hover:border-red-300 flex items-center justify-center text-sm opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    ✕
                  </button>
                )}
                <CourseCard course={toCourseShape(item)} />
                {item.reason && (
                  <p className="mt-1.5 text-xs text-ink-400">{item.reason}</p>
                )}
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
