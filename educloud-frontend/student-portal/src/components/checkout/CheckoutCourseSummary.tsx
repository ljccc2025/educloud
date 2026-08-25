import { getCourseCover, getCourseTeacher } from '@/utils/courseHelper';
import type { CourseDetail } from '@/types';

export default function CheckoutCourseSummary({ course }: { course: CourseDetail }) {
  const price = Number(course.price);
  const coverSrc = course.coverUrl || getCourseCover(course.title, 0);
  const teacher = getCourseTeacher(course.title, course.teachers?.[0]?.teacherId);

  return (
    <section
      aria-labelledby="checkout-course-title"
      className="rounded-3xl border border-white/70 bg-white/80 p-6 shadow-xl shadow-indigo-950/5 backdrop-blur-xl"
    >
      <div className="flex flex-col gap-5 sm:flex-row">
        <img
          src={coverSrc}
          alt=""
          className="h-32 w-full rounded-2xl object-cover sm:w-52"
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-indigo-900">
              {teacher.name}
            </p>
            <span className="text-xs text-ink-400">· {course.enrollmentCount.toLocaleString()} 人已购买 · 永久有效</span>
          </div>
          <h2
            id="checkout-course-title"
            className="mt-2 font-display text-2xl font-bold text-ink-900"
          >
            {course.title}
          </h2>
          <div className="mt-5 flex flex-wrap items-baseline gap-3">
            <strong className="font-display text-3xl text-indigo-800">
              {price === 0 ? '免费' : `¥${course.price}`}
            </strong>
            {price > 0 && (
              <span className="text-sm text-ink-400">{course.currency ?? 'CNY'}</span>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}