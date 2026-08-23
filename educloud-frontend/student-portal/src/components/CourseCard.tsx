import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Star, Users, Play } from 'lucide-react';
import { cover } from '../services/api';
import type { Course } from '../types';

const levelLabel: Record<string, string> = {
  BEGINNER: '入门',
  INTERMEDIATE: '进阶',
  ADVANCED: '高级',
};

export default function CourseCard({ course, index = 0 }: { course: Course; index?: number }) {
  const [imageFailed, setImageFailed] = useState(false);
  const isFree = Number(course.price) === 0;
  const coverSrc = imageFailed || !course.coverUrl ? cover(index) : course.coverUrl;

  return (
    <Link
      to={`/courses/${course.id}`}
      className="card-editorial group flex flex-col animate-fade-up opacity-0"
      style={{ animationDelay: `${index * 80}ms` }}
    >
      {/* Cover */}
      <div className="relative aspect-[16/10] overflow-hidden rounded-t-2xl bg-ink-100">
        <img
          src={coverSrc}
          alt={course.title}
          loading="lazy"
          className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-ink-900/40 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
        {isFree && (
          <span className="absolute top-3 left-3 badge-green">免费</span>
        )}
        <span className="absolute top-3 right-3 badge bg-white/90 text-ink-700 border border-ink-200 backdrop-blur-sm">
          {levelLabel[course.level] ?? course.level}
        </span>
        <div className="absolute bottom-3 right-3 w-10 h-10 bg-white/90 backdrop-blur-sm flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 translate-y-2 group-hover:translate-y-0">
          <Play size={16} className="text-indigo-800 ml-0.5" fill="#1e1b4b" />
        </div>
      </div>

      {/* Body */}
      <div className="flex flex-col flex-1 p-5">
        <div className="flex items-center gap-2 mb-2">
          <span className="text-xs font-medium uppercase tracking-wider text-amber-700 bg-amber-50 px-2 py-0.5 border border-amber-200">
            {course.categoryName}
          </span>
        </div>
        <h3 className="font-display text-lg font-600 text-ink-900 leading-snug mb-2 line-clamp-2 group-hover:text-indigo-800 transition-colors">
          {course.title}
        </h3>

        {/* Teacher */}
        <div className="flex items-center gap-2 mb-3 pb-3 border-b border-ink-50">
          <div className="w-7 h-7 bg-indigo-100 border border-ink-200 flex items-center justify-center">
            <span className="text-xs font-semibold text-indigo-800">
              {(course.teacherName || '讲师').charAt(0)}
            </span>
          </div>
          <span className="text-sm text-ink-600">{course.teacherName || '讲师'}</span>
        </div>

        {/* Meta */}
        <div className="flex items-center gap-3 text-xs text-ink-400 mb-3">
          <span className="flex items-center gap-1">
            <Star size={13} className="text-amber-500" fill="#f59e0b" />
            <span className="font-medium text-ink-700">
              {course.ratingAvg != null ? Number(course.ratingAvg).toFixed(1) : '暂无'}
            </span>
          </span>
          <span className="flex items-center gap-1">
            <Users size={13} />
            {(course.enrollmentCount / 1000).toFixed(1)}k
          </span>
          <span>{course.ratingCount} 条评价</span>
        </div>

        {/* Price */}
        <div className="flex items-baseline gap-2 mt-auto">
          {isFree ? (
            <span className="font-display text-2xl font-700 text-green-700">免费</span>
          ) : (
            <span className="font-display text-2xl font-700 text-indigo-800">¥{course.price}</span>
          )}
        </div>
      </div>
    </Link>
  );
}
