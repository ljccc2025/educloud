import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, BookOpen, CalendarClock, RefreshCw, Search, Users } from 'lucide-react';
import CustomSelect, { type SelectOption } from '../components/CustomSelect';
import { teacherCourseApi } from '../services/teacherCourseApi';
import { apiErrorText } from '../services/http';
import type { CourseStudent, TeacherCourse } from '../types';
import dayjs from 'dayjs';

export default function StudentList() {
  const [courses, setCourses] = useState<TeacherCourse[]>([]);
  const [coursesLoading, setCoursesLoading] = useState(true);
  const [coursesError, setCoursesError] = useState<string | null>(null);
  const [selectedCourseId, setSelectedCourseId] = useState('');
  const [students, setStudents] = useState<CourseStudent[]>([]);
  const [total, setTotal] = useState(0);
  const [studentsLoading, setStudentsLoading] = useState(false);
  const [studentsError, setStudentsError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [retryTick, setRetryTick] = useState(0);

  useEffect(() => {
    let alive = true;
    setCoursesLoading(true);
    setCoursesError(null);
    teacherCourseApi
      .getTeacherCourses({ size: 100 })
      .then((page) => {
        if (!alive) return;
        // 仅保留审核通过/已发布的课程，未审核/草稿课程不展示在学员名录中
        const published = (page.items || []).filter((c) => {
          if (
            c.versionStatus === 'DRAFT' ||
            c.versionStatus === 'PENDING_REVIEW' ||
            c.versionStatus === 'REJECTED' ||
            c.versionStatus === 'WITHDRAWN'
          ) {
            return false;
          }
          return c.versionStatus === 'PUBLISHED' || c.lifecycleStatus === 'PUBLISHED';
        });
        setCourses(published);
        setCoursesLoading(false);
        // 默认选中第一门已发布课程
        setSelectedCourseId((prev) => prev || published[0]?.courseId || '');
      })
      .catch((e) => {
        if (!alive) return;
        setCoursesError(apiErrorText(e));
        setCoursesLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [retryTick]);

  const loadStudents = useCallback(async (courseId: string) => {
    if (!courseId) {
      setStudents([]);
      setTotal(0);
      return;
    }
    setStudentsLoading(true);
    setStudentsError(null);
    try {
      const page = await teacherCourseApi.getStudents(courseId, 1, 100);
      setStudents(page.items);
      setTotal(page.total);
    } catch (e) {
      setStudentsError(apiErrorText(e));
    } finally {
      setStudentsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStudents(selectedCourseId);
  }, [selectedCourseId, loadStudents]);

  const selectedCourse = courses.find((c) => c.courseId === selectedCourseId);

  const courseOptions: SelectOption[] = useMemo(() => {
    return courses.map((c) => ({
      value: c.courseId,
      label: c.title,
      image: c.coverUrl ?? undefined,
      badge: c.enrollmentCount != null ? `${c.enrollmentCount} 学员` : undefined,
    }));
  }, [courses]);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return students;
    return students.filter(
      (s) => s.studentId.toLowerCase().includes(keyword) || (s.displayName ?? '').toLowerCase().includes(keyword),
    );
  }, [students, search]);

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div>
        <p className="section-label mb-2">学生管理</p>
        <h1 className="display-heading text-3xl md:text-4xl">学员名录</h1>
        <p className="text-ink-500 mt-2 text-sm">按课程查看已报名学员基本信息与学习档案</p>
      </div>

      {/* Course selector + search */}
      <div className="flex flex-col md:flex-row gap-4 items-start md:items-center relative z-30 overflow-visible">
        <div className="flex-1 max-w-md w-full">
          <CustomSelect
            options={courseOptions}
            value={selectedCourseId}
            onChange={setSelectedCourseId}
            placeholder={coursesLoading ? '正在加载课程…' : '请选择课程'}
            disabled={coursesLoading || courses.length === 0}
            prefixIcon={BookOpen}
            minWidth="w-full"
          />
        </div>
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-300" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索学员 ID 或姓名……"
            className="input-field pl-11"
          />
        </div>
      </div>

      {coursesError && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-700 flex items-center gap-3">
          <AlertCircle className="w-4 h-4" />
          <span className="flex-1">课程列表加载失败：{coursesError}</span>
          <button type="button" onClick={() => setRetryTick((tick) => tick + 1)} className="btn-outline !py-1">
            <RefreshCw className="w-4 h-4" />
            重试
          </button>
        </div>
      )}

      {/* Table */}
      <div className="card-editorial overflow-hidden">
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>学员姓名</th>
                <th>学员 ID</th>
                <th>报名时间</th>
              </tr>
            </thead>
            <tbody>
              {studentsLoading ? (
                <tr>
                  <td colSpan={3} className="text-center py-12 text-ink-400">加载中…</td>
                </tr>
              ) : studentsError ? (
                <tr>
                  <td colSpan={3} className="text-center py-12">
                    <div className="flex flex-col items-center gap-3">
                      <AlertCircle className="w-8 h-8 text-red-400" />
                      <p className="text-ink-500">学员列表加载失败：{studentsError}</p>
                      <button type="button" onClick={() => void loadStudents(selectedCourseId)} className="btn-outline">
                        <RefreshCw className="w-4 h-4" />
                        重新加载
                      </button>
                    </div>
                  </td>
                </tr>
              ) : !selectedCourseId ? (
                <tr>
                  <td colSpan={3} className="text-center py-12 text-ink-400">请先选择课程</td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={3} className="text-center py-12 text-ink-400">
                    {students.length === 0 ? '该课程暂无已报名学员' : '未找到匹配的学员'}
                  </td>
                </tr>
              ) : (
                filtered.map((s) => (
                  <tr key={s.studentId}>
                    <td>
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 font-medium text-xs flex items-center justify-center shrink-0">
                          {(s.displayName || s.studentId).slice(-1)}
                        </div>
                        <span className="font-medium text-ink-900">{s.displayName ?? '—'}</span>
                      </div>
                    </td>
                    <td>
                      <span className="flex items-center gap-1.5 text-ink-600 font-mono text-xs">
                        <Users className="w-3.5 h-3.5 text-ink-400" />
                        {s.studentId}
                      </span>
                    </td>
                    <td>
                      <span className="flex items-center gap-1.5 text-ink-500 text-sm">
                        <CalendarClock className="w-3.5 h-3.5 text-ink-400" />
                        {dayjs(s.enrolledAt).format('YYYY-MM-DD HH:mm')}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {selectedCourse && !studentsLoading && !studentsError && (
        <p className="text-sm text-ink-400 flex items-center gap-1.5">
          <BookOpen className="w-4 h-4" />
          课程「{selectedCourse.title}」共 {total} 名学员（分页控件 M06 规模化再补）
        </p>
      )}
    </div>
  );
}
