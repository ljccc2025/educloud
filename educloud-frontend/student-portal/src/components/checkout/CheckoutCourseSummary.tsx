import type { Course } from '@/types';

export default function CheckoutCourseSummary({ course }: { course: Course }) {
  const originalAmount = course.originalPrice ?? course.price;
  const discount = originalAmount - course.price;

  return (
    <section
      aria-labelledby="checkout-course-title"
      className="rounded-3xl border border-white/70 bg-white/80 p-6 shadow-xl shadow-indigo-950/5 backdrop-blur-xl"
    >
      <div className="flex flex-col gap-5 sm:flex-row">
        <img
          src={course.cover}
          alt=""
          className="h-32 w-full rounded-2xl object-cover sm:w-52"
        />
        <div className="min-w-0 flex-1">
          <p className="text-sm text-ink-400">
            {course.teacherName} · 永久访问
          </p>
          <h2
            id="checkout-course-title"
            className="mt-2 font-display text-2xl font-bold text-ink-900"
          >
            {course.title}
          </h2>
          <div className="mt-5 flex flex-wrap items-baseline gap-3">
            <strong className="font-display text-3xl text-indigo-800">
              ¥{course.price}
            </strong>
            {discount > 0 && (
              <>
                <span className="text-ink-300 line-through">
                  ¥{originalAmount}
                </span>
                <span className="text-sm text-amber-600">
                  立省 ¥{discount}
                </span>
              </>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
