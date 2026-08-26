import { useEffect, useMemo, useState } from 'react';
import { Search, SlidersHorizontal, X, AlertCircle, Sparkles, Database } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useCourseStore } from '@/stores/useCourseStore';
import { courseApi } from '@/services/courseApi';
import {
  searchApi,
  type CourseSearchItem,
  type SearchAggregations,
  type CourseSearchQueryParams,
} from '@/services/searchApi';
import CourseCard from '@/components/CourseCard';
import CourseSortSelect, { type CourseSortOption } from '@/components/CourseSortSelect';
import { apiErrorText } from '@/services/http';
import { cn } from '@/utils/cn';
import type { Category, Course, CourseLevel } from '@/types';

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

function mapSearchItemToCourse(item: CourseSearchItem): Course {
  return {
    id: item.courseId || item.id,
    title: item.title,
    coverUrl: item.coverUrl ?? null,
    teacherName: item.teacherName || '主讲名师',
    categoryName: item.category || '通识课程',
    level: (item.difficulty as CourseLevel) || 'BEGINNER',
    price: item.isFree ? '0' : String((item.priceCents ?? 0) / 100),
    ratingAvg: item.rating ?? 5.0,
    ratingCount: item.studentCount ? Math.floor(item.studentCount / 10) + 5 : 12,
    enrollmentCount: item.studentCount ?? 0,
    enrolled: false,
  };
}

export default function CourseList() {
  const {
    courses: storeCourses,
    total: storeTotal,
    loading: storeLoading,
    error: storeError,
    fetchCourses,
  } = useCourseStore();

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

  // 搜索接口专属状态
  const [searchCourses, setSearchCourses] = useState<Course[]>([]);
  const [searchTotal, setSearchTotal] = useState(0);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [searchAggs, setSearchAggs] = useState<SearchAggregations | null>(null);
  const [isDegraded, setIsDegraded] = useState(false);

  const isSearchMode = Boolean(search.trim());

  // 当 URL 中的 keyword 改变时同步更新内部 search 状态
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

  // 数据请求调度：有 keyword 时走 searchCourses，无 keyword 时走 courseApi/useCourseStore
  useEffect(() => {
    const timer = window.setTimeout(async () => {
      const trimmedSearch = search.trim();

      if (trimmedSearch) {
        // 搜索引擎检索分支
        setSearchLoading(true);
        setSearchError(null);

        // 排序与价格转换
        let sortBy = 'relevance';
        if (sort === 'popular') sortBy = 'popular';
        else if (sort === 'newest') sortBy = 'newest';
        else if (sort === 'price-asc') sortBy = 'price_asc';
        else if (sort === 'price-desc') sortBy = 'price_desc';

        const searchQueryParams: CourseSearchQueryParams = {
          keyword: trimmedSearch,
          category: selectedCategory === 'all' ? undefined : selectedCategory,
          difficulty: selectedLevel === 'all' ? undefined : selectedLevel,
          sortBy,
          page,
          size: PAGE_SIZE,
        };

        if (priceRange === 'free') {
          searchQueryParams.isFree = true;
        } else if (priceRange === 'under200') {
          searchQueryParams.maxPriceCents = 20000;
        } else if (priceRange === '200to400') {
          searchQueryParams.minPriceCents = 20000;
          searchQueryParams.maxPriceCents = 40000;
        } else if (priceRange === 'above400') {
          searchQueryParams.minPriceCents = 40000;
        }

        try {
          const resp = await searchApi.searchCourses(searchQueryParams);
          setSearchCourses((resp.items || []).map(mapSearchItemToCourse));
          setSearchTotal(resp.total || 0);
          setSearchAggs(resp.aggregations || null);
          setIsDegraded(Boolean(resp.isDegraded));
        } catch (e) {
          setSearchError(apiErrorText(e));
          setSearchCourses([]);
          setSearchTotal(0);
        } finally {
          setSearchLoading(false);
        }
      } else {
        // 普通课程目录列表分支
        setSearchAggs(null);
        setIsDegraded(false);
        void fetchCourses({
          categoryId: selectedCategory === 'all' ? undefined : categoryId,
          level: selectedLevel === 'all' ? undefined : selectedLevel,
          priceRange: priceRange === 'all' ? undefined : priceRange,
          sort,
          page,
          size: PAGE_SIZE,
        });
      }
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

  const displayCourses = isSearchMode ? searchCourses : storeCourses;
  const displayTotal = isSearchMode ? searchTotal : storeTotal;
  const displayLoading = isSearchMode ? searchLoading : storeLoading;
  const displayError = isSearchMode ? searchError : storeError;
  const totalPages = Math.max(1, Math.ceil(displayTotal / PAGE_SIZE));

  const clearFilters = () => {
    setSearch('');
    setSearchParams(new URLSearchParams());
    setSelectedLevel('all');
    setPriceRange('all');
    setSort('popular');
    setPage(1);
  };

  const FilterSidebar = () => {
    // 提取聚合计数映射
    const categoryCountMap = useMemo(() => {
      const map = new Map<string, number>();
      if (searchAggs?.categories) {
        searchAggs.categories.forEach((item) => {
          map.set(item.key, item.count);
        });
      }
      return map;
    }, []);

    const difficultyCountMap = useMemo(() => {
      const map = new Map<string, number>();
      if (searchAggs?.difficulties) {
        searchAggs.difficulties.forEach((item) => {
          map.set(item.key, item.count);
        });
      }
      return map;
    }, []);

    return (
      <div className="space-y-8">
        {/* Categories */}
        <div>
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-display text-lg font-semibold text-ink-900">课程分类</h3>
            {searchAggs && (
              <span className="text-[10px] text-indigo-700 bg-indigo-50 px-1.5 py-0.5 rounded font-medium">
                动态聚合
              </span>
            )}
          </div>
          <div className="space-y-1">
            <button
              type="button"
              aria-pressed={selectedCategory === 'all'}
              data-course-category-filter="all"
              onClick={() => selectCategory('all')}
              className={cn(
                'w-full text-left px-3 py-2 text-sm transition-colors flex justify-between items-center rounded-r-lg',
                selectedCategory === 'all'
                  ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                  : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent',
              )}
            >
              <span>全部分类</span>
              {isSearchMode && displayTotal > 0 && (
                <span className="text-xs text-ink-400 font-normal">{displayTotal}</span>
              )}
            </button>
            {flatCategories.map((cat) => {
              const aggCount = categoryCountMap.get(cat.name);
              return (
                <button
                  key={cat.id}
                  type="button"
                  aria-pressed={selectedCategory === cat.name}
                  data-course-category-filter={cat.name}
                  onClick={() => selectCategory(cat.name)}
                  className={cn(
                    'w-full text-left px-3 py-2 text-sm transition-colors flex justify-between items-center rounded-r-lg',
                    selectedCategory === cat.name
                      ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                      : 'text-ink-600 hover:bg-ink-50 border-l-2 border-transparent',
                  )}
                >
                  <span className="truncate">{cat.name}</span>
                  {aggCount !== undefined && (
                    <span
                      className={cn(
                        'text-xs px-1.5 py-0.2 rounded-full text-[11px]',
                        selectedCategory === cat.name
                          ? 'bg-indigo-200/70 text-indigo-900 font-semibold'
                          : 'bg-ink-100 text-ink-500',
                      )}
                    >
                      {aggCount}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        {/* Level */}
        <div>
          <h3 className="font-display text-lg font-semibold text-ink-900 mb-4">难度等级</h3>
          <div className="space-y-2">
            {levels.map((lvl) => {
              const diffCount = difficultyCountMap.get(lvl.value);
              return (
                <label
                  key={lvl.value}
                  className="flex items-center justify-between gap-3 cursor-pointer group py-0.5"
                >
                  <div className="flex items-center gap-3">
                    <input
                      type="radio"
                      name="level"
                      checked={selectedLevel === lvl.value}
                      onChange={() => {
                        setSelectedLevel(lvl.value);
                        setPage(1);
                      }}
                      className="w-4 h-4 accent-indigo-800"
                    />
                    <span className="text-sm text-ink-600 group-hover:text-ink-900">
                      {lvl.label}
                    </span>
                  </div>
                  {diffCount !== undefined && lvl.value !== 'all' && (
                    <span className="text-xs text-ink-400 font-mono">({diffCount})</span>
                  )}
                </label>
              );
            })}
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
              <label key={opt.value} className="flex items-center gap-3 cursor-pointer group py-0.5">
                <input
                  type="radio"
                  name="price"
                  checked={priceRange === opt.value}
                  onChange={() => {
                    setPriceRange(opt.value as PriceRange);
                    setPage(1);
                  }}
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
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      {/* Page Header */}
      <div className="mb-10">
        <span className="section-label mb-3">
          {isSearchMode ? '检索结果' : '课程目录'}
        </span>
        <div className="flex flex-wrap items-center gap-3 mt-3">
          <h1 className="display-heading text-4xl md:text-5xl">
            {isSearchMode ? '课程检索' : '全部课程'}
          </h1>
          {isSearchMode && isDegraded && (
            <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full bg-amber-50 text-amber-800 border border-amber-200">
              <Database size={13} />
              数据库降级模式
            </span>
          )}
        </div>
        <p className="text-ink-500 mt-3">
          {isSearchMode ? (
            <span>
              关键词 “<span className="font-semibold text-indigo-900">{search}</span>” 共找到{' '}
              <span className="font-semibold text-ink-900">{displayTotal}</span> 门相关课程
            </span>
          ) : (
            `共 ${displayTotal} 门精品课程，找到最适合你的学习路径`
          )}
        </p>
      </div>

      {/* Search Bar */}
      <div className="flex flex-col sm:flex-row gap-4 mb-8">
        <div className="relative flex-1">
          <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
          <input
            type="text"
            value={search}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="搜索课程名称、描述、讲师、标签..."
            className="w-full pl-11 pr-10 py-3 bg-white border border-ink-200 text-ink-800 text-sm placeholder:text-ink-400 focus:outline-none focus:border-indigo-800 transition-colors"
          />
          {search && (
            <button
              type="button"
              onClick={() => handleSearchChange('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-ink-400 hover:text-ink-700"
              title="清除关键词"
            >
              <X size={16} />
            </button>
          )}
        </div>
        <CourseSortSelect
          value={sort}
          options={sortOptions}
          onChange={(value) => {
            setSort(value);
            setPage(1);
          }}
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
          {displayLoading ? (
            <div className="flex items-center justify-center py-32">
              <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
            </div>
          ) : displayError ? (
            <div className="text-center py-32">
              <AlertCircle size={40} className="mx-auto text-red-400 mb-4" />
              <p className="font-display text-xl text-ink-600 mb-2">课程加载失败</p>
              <p className="text-sm text-ink-400 mb-6">{displayError}</p>
              <button
                type="button"
                onClick={() => setRetryTick((tick) => tick + 1)}
                className="btn-primary"
              >
                重新加载
              </button>
            </div>
          ) : displayCourses.length === 0 ? (
            <div className="text-center py-32">
              <p className="font-display text-2xl text-ink-400 mb-2">未找到匹配的课程</p>
              <p className="text-sm text-ink-400 mb-6">尝试调整筛选条件或更换搜索关键词</p>
              <button type="button" onClick={clearFilters} className="btn-outline">
                清除筛选与搜索
              </button>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between mb-6">
                <p className="text-sm text-ink-500">
                  找到 <span className="font-semibold text-ink-900">{displayTotal}</span> 门课程
                </p>
                {isSearchMode && (
                  <span className="text-xs text-indigo-700 flex items-center gap-1">
                    <Sparkles size={13} />
                    已启用智能高亮检索
                  </span>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
                {displayCourses.map((course, index) => (
                  <CourseCard key={course.id} course={course} index={index} />
                ))}
              </div>

              {/* Pagination */}
              <div className="mt-10 flex items-center justify-center gap-4">
                <button
                  type="button"
                  disabled={page <= 1 || displayLoading}
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
                  disabled={page >= totalPages || displayLoading}
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
