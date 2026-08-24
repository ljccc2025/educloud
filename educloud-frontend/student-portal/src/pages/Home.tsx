import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  ArrowRight,
  ArrowUpRight,
  Award,
  BarChart3,
  BookOpen,
  Brain,
  Calculator,
  Languages,
  Layers,
  Lightbulb,
  Monitor,
  Music2,
  Palette,
  PenTool,
  PlayCircle,
  Scale,
  Server,
  Boxes,
  Database,
  Terminal,
  TrendingUp,
  Users,
} from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import { courseApi } from '@/services/courseApi';
import CourseCard from '@/components/CourseCard';
import type { Category } from '@/types';
import SideRays from '@/components/SideRays/SideRays';

const stats = [
  { icon: BookOpen, value: '200+', label: '精品课程', num: '01' },
  { icon: Users, value: '50,000+', label: '注册学员', num: '02' },
  { icon: Award, value: '120+', label: '认证讲师', num: '03' },
  { icon: TrendingUp, value: '98%', label: '好评率', num: '04' },
];

// 真实分类（种子：前端开发/后端开发/数据分析 + 各 2 个子分类；课程挂载在叶子分类上）
const categoryIcons: Record<string, LucideIcon> = {
  前端开发: Monitor,
  后端开发: Server,
  数据分析: BarChart3,
  'Web 基础': Monitor,
  'Vue 前端框架': Layers,
  'Java 后端': Server,
  'Spring Boot 微服务': Boxes,
  'SQL 数据分析': Database,
  'Python 数据分析': Terminal,
};

export default function Home() {
  const { courses, fetchCourses } = useCourseStore();
  const [leafCategories, setLeafCategories] = useState<Category[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(true);

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  // 真实分类（GET /api/v1/categories）：取叶子分类保证深链到课程列表有结果
  useEffect(() => {
    let cancelled = false;
    courseApi
      .getCategories()
      .then((tree) => {
        if (cancelled) return;
        const leaves: Category[] = [];
        const walk = (nodes: Category[]) => {
          nodes.forEach((node) => {
            if (node.children && node.children.length > 0) walk(node.children);
            else leaves.push(node);
          });
        };
        walk(tree);
        setLeafCategories(leaves);
      })
      .catch(() => {
        if (!cancelled) setLeafCategories([]);
      })
      .finally(() => {
        if (!cancelled) setCategoriesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const featuredCourses = courses.slice(0, 6);

  return (
    <div>
      {/* Hero Section */}
      <section
        data-home-hero
        className="relative -mt-[68px] overflow-hidden border-b border-ink-100 bg-paper pt-[68px] dark:border-ink-800 dark:bg-ink-900"
      >
        <SideRays
          speed={1.1}
          rayColor1="#EAB308"
          rayColor2="#96c8ff"
          intensity={1.25}
          spread={1.8}
          origin="top-right"
          tilt={0}
          saturation={1.2}
          blend={0.72}
          falloff={1.7}
          opacity={0.62}
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 z-10 bg-gradient-to-b from-paper/20 via-paper/35 to-paper/95 dark:from-ink-900/20 dark:via-ink-900/35 dark:to-ink-900/95"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 z-10 bg-gradient-to-br from-transparent via-transparent to-amber-50/20 dark:to-indigo-900/10"
        />
        <div className="relative z-20 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-32">
          <div className="grid md:grid-cols-12 gap-12 items-center">
            <div className="md:col-span-7 animate-fade-up">
              <span className="section-label mb-6">EduCloud 学生端 · 在线学习平台</span>
              <h1 className="display-heading text-5xl md:text-7xl lg:text-8xl mt-6 mb-6">
                开启你的
                <br />
                <span className="text-indigo-800 italic">学习之旅</span>
              </h1>
              <p className="text-lg text-ink-500 max-w-xl leading-relaxed mb-10 animate-fade-up animation-delay-200">
                汇聚行业顶尖讲师，提供从入门到精通的系统化课程。无论你是编程新手还是资深开发者，都能在这里找到适合自己的成长路径。
              </p>
              <div className="flex flex-wrap gap-4 animate-fade-up animation-delay-300">
                <Link to="/courses" className="btn-primary">
                  浏览全部课程
                  <ArrowRight size={16} />
                </Link>
                <Link to="/my-courses" className="btn-outline">
                  <PlayCircle size={16} />
                  继续学习
                </Link>
              </div>
            </div>

            <div className="md:col-span-5 animate-fade-in animation-delay-300">
              <div className="relative">
                <div className="relative overflow-hidden bg-white border border-ink-100 p-8 shadow-2xl shadow-indigo-900/5 rounded-2xl">
                  <span className="pointer-events-none absolute -top-3 -right-1 font-display text-8xl font-black text-indigo-800/[0.05] leading-none select-none">01</span>
                  <div className="relative">
                  <div className="flex items-center gap-3 mb-6">
                    <div className="w-10 h-10 bg-indigo-800 rounded-xl flex items-center justify-center">
                      <BookOpen size={20} className="text-paper" />
                    </div>
                    <div>
                      <p className="font-display font-bold text-ink-900">正在学习</p>
                      <p className="text-xs text-ink-400">React 18 从入门到精通</p>
                    </div>
                  </div>
                  <div className="space-y-3">
                    <div className="flex justify-between text-sm">
                      <span className="text-ink-600">课程进度</span>
                      <span className="font-semibold text-indigo-800">65%</span>
                    </div>
                    <div className="progress-track">
                      <div className="progress-fill" style={{ width: '65%' }} />
                    </div>
                  </div>
                  <div className="grid grid-cols-3 gap-4 mt-6 pt-6 border-t border-ink-100">
                    <div>
                      <p className="font-display text-2xl font-bold text-ink-900">42</p>
                      <p className="text-xs text-ink-400">课时</p>
                    </div>
                    <div>
                      <p className="font-display text-2xl font-bold text-ink-900">12.5K</p>
                      <p className="text-xs text-ink-400">学员</p>
                    </div>
                    <div>
                      <p className="font-display text-2xl font-bold text-amber-600">4.9</p>
                      <p className="text-xs text-ink-400">评分</p>
                    </div>
                  </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="border-b border-ink-100">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            {stats.map((stat) => (
              <div key={stat.label} className="stat-card relative">
                <span className="absolute top-3 right-4 font-display text-3xl font-black text-ink-100">
                  {stat.num}
                </span>
                <stat.icon size={24} className="text-amber-600 mb-3" strokeWidth={1.5} />
                <p className="font-display text-3xl font-bold text-ink-900">{stat.value}</p>
                <p className="text-sm text-ink-500 mt-1">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Featured Courses */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
        <div className="flex items-end justify-between mb-12">
          <div>
            <span className="section-label mb-4">精选课程</span>
            <h2 className="display-heading text-4xl md:text-5xl mt-4">
              热门推荐
            </h2>
          </div>
          <Link
            to="/courses"
            className="hidden sm:flex items-center gap-2 text-sm font-medium text-indigo-800 link-underline"
          >
            查看全部
            <ArrowRight size={14} />
          </Link>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {featuredCourses.map((course, i) => (
            <div
              key={course.id}
              className={`animate-fade-up animation-delay-${(i % 3 + 1) * 100}`}
            >
              <CourseCard course={course} />
            </div>
          ))}
        </div>
      </section>

      {/* Categories Section */}
      <section
        data-home-category-section
        className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20"
      >
        <div className="relative">
          <div className="relative grid gap-5 mb-10 md:grid-cols-[minmax(0,1fr)_minmax(18rem,28rem)] md:items-end md:gap-12">
            <div>
              <span className="section-label mb-4">课程分类</span>
              <h2 className="display-heading text-4xl md:text-5xl mt-4">
                探索你的方向
              </h2>
            </div>
            <div className="flex flex-col gap-3 md:items-end">
              <span
                aria-hidden="true"
                data-home-category-number
                className="section-number pointer-events-none self-end text-right"
              >
                02
              </span>
              <p
                data-home-category-description
                className="max-w-md text-sm leading-7 text-ink-500"
              >
                从感兴趣的领域开始，找到适合当前阶段的课程，把注意力留给真正想学习的内容。
              </p>
            </div>
          </div>

          <div
            data-home-category-grid
            className="relative grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 md:gap-4"
          >
            {leafCategories.length === 0 ? (
              <p className="text-sm text-ink-400 col-span-full text-center py-8">
                {categoriesLoading ? '分类加载中…' : '分类暂不可用'}
              </p>
            ) : (
              leafCategories.map((cat) => {
                const Icon = categoryIcons[cat.name] ?? BookOpen;
                return (
                  <Link
                    key={cat.id}
                    data-home-category-card
                    to={`/courses?category=${encodeURIComponent(cat.name)}`}
                    className="card-editorial group relative flex min-h-[150px] flex-col justify-between p-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-2 motion-reduce:transition-none"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-800 transition-colors duration-200 group-hover:bg-amber-50 group-hover:text-amber-700 motion-reduce:transition-none">
                        <Icon size={20} strokeWidth={1.7} aria-hidden="true" />
                      </span>
                      <ArrowUpRight
                        size={17}
                        strokeWidth={1.5}
                        aria-hidden="true"
                        className="text-ink-300 transition-all duration-200 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-amber-600 motion-reduce:transition-none motion-reduce:!transform-none"
                      />
                    </div>
                    <div>
                      <p className="font-display text-base font-bold text-ink-900">
                        {cat.name}
                      </p>
                      <p className="mt-1 text-xs text-ink-400">查看课程</p>
                    </div>
                  </Link>
                );
              })
            )}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section
        data-home-cta-section
        className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20"
      >
        <div
          data-home-cta-panel
          className="relative overflow-hidden rounded-3xl border border-amber-100 bg-gradient-to-br from-amber-50/90 via-white to-indigo-50/80 p-8 shadow-[0_20px_60px_rgba(30,27,75,0.06)] md:p-10 lg:p-12"
        >
          <div
            aria-hidden="true"
            data-home-cta-decoration
            className="pointer-events-none absolute -right-20 -top-24 h-64 w-64 rounded-full bg-amber-200/25 blur-3xl"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -bottom-28 right-32 h-64 w-64 rounded-full bg-indigo-200/25 blur-3xl"
          />
          <div
            data-home-cta-content
            className="relative grid gap-8 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-12"
          >
            <div className="max-w-2xl">
              <h2 className="font-display text-3xl font-bold text-ink-900 md:text-4xl">
                准备好开始学习了吗？
              </h2>
              <p className="mt-4 leading-relaxed text-ink-500">
                加入 50,000+ 名学员，在 EduCloud 开启你的技术成长之路，海量精品课程随心学。
              </p>
            </div>
            <Link
              data-home-cta-link
              to="/courses"
              className="group inline-flex min-h-11 items-center justify-center gap-2 self-start rounded-xl bg-indigo-800 px-7 py-3.5 text-sm font-medium text-white transition-all duration-200 hover:bg-indigo-900 hover:shadow-lg hover:shadow-indigo-800/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-2 focus-visible:ring-offset-white motion-reduce:transition-none md:self-auto"
            >
              立即开始
              <ArrowRight
                size={16}
                aria-hidden="true"
                className="transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transition-none motion-reduce:!transform-none"
              />
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}