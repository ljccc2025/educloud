import { cover } from '@/services/api';
import type { CourseDetail } from '@/types';

function maskedId(id: string | undefined): string {
  if (!id) return '讲师';
  if (id.length <= 6) return id;
  return `...${id.slice(-6)}`;
}

export default function CheckoutCourseSummary({ course }: { course: CourseDetail }) {
  const price = Number(course.price);
  const coverSrc = course.coverUrl ?? cover(0);

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
          <p className="text-sm text-ink-400">
            {maskedId(course.teachers?.[0]?.teacherId)} · 永久访问
          </p>
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