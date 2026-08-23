import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Pencil, FolderTree, Search, Users, RefreshCw, AlertCircle } from 'lucide-react';
import { teacherCourseApi } from '../services/teacherCourseApi';
import { apiErrorText } from '../services/http';
import type { Category, CourseDraftInput, TeacherCourse } from '../types';
import { cn } from '../utils/cn';
import CourseCreateModal from '../components/CourseCreateModal';

const statusConfig: Record<string, { label: string; cls: string }> = {
  DRAFT: { label: '草稿', cls: 'badge-amber' },
  PENDING_REVIEW: { label: '待审核', cls: 'badge-indigo' },
  REJECTED: { label: '已驳回', cls: 'badge-red' },
  WITHDRAWN: { label: '已撤回', cls: 'badge-red' },
  PUBLISHED: { label: '已发布', cls: 'badge-green' },
  OFFLINE: { label: '已下架', cls: 'badge-amber' },
  ARCHIVED: { label: '已归档', cls: 'badge-indigo' },
};

const statusFilters = ['ALL', 'DRAFT', 'PENDING_REVIEW', 'REJECTED', 'WITHDRAWN', 'PUBLISHED', 'OFFLINE', 'ARCHIVED'] as const;
type StatusFilter = (typeof statusFilters)[number];

function displayStatus(course: TeacherCourse): string {
  // 工作态（草稿/待审/驳回/已撤回）优先展示版本状态；其余回落到生命周期状态。
  if (
    course.versionStatus === 'DRAFT' ||
    course.versionStatus === 'PENDING_REVIEW' ||
    course.versionStatus === 'REJECTED' ||
    course.versionStatus === 'WITHDRAWN'
  ) {
    return course.versionStatus;
  }
  return course.lifecycleStatus;
}

function formatPrice(price: string | null | undefined): string {
  if (price == null || price === '' || Number(price) === 0) return '免费';
  return '¥' + price;
}

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((c) => [c, ...flattenCategories(c.children)]);
}

export default function CourseManage() {
  const navigate = useNavigate();
  const createButtonRef = useRef<HTMLButtonElement>(null);
  const [courses, setCourses] = useState<TeacherCourse[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryTick, setRetryTick] = useState(0);
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<StatusFilter>('ALL');
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    Promise.all([teacherCourseApi.getTeacherCourses({ size: 100 }), teacherCourseApi.getCategories()])
      .then(([page, cats]) => {
        if (!alive) return;
        setCourses(page.items);
        setCategories(cats);
        setLoading(false);
      })
      .catch((e) => {
        if (!alive) return;
        setError(apiErrorText(e));
        setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [retryTick]);

  const categoryNameById = useMemo(() => {
    const map: Record<string, string> = {};
    for (const c of flattenCategories(categories)) map[c.id] = c.name;
    return map;
  }, [categories]);

  const filtered = courses.filter((c) => {
    const matchSearch = c.title.toLowerCase().includes(search.toLowerCase());
    const matchStatus = filterStatus === 'ALL' || displayStatus(c) === filterStatus;
    return matchSearch && matchStatus;
  });

  const handleCreate = useCallback(
    async (data: CourseDraftInput) => {
      const created = await teacherCourseApi.createCourse(data);
      setShowCreate(false);
      navigate('/courses/edit/' + created.courseId);
    },
    [navigate],
  );

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Page header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <p className="section-label mb-2">课程管理</p>
          <h1 className="display-heading text-3xl md:text-4xl">我的课程</h1>
          <p className="text-ink-500 mt-2 text-sm">共 {courses.length} 门课程，管理课程内容与发布状态</p>
        </div>
        <button
          ref={createButtonRef}
          onClick={() => setShowCreate(true)}
          className="btn-primary"
        >
          <Plus className="w-4 h-4" />
          新建课程
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-col md:flex-row gap-4 items-start md:items-center">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-300" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索课程标题……"
            className="input-field pl-11"
          />
        </div>
        <div className="flex flex-wrap gap-2">
          {statusFilters.map((s) => (
            <button
              key={s}
              onClick={() => setFilterStatus(s)}
              className={cn(
                'px-4 py-2 text-sm font-medium border transition-all rounded-lg',
                filterStatus === s
                  ? 'border-indigo-800 bg-indigo-800 text-white'
                  : 'border-ink-200 text-ink-600 hover:border-ink-400'
              )}
            >
              {s === 'ALL' ? '全部' : statusConfig[s].label}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="card-editorial overflow-hidden">
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>课程</th>
                <th>分类</th>
                <th>学员数</th>
                <th>价格</th>
                <th>状态</th>
                <th className="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-ink-400">加载中…</td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={6} className="text-center py-12">
                    <div className="flex flex-col items-center gap-3">
                      <AlertCircle className="w-8 h-8 text-red-400" />
                      <p className="text-ink-500">加载失败：{error}</p>
                      <button
                        type="button"
                        onClick={() => setRetryTick((tick) => tick + 1)}
                        className="btn-outline"
                      >
                        <RefreshCw className="w-4 h-4" />
                        重新加载
                      </button>
                    </div>
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-ink-400">
                    {courses.length === 0 ? '暂无课程数据，点击右上角「新建课程」开始' : '未找到匹配的课程'}
                  </td>
                </tr>
              ) : (
                filtered.map((course) => (
                  <tr key={course.courseId}>
                    <td>
                      <div className="flex items-center gap-3">
                        {course.coverUrl ? (
                          <img
                            src={course.coverUrl}
                            alt={course.title}
                            className="w-16 h-12 object-cover flex-shrink-0 bg-ink-100 rounded-md"
                          />
                        ) : (
                          <div className="w-16 h-12 flex-shrink-0 bg-ink-100 rounded-md flex items-center justify-center text-ink-300 text-xs">
                            无封面
                          </div>
                        )}
                        <div className="min-w-0">
                          <p className="font-medium text-ink-800 line-clamp-1">{course.title}</p>
                          <p className="text-xs text-ink-400 mt-0.5">版本 v{course.versionNo ?? '--'}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="text-ink-600">{course.categoryId ? (categoryNameById[course.categoryId] ?? '—') : '—'}</span>
                    </td>
                    <td>
                      <span className="flex items-center gap-1 text-ink-700">
                        <Users className="w-3.5 h-3.5 text-ink-400" />
                        {(course.enrollmentCount ?? 0).toLocaleString()}
                      </span>
                    </td>
                    <td>
                      <span className="font-medium text-ink-800">{formatPrice(course.price)}</span>
                    </td>
                    <td>
                      <span className={statusConfig[displayStatus(course)]?.cls ?? 'badge-amber'}>
                        {statusConfig[displayStatus(course)]?.label ?? course.versionStatus}
                      </span>
                    </td>
                    <td>
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => navigate('/courses/edit/' + course.courseId)}
                          className="btn-ghost"
                          title="编辑"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => navigate('/content')}
                          className="btn-ghost"
                          title="管理内容（M06 接入）"
                        >
                          <FolderTree className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {showCreate && (
        <CourseCreateModal
          onClose={() => setShowCreate(false)}
          onSubmit={handleCreate}
          returnFocusRef={createButtonRef}
        />
      )}
    </div>
  );
}
