import { useEffect, useMemo, useState } from 'react';
import { Search, SlidersHorizontal, X, AlertCircle } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useCourseStore } from '@/stores/useCourseStore';
import { courseApi } from '@/services/courseApi';
import CourseCard from '@/components/CourseCard';
import CourseSortSelect, { type CourseSortOption } from '@/components/CourseSortSelect';
import { cn } from '@/utils/cn';
import type { Category, CourseLevel } from '@/types';

type SortOption = 'popular' | 'newest' | 'price-asc' | 'price-desc' | 'rating';
type PriceRange = 'all' | 'free' | 'under200' | '200to400' | 'above400';

const PAGE_SIZE = 12;

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

/** 展平后端分类树（children 递归）为过滤列表。 */
function flattenCategories(nodes: Category[]): Category[] {
  const out: Category[] = [];
  const walk = (list: Category[]) => {
    list.forEach((node) => {
      out.push(node);
      if (node.children && node.children.length > 0) walk(node.children);
    });
  };
  walk(nodes);
  return out;
}

export default function CourseList() {
  const { courses, total, loading, error, fetchCourses } = useCourseStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const [categoryList, setCategoryList] = useState<Category[]>([]);
  const keywordParam = searchParams.get('keyword') ?? '';
  const [search, setSearch] = useState(keywordParam);
  const [selectedLevel, setSelectedLevel] = useState<CourseLevel | 'all'>('all');
  const [priceRange, setPriceRange] = useState<PriceRange>('all');
  const [sort, setSort] = useState<SortOption>('popular');
  const [page, setPage] = useState(1);
  const [retryTick, setRetryTick] = useState(0);
  const [showMobileFilters, setShowMobileFilters] = useState(false);

  // 当 URL 中的 keyword 改变时（如 Navbar 搜索跳转），同步更新内部 search 状态
  useEffect(() => {
    setSearch(keywordParam);
  }, [keywordParam]);

  // 真实分类（GET /api/v1/categories）
  useEffect(() => {
    courseApi
      .getCategories()
      .then(setCategoryList)
      .catch(() => setCategoryList([]));
  }, []);

  const flatCategories = useMemo(() => flattenCategories(categoryList), [categoryList]);

  const categoryParam = searchParams.get('category');
  const selectedCategory =
    categoryParam && flatCategories.some((category) => category.name === categoryParam)
      ? categoryParam
      : 'all';

  const categoryId = useMemo(
    () => flatCategories.find((category) => category.name === selectedCategory)?.id,
    [flatCategories, selectedCategory],
  );

  const selectCategory = (category: string) => {
    setPage(1);
    setSearchParams((currentParams) => {
      const nextParams = new URLSearchParams(currentParams);
      if (category === 'all') {
        nextParams.delete('category');
      } else {
        nextParams.set('category', category);
      }
      return nextParams;
    });
  };

  const handleSearchChange = (value: string) => {
    setSearch(value);
    setPage(1);
    setSearchParams((currentParams) => {
      const nextParams = new URLSearchParams(currentParams);
      if (value.trim()) {
        nextParams.set('keyword', value.trim());
      } else {
        nextParams.delete('keyword');
      }
      return nextParams;
    });
  };

  // 真实分页/筛选/排序：筛选或排序变化即重新请求（搜索输入 300ms 防抖）
  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchCourses({
        keyword: search.trim() || undefined,
        categoryId: selectedCategory === 'all' ? undefined : categoryId,
        level: selectedLevel === 'all' ? undefined : selectedLevel,
        priceRange: priceRange === 'all' ? undefined : priceRange,
        sort,
        page,
        size: PAGE_SIZE,
      });
    }, search ? 300 : 0);
    return () => window.clearTimeout(timer);
  }, [
    fetchCourses,
    search,
    selectedCategory,
    categoryId,
    selectedLevel,
    priceRange,
    sort,
    page,
    retryTick,
  ]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const clearFilters = () => {
    setSearch('');
    setSearchParams(new URLSearchParams());
    setSelectedLevel('all');
    setPriceRange('all');
    setSort('popular');
    setPage(1);
  };

  const FilterSidebar = () => (
    <div className="space-y-8">
      {/* Categories */}
      <div>
        <h3 className="font-display text-lg font-semibold text-ink-900 mb-4">课程分类</h3>
        <div className="space-y-1">
          <button
            type="button"
            aria-pressed={selectedCategory === 'all'}
            data-course-category-filter="all"
            onClick={() => selectCategory('all')}
            className={cn(
              'w-full text-left px-3 py-2 text-sm transition-colors',
              selectedCategory === 'all'
                ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent'
            )}
          >
            全部分类
          </button>
          {flatCategories.map((cat) => (
            <button
              key={cat.id}
              type="button"
              aria-pressed={selectedCategory === cat.name}
              data-course-category-filter={cat.name}
              onClick={() => selectCategory(cat.name)}
              className={cn(
                'w-full text-left px-3 py-2 text-sm transition-colors flex justify-between items-center',
                selectedCategory === cat.name
                  ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                  : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent'
              )}
            >
              {cat.name}
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
                onChange={() => { setSelectedLevel(lvl.value); setPage(1); }}
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
                onChange={() => { setPriceRange(opt.value as PriceRange); setPage(1); }}
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
        <p className="text-ink-500 mt-3">共 {total} 门精品课程，找到最适合你的学习路径</p>
      </div>

      {/* Search Bar */}
      <div className="flex flex-col sm:flex-row gap-4 mb-8">
        <div className="relative flex-1">
          <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
          <input
            type="text"
            value={search}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="搜索课程名称、标签..."
            className="w-full pl-11 pr-4 py-3 bg-white border border-ink-200 text-ink-800 text-sm placeholder:text-ink-400 focus:outline-none focus:border-indigo-800 transition-colors"
          />
        </div>
        <CourseSortSelect
          value={sort}
          options={sortOptions}
          onChange={(value) => { setSort(value); setPage(1); }}
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
          ) : error ? (
            <div className="text-center py-32">
              <AlertCircle size={40} className="mx-auto text-red-400 mb-4" />
              <p className="font-display text-xl text-ink-600 mb-2">课程加载失败</p>
              <p className="text-sm text-ink-400 mb-6">{error}</p>
              <button
                type="button"
                onClick={() => setRetryTick((tick) => tick + 1)}
                className="btn-primary"
              >
                重新加载
              </button>
            </div>
          ) : courses.length === 0 ? (
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
                找到 <span className="font-semibold text-ink-900">{total}</span> 门课程
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
                {courses.map((course, index) => (
                  <CourseCard key={course.id} course={course} index={index} />
                ))}
              </div>

              {/* Pagination */}
              <div className="mt-10 flex items-center justify-center gap-4">
                <button
                  type="button"
                  disabled={page <= 1 || loading}
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  className="btn-outline disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  上一页
                </button>
                <span className="text-sm text-ink-500">
                  第 <span className="font-semibold text-ink-900">{page}</span> / {totalPages} 页
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages || loading}
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  className="btn-outline disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  下一页
                </button>
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
