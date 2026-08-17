import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Star, Users, Clock, Play, FileText, HelpCircle, ChevronDown,
  ShoppingCart, Check, Award, BookOpen,
} from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import { useCartStore } from '@/stores/useCartStore';
import ProgressBar from '@/components/ProgressBar';
import { cn } from '@/utils/cn';

export default function CourseDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { currentCourse, loading, fetchCourse } = useCourseStore();
  const { addToCart, isInCart } = useCartStore();
  const [openChapter, setOpenChapter] = useState<number | null>(1);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    if (id) fetchCourse(id);
  }, [id, fetchCourse]);

  if (loading || !currentCourse) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
      </div>
    );
  }

  const course = currentCourse;
  const inCart = isInCart(course.id);
  const totalCoursewares = course.chapters.reduce(
    (sum, ch) => sum + ch.coursewares.length, 0
  );
  const completedCoursewares = course.chapters.reduce(
    (sum, ch) => sum + ch.coursewares.filter((cw) => cw.completed).length, 0
  );

  const handleAddToCart = () => {
    addToCart({
      courseId: course.id,
      title: course.title,
      price: course.price,
      cover: course.cover,
      teacherName: course.teacherName,
    });
    setAdded(true);
    setTimeout(() => setAdded(false), 2000);
  };

  const getCoursewareIcon = (type: string) => {
    switch (type) {
      case 'video': return <Play size={14} />;
      case 'quiz': return <HelpCircle size={14} />;
      default: return <FileText size={14} />;
    }
  };

  return (
    <div>
      {/* Course Hero */}
      <section
        className="bg-gradient-to-br from-indigo-900 via-indigo-800 to-ink-900 py-16"
        style={{ backgroundImage: `linear-gradient(rgba(30, 27, 75, 0.82), rgba(30, 27, 75, 0.92)), url(${course.cover})` }}
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid md:grid-cols-3 gap-10">
            <div className="md:col-span-2 text-white">
              <div className="flex items-center gap-2 mb-4">
                <span className="badge bg-white/20 text-white backdrop-blur-sm">{course.category}</span>
                <span className="badge bg-amber-500 text-white">{course.level}</span>
              </div>
              <h1 className="font-display text-3xl md:text-5xl font-bold leading-tight mb-4">
                {course.title}
              </h1>
              <p className="text-lg text-white/80 mb-6 max-w-2xl">{course.description}</p>

              <div className="flex flex-wrap items-center gap-6 text-sm text-white/90">
                <span className="flex items-center gap-1.5">
                  <Star size={16} className="fill-amber-400 text-amber-400" />
                  <span className="font-bold">{course.rating}</span>
                  <span className="text-white/60">({course.reviewCount} 评价)</span>
                </span>
                <span className="flex items-center gap-1.5">
                  <Users size={16} />
                  {course.studentCount.toLocaleString()} 名学员
                </span>
                <span className="flex items-center gap-1.5">
                  <Clock size={16} />
                  {course.totalDuration}
                </span>
                <span className="flex items-center gap-1.5">
                  <BookOpen size={16} />
                  {course.chapters.length} 章 · {totalCoursewares} 节
                </span>
              </div>
            </div>

            {/* Price Card */}
            <div className="bg-white p-6 self-start shadow-2xl">
              <div className="flex items-baseline gap-3 mb-1">
                <span className="font-display text-4xl font-bold text-indigo-800">¥{course.price}</span>
                {course.originalPrice !== undefined && (
                  <span className="text-lg text-ink-300 line-through">¥{course.originalPrice}</span>
                )}
              </div>
              {course.originalPrice !== undefined && (
                <p className="text-xs text-amber-600 font-medium mb-5">
                  限时优惠，立省 ¥{course.originalPrice - course.price}
                </p>
              )}
              <div className="space-y-3 mb-5">
                {course.enrolled ? (
                  <Link
                    to={`/learn/${course.id}`}
                    className="btn-primary w-full"
                  >
                    <Play size={16} />
                    继续学习
                  </Link>
                ) : (
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
                )}
                <button
                  type="button"
                  onClick={() => {
                    handleAddToCart();
                    navigate('/courses');
                  }}
                  className="btn-outline w-full"
                >
                  立即购买
                </button>
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
              <div className="flex flex-wrap gap-2 mt-5">
                {course.whatYouLearn.map((tag) => (
                  <span key={tag} className="badge-indigo">{tag}</span>
                ))}
              </div>
            </section>

            {/* Chapters Accordion */}
            <section>
              <h2 className="font-display text-2xl font-bold text-ink-900 mb-5 flex items-center gap-3">
                <span className="w-1 h-6 bg-amber-600" />
                课程大纲
              </h2>
              <div className="border border-ink-100">
                {course.chapters.map((chapter, idx) => (
                  <div key={chapter.id} className="border-b border-ink-100 last:border-b-0">
                    <button
                      type="button"
                      onClick={() =>
                        setOpenChapter(openChapter === chapter.id ? null : chapter.id)
                      }
                      className="w-full flex items-center justify-between px-5 py-4 hover:bg-ink-50/50 transition-colors text-left"
                    >
                      <div className="flex items-center gap-3">
                        <span className="font-display text-lg font-bold text-indigo-800/60 w-8">
                          {String(idx + 1).padStart(2, '0')}
                        </span>
                        <div>
                          <span className="font-medium text-ink-900">{chapter.title}</span>
                          <span className="text-xs text-ink-400 ml-3">
                            {chapter.coursewares.length} 节
                          </span>
                        </div>
                      </div>
                      <ChevronDown
                        size={18}
                        className={cn(
                          'text-ink-400 transition-transform flex-shrink-0',
                          openChapter === chapter.id && 'rotate-180'
                        )}
                      />
                    </button>
                    {openChapter === chapter.id && (
                      <div className="bg-ink-50/30 animate-fade-in">
                        {chapter.coursewares.map((cw) => (
                          <div
                            key={cw.id}
                            className="flex items-center justify-between px-5 py-3 border-t border-ink-100 hover:bg-white transition-colors"
                          >
                            <div className="flex items-center gap-3">
                              <span className={cn(
                                'flex items-center justify-center w-7 h-7',
                                cw.completed ? 'text-green-600' : 'text-ink-400'
                              )}>
                                {getCoursewareIcon(cw.type)}
                              </span>
                              <span className={cn(
                                'text-sm',
                                cw.completed ? 'text-ink-400 line-through' : 'text-ink-700'
                              )}>
                                {cw.title}
                              </span>
                            </div>
                            <div className="flex items-center gap-3">
                              {cw.completed && <Check size={14} className="text-green-600" />}
                              <span className="text-xs text-ink-400">{cw.duration} 分钟</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </section>

            {/* Reviews */}
            <section>
              <h2 className="font-display text-2xl font-bold text-ink-900 mb-5 flex items-center gap-3">
                <span className="w-1 h-6 bg-amber-600" />
                学员评价
              </h2>
              <div className="space-y-4">
                {course.reviews.map((review) => (
                  <div key={review.id} className="card-editorial p-5">
                    <div className="flex items-start gap-4">
                      <div className="w-10 h-10 bg-indigo-100 flex items-center justify-center flex-shrink-0">
                        <span className="text-sm font-semibold text-indigo-800">
                          {review.userName.charAt(0)}
                        </span>
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-medium text-ink-900 text-sm">{review.userName}</span>
                          <span className="text-xs text-ink-400">{review.date}</span>
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
                        <p className="text-sm text-ink-600 leading-relaxed">{review.content}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          </div>

          {/* Right Sidebar */}
          <aside className="space-y-6">
            {/* Teacher Card */}
            <div className="card-editorial p-6">
              <h3 className="font-display text-lg font-bold text-ink-900 mb-4">讲师介绍</h3>
              <div className="flex items-center gap-4 mb-4">
                <div className="w-14 h-14 bg-gradient-to-br from-indigo-700 to-indigo-900 flex items-center justify-center">
                  <span className="text-xl font-bold text-paper">
                    {course.teacherName.charAt(0)}
                  </span>
                </div>
                <div>
                  <p className="font-semibold text-ink-900">{course.teacherName}</p>
                  <p className="text-sm text-ink-500">{course.teacherTitle}</p>
                </div>
              </div>
            </div>

            {/* Progress Card (if enrolled) */}
            {course.enrolled && (
              <div className="card-editorial p-6">
                <h3 className="font-display text-lg font-bold text-ink-900 mb-4">学习进度</h3>
                <ProgressBar progress={course.progress} showLabel />
                <div className="mt-4 grid grid-cols-2 gap-4 text-center">
                  <div>
                    <p className="font-display text-2xl font-bold text-indigo-800">
                      {completedCoursewares}/{totalCoursewares}
                    </p>
                    <p className="text-xs text-ink-400">已完成课时</p>
                  </div>
                  <div>
                    <p className="font-display text-2xl font-bold text-amber-600">
                      {course.progress}%
                    </p>
                    <p className="text-xs text-ink-400">总进度</p>
                  </div>
                </div>
              </div>
            )}

            {/* Features */}
            <div className="card-editorial p-6">
              <h3 className="font-display text-lg font-bold text-ink-900 mb-4">课程特色</h3>
              <ul className="space-y-3 text-sm text-ink-600">
                <li className="flex items-start gap-3">
                  <Award size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  完成课程获得认证证书
                </li>
                <li className="flex items-start gap-3">
                  <Play size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  {course.totalDuration} 高清视频
                </li>
                <li className="flex items-start gap-3">
                  <FileText size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
                  配套源码与学习资料
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
    </div>
  );
}
