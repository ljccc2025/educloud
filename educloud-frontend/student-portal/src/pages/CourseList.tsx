import { useEffect, useState, useMemo } from 'react';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import { categories } from '@/services/api';
import CourseCard from '@/components/CourseCard';
import CourseSortSelect, { type CourseSortOption } from '@/components/CourseSortSelect';
import { cn } from '@/utils/cn';
import type { CourseLevel } from '@/types';

type SortOption = 'popular' | 'newest' | 'price-asc' | 'price-desc' | 'rating';
type PriceRange = 'all' | 'free' | 'under200' | '200to400' | 'above400';

const levels: { value: CourseLevel | 'all'; label: string }[] = [
  { value: 'all', label: '全部难度' },
  { value: 'BEGINNER', label: '入门' },
  { value: 'INTERMEDIATE', label: '进阶' },
  { value: 'ADVANCED', label: '高级' },
];

const sortOptions: readonly CourseSortOption<SortOption>[] = [
  { value: 'popular', label: '最受欢迎' },
  { value: 'newest', label: '最新发布' },
  { value: 'price-asc', label: '价格从低到高' },
  { value: 'price-desc', label: '价格从高到低' },
  { value: 'rating', label: '评分最高' },
];

export default function CourseList() {
  const { courses, loading, fetchCourses } = useCourseStore();
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [selectedLevel, setSelectedLevel] = useState<CourseLevel | 'all'>('all');
  const [priceRange, setPriceRange] = useState<PriceRange>('all');
  const [sort, setSort] = useState<SortOption>('popular');
  const [showMobileFilters, setShowMobileFilters] = useState(false);

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  const filteredCourses = useMemo(() => {
    let result = [...courses];

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(
        (c) =>
          c.title.toLowerCase().includes(q) ||
          c.description.toLowerCase().includes(q)
      );
    }

    if (selectedCategory !== 'all') {
      result = result.filter((c) => c.category === selectedCategory);
    }

    if (selectedLevel !== 'all') {
      result = result.filter((c) => c.level === selectedLevel);
    }

    if (priceRange !== 'all') {
      result = result.filter((c) => {
        if (priceRange === 'free') return c.price === 0;
        if (priceRange === 'under200') return c.price < 200;
        if (priceRange === '200to400') return c.price >= 200 && c.price <= 400;
        if (priceRange === 'above400') return c.price > 400;
        return true;
      });
    }

    switch (sort) {
      case 'newest':
        result.reverse();
        break;
      case 'price-asc':
        result.sort((a, b) => a.price - b.price);
        break;
      case 'price-desc':
        result.sort((a, b) => b.price - a.price);
        break;
      case 'rating':
        result.sort((a, b) => b.rating - a.rating);
        break;
      case 'popular':
      default:
        result.sort((a, b) => b.studentCount - a.studentCount);
        break;
    }

    return result;
  }, [courses, search, selectedCategory, selectedLevel, priceRange, sort]);

  const clearFilters = () => {
    setSearch('');
    setSelectedCategory('all');
    setSelectedLevel('all');
    setPriceRange('all');
    setSort('popular');
  };

  const FilterSidebar = () => (
    <div className="space-y-8">
      {/* Categories */}
      <div>
        <h3 className="font-display text-lg font-semibold text-ink-900 mb-4">课程分类</h3>
        <div className="space-y-1">
          <button
            type="button"
            onClick={() => setSelectedCategory('all')}
            className={cn(
              'w-full text-left px-3 py-2 text-sm transition-colors',
              selectedCategory === 'all'
                ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent'
            )}
          >
            全部分类
          </button>
          {categories.map((cat) => (
            <button
              key={cat.name}
              type="button"
              onClick={() => setSelectedCategory(cat.name)}
              className={cn(
                'w-full text-left px-3 py-2 text-sm transition-colors flex justify-between items-center',
                selectedCategory === cat.name
                  ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                  : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent'
              )}
            >
              {cat.name}
              <span className="text-xs text-ink-400">{cat.courseCount}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Level */}
      <div>
        <h3 className="font-display text-lg font-semibold text-ink-900 mb-4">难度等级</h3>
        <div className="space-y-2">
          {levels.map((lvl) => (
            <label key={lvl.value} className="flex items-center gap-3 cursor-pointer group">
              <input
                type="radio"
                name="level"
                checked={selectedLevel === lvl.value}
                onChange={() => setSelectedLevel(lvl.value)}
                className="w-4 h-4 accent-indigo-800"
              />
              <span className="text-sm text-ink-600 group-hover:text-ink-900">{lvl.label}</span>
            </label>
          ))}
        </div>
      </div>

      {/* Price Range */}
      <div>
        <h3 className="font-display text-lg font-semibold text-ink-900 mb-4">价格区间</h3>
        <div className="space-y-2">
          {[
            { value: 'all', label: '全部价格' },
            { value: 'free', label: '免费课程' },
            { value: 'under200', label: '200 元以下' },
            { value: '200to400', label: '200 - 400 元' },
            { value: 'above400', label: '400 元以上' },
          ].map((opt) => (
            <label key={opt.value} className="flex items-center gap-3 cursor-pointer group">
              <input
                type="radio"
                name="price"
                checked={priceRange === opt.value}
                onChange={() => setPriceRange(opt.value as PriceRange)}
                className="w-4 h-4 accent-indigo-800"
              />
              <span className="text-sm text-ink-600 group-hover:text-ink-900">{opt.label}</span>
            </label>
          ))}
        </div>
      </div>

      <button
        type="button"
        onClick={clearFilters}
        className="text-sm text-amber-600 font-medium hover:text-amber-700 transition-colors"
      >
        清除全部筛选
      </button>
    </div>
  );

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      {/* Page Header */}
      <div className="mb-10">
        <span className="section-label mb-3">课程目录</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">全部课程</h1>
        <p className="text-ink-500 mt-3">共 {courses.length} 门精品课程，找到最适合你的学习路径</p>
      </div>

      {/* Search Bar */}
      <div className="flex flex-col sm:flex-row gap-4 mb-8">
        <div className="relative flex-1">
          <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索课程名称、标签..."
            className="w-full pl-11 pr-4 py-3 bg-white border border-ink-200 text-ink-800 text-sm placeholder:text-ink-400 focus:outline-none focus:border-indigo-800 transition-colors"
          />
        </div>
        <CourseSortSelect
          value={sort}
          options={sortOptions}
          onChange={setSort}
        />
        <button
          type="button"
          onClick={() => setShowMobileFilters(true)}
          className="lg:hidden btn-outline"
        >
          <SlidersHorizontal size={16} />
          筛选
        </button>
      </div>

      <div className="flex gap-10">
        {/* Desktop Sidebar */}
        <aside className="hidden lg:block w-64 flex-shrink-0">
          <div className="sticky top-24">
            <FilterSidebar />
          </div>
        </aside>

        {/* Course Grid */}
        <div className="flex-1 min-w-0">
          {loading ? (
            <div className="flex items-center justify-center py-32">
              <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
            </div>
          ) : filteredCourses.length === 0 ? (
            <div className="text-center py-32">
              <p className="font-display text-2xl text-ink-400 mb-2">未找到匹配的课程</p>
              <p className="text-sm text-ink-400 mb-6">尝试调整筛选条件或搜索关键词</p>
              <button type="button" onClick={clearFilters} className="btn-outline">
                清除筛选
              </button>
            </div>
          ) : (
            <>
              <p className="text-sm text-ink-500 mb-6">
                找到 <span className="font-semibold text-ink-900">{filteredCourses.length}</span> 门课程
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
                {filteredCourses.map((course) => (
                  <CourseCard key={course.id} course={course} />
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Mobile Filter Drawer */}
      {showMobileFilters && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-ink-900/50 backdrop-blur-sm"
            onClick={() => setShowMobileFilters(false)}
          />
          <div className="absolute right-0 top-0 bottom-0 w-80 max-w-[85vw] bg-white p-6 overflow-y-auto animate-fade-in">
            <div className="flex items-center justify-between mb-8">
              <h2 className="font-display text-xl font-bold">筛选条件</h2>
              <button
                type="button"
                onClick={() => setShowMobileFilters(false)}
                className="p-2 text-ink-500 hover:text-ink-900"
              >
                <X size={20} />
              </button>
            </div>
            <FilterSidebar />
          </div>
        </div>
      )}
    </div>
  );
}
