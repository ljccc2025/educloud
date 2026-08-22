import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Pencil, FolderTree, Trash2, Search, Users } from 'lucide-react';
import { useCourseStore } from '../stores/useCourseStore';
import type { Course, CourseStatus } from '../types';
import { cn } from '../utils/cn';
import CourseCreateModal from '../components/CourseCreateModal';

const statusConfig: Record<CourseStatus, { label: string; cls: string }> = {
  DRAFT: { label: '草稿', cls: 'badge-amber' },
  PUBLISHED: { label: '已发布', cls: 'badge-green' },
  ARCHIVED: { label: '已归档', cls: 'badge-indigo' },
};

const categoryLabels: Record<string, string> = {
  backend: '后端开发',
  frontend: '前端开发',
  data: '数据分析',
  ai: '人工智能',
  devops: '运维部署',
  mobile: '移动开发',
};

export default function CourseManage() {
  const navigate = useNavigate();
  const createButtonRef = useRef<HTMLButtonElement>(null);
  const { courses, loading, fetchCourses, createCourse, deleteCourse } = useCourseStore();
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<CourseStatus | 'ALL'>('ALL');
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  const filtered = courses.filter((c) => {
    const matchSearch = c.title.toLowerCase().includes(search.toLowerCase());
    const matchStatus = filterStatus === 'ALL' || c.status === filterStatus;
    return matchSearch && matchStatus;
  });

  const handleDelete = async (course: Course) => {
    if (window.confirm(`确定删除课程「${course.title}」吗？此操作不可撤销。`)) {
      await deleteCourse(course.id);
    }
  };

  const handleCreate = async (data: Partial<Course>) => {
    const created = await createCourse(data);
    setShowCreate(false);
    navigate(`/courses/edit/${created.id}`);
  };

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
        <div className="flex gap-2">
          {(['ALL', 'PUBLISHED', 'DRAFT', 'ARCHIVED'] as const).map((s) => (
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
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-ink-400">暂无课程数据</td>
                </tr>
              ) : (
                filtered.map((course) => (
                  <tr key={course.id}>
                    <td>
                      <div className="flex items-center gap-3">
                        <img
                          src={course.cover}
                          alt={course.title}
                          className="w-16 h-12 object-cover flex-shrink-0 bg-ink-100 rounded-md"
                        />
                        <div className="min-w-0">
                          <p className="font-medium text-ink-800 line-clamp-1">{course.title}</p>
                          <p className="text-xs text-ink-400 mt-0.5">
                            {course.chapters.length} 章节 · {course.chapters.reduce((acc, ch) => acc + ch.coursewares.length, 0)} 课件
                          </p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="text-ink-600">{categoryLabels[course.category]}</span>
                    </td>
                    <td>
                      <span className="flex items-center gap-1 text-ink-700">
                        <Users className="w-3.5 h-3.5 text-ink-400" />
                        {course.studentCount.toLocaleString()}
                      </span>
                    </td>
                    <td>
                      <span className="font-medium text-ink-800">
                        {course.price === 0 ? '免费' : `¥${course.price}`}
                      </span>
                    </td>
                    <td>
                      <span className={statusConfig[course.status].cls}>
                        {statusConfig[course.status].label}
                      </span>
                    </td>
                    <td>
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => navigate(`/courses/edit/${course.id}`)}
                          className="btn-ghost"
                          title="编辑"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => navigate('/content')}
                          className="btn-ghost"
                          title="管理内容"
                        >
                          <FolderTree className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDelete(course)}
                          className="btn-ghost text-red-500 hover:text-red-700"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
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
