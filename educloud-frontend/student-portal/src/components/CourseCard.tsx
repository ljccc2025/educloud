import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Star, Users, Play } from 'lucide-react';
import { getCourseCover, getCourseTeacher } from '../utils/courseHelper';
import type { Course } from '../types';

const levelLabel: Record<string, string> = {
  BEGINNER: '入门',
  INTERMEDIATE: '进阶',
  ADVANCED: '高级',
};

function stripHtml(text: string): string {
  return text ? text.replace(/<[^>]*>/g, '') : '';
}

export default function CourseCard({ course, index = 0 }: { course: Course; index?: number }) {
  const [imageFailed, setImageFailed] = useState(false);

  useEffect(() => {
    setImageFailed(false);
  }, [course.coverUrl, course.title]);

  const rawTitle = stripHtml(course.title);
  const isFree = Number(course.price) === 0;
  // 严格匹配课程主题的高清封面
  const coverSrc = imageFailed || !course.coverUrl ? getCourseCover(rawTitle, index) : course.coverUrl;
  // 名师信息与头衔
  const teacher = getCourseTeacher(rawTitle, course.teacherName);

  return (
    <Link
      to={`/courses/${course.id}`}
      className="card-editorial group flex flex-col animate-fade-up opacity-0 shadow-sm hover:shadow-md transition-shadow"
      style={{ animationDelay: `${index * 80}ms` }}
    >
      {/* Cover */}
      <div className="relative aspect-[16/10] overflow-hidden rounded-t-2xl bg-ink-900">
        <img
          src={coverSrc}
          alt={rawTitle}
          loading="lazy"
          onError={() => setImageFailed(true)}
          className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-ink-900/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
        {isFree && (
          <span className="absolute top-3 left-3 badge-green shadow-sm">免费</span>
        )}
        <span className="absolute top-3 right-3 badge bg-white/95 text-ink-800 border border-ink-200 backdrop-blur-sm shadow-sm font-semibold">
          {levelLabel[course.level] ?? course.level}
        </span>
        <div className="absolute bottom-3 right-3 w-10 h-10 bg-white/95 backdrop-blur-sm flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 translate-y-2 group-hover:translate-y-0 rounded-xl shadow-md">
          <Play size={16} className="text-indigo-900 ml-0.5" fill="#1e1b4b" />
        </div>
      </div>

      {/* Body */}
      <div className="flex flex-col flex-1 p-5">
        <div className="flex items-center gap-2 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-amber-800 bg-amber-50 px-2 py-0.5 rounded border border-amber-200/80">
            {course.categoryName}
          </span>
        </div>
        <h3 className="font-display text-base sm:text-lg font-bold text-ink-900 leading-snug mb-2 line-clamp-2 group-hover:text-indigo-800 transition-colors">
          {course.title.includes('<') ? (
            <span dangerouslySetInnerHTML={{ __html: course.title }} />
          ) : (
            course.title
          )}
        </h3>

        {/* Teacher & Enrolled Count */}
        <div className="flex items-center justify-between gap-2 mb-3 pb-3 border-b border-ink-100">
          <div className="flex items-center gap-2 min-w-0">
            <div className="w-7 h-7 rounded-full bg-indigo-100 border border-indigo-200 flex items-center justify-center flex-shrink-0 text-indigo-900 font-bold text-xs">
              {teacher.name.charAt(0)}
            </div>
            <span className="text-xs sm:text-sm font-medium text-ink-700 truncate">{teacher.name}</span>
          </div>
          <span className="text-[11px] font-semibold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200/70 flex-shrink-0">
            {course.enrollmentCount.toLocaleString()} 人已购买
          </span>
        </div>

        {/* Meta */}
        <div className="flex items-center justify-between text-xs text-ink-400 mb-3">
          <span className="flex items-center gap-1">
            <Star size={13} className="text-amber-500 fill-amber-500" />
            <span className="font-bold text-ink-800">
              {course.ratingAvg != null ? Number(course.ratingAvg).toFixed(1) : '5.0'}
            </span>
            <span className="text-ink-400">({course.ratingCount} 评价)</span>
          </span>
          <span className="text-ink-500 font-medium">共 {course.level === 'ADVANCED' ? '12+' : '8+'} 课时</span>
        </div>

        {/* Price */}
        <div className="flex items-baseline gap-2 mt-auto pt-2">
          {isFree ? (
            <span className="font-display text-2xl font-bold text-emerald-600">免费</span>
          ) : (
            <span className="font-display text-2xl font-bold text-indigo-900">¥{course.price}</span>
          )}
        </div>
      </div>
    </Link>
  );
}
