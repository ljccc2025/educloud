import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, BookOpen, Award, Users, TrendingUp, PlayCircle } from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import { categories } from '@/services/api';
import CourseCard from '@/components/CourseCard';

const stats = [
  { icon: BookOpen, value: '200+', label: '精品课程', num: '01' },
  { icon: Users, value: '50,000+', label: '注册学员', num: '02' },
  { icon: Award, value: '120+', label: '认证讲师', num: '03' },
  { icon: TrendingUp, value: '98%', label: '好评率', num: '04' },
];

const categoryIcons: Record<string, string> = {
  '前端开发': '</>',
  '后端开发': '{ }',
  '移动开发': 'iOS',
  '数据科学': 'Py',
  '云计算': 'K8s',
  '人工智能': 'ML',
};

export default function Home() {
  const { courses, fetchCourses } = useCourseStore();

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  const featuredCourses = courses.slice(0, 6);

  return (
    <div>
      {/* Hero Section */}
      <section className="relative overflow-hidden border-b border-ink-100">
        <div className="absolute inset-0 bg-gradient-to-br from-indigo-50/50 via-paper to-amber-50/30" />
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage:
              'linear-gradient(#1e1b4b 1px, transparent 1px), linear-gradient(90deg, #1e1b4b 1px, transparent 1px)',
            backgroundSize: '60px 60px',
          }}
        />
        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-32">
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
                <div className="absolute -top-6 -left-6 section-number">01</div>
                <div className="relative bg-white border border-ink-100 p-8 shadow-2xl shadow-indigo-900/5">
                  <div className="flex items-center gap-3 mb-6">
                    <div className="w-10 h-10 bg-indigo-800 flex items-center justify-center">
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
      <section className="bg-ink-900 text-paper py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-14">
            <span className="inline-flex items-center gap-3 text-xs font-medium uppercase tracking-widest-xl text-amber-500/80">
              <span className="block w-8 h-px bg-amber-500" />
              课程分类
              <span className="block w-8 h-px bg-amber-500" />
            </span>
            <h2 className="font-display text-4xl md:text-5xl font-bold mt-4 text-white">
              探索你的方向
            </h2>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            {categories.map((cat) => (
              <Link
                key={cat.name}
                to="/courses"
                className="group bg-ink-800 border border-ink-700 p-6 text-center hover:border-amber-600 hover:bg-ink-800/80 transition-all duration-300"
              >
                <div className="font-display text-3xl font-bold text-amber-500 mb-2 group-hover:scale-110 transition-transform">
                  {categoryIcons[cat.name] ?? '+'}
                </div>
                <p className="text-sm font-medium text-paper">{cat.name}</p>
                <p className="text-xs text-ink-400 mt-1">{cat.courseCount} 门课程</p>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
        <div className="relative bg-indigo-800 p-12 md:p-16 overflow-hidden">
          <div
            className="absolute inset-0 opacity-10"
            style={{
              backgroundImage:
                'linear-gradient(rgba(255,255,255,0.3) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.3) 1px, transparent 1px)',
              backgroundSize: '30px 30px',
            }}
          />
          <div className="relative max-w-2xl">
            <h2 className="font-display text-3xl md:text-4xl font-bold text-white mb-4">
              准备好开始学习了吗？
            </h2>
            <p className="text-indigo-200 mb-8 leading-relaxed">
              加入 50,000+ 名学员，在 EduCloud 开启你的技术成长之路。首单立减 50 元，限时优惠中。
            </p>
            <Link to="/courses" className="inline-flex items-center gap-2 px-8 py-4 bg-amber-600 text-white font-medium text-sm hover:bg-amber-500 transition-colors">
              立即开始
              <ArrowRight size={16} />
            </Link>
          </div>
          <div className="absolute -right-8 -bottom-8 section-number !text-white/10 !text-[12rem] md:!text-[16rem]">
            {'>'}
          </div>
        </div>
      </section>
    </div>
  );
}
