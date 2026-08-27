import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { Plus, FileQuestion, Clock, Users, Play, Pencil, Eye } from 'lucide-react';
import CustomSelect from '../components/CustomSelect';
import { api } from '../services/api';
import type { Course, Exam, ExamStatus } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<ExamStatus, { label: string; cls: string }> = {
  DRAFT: { label: '草稿', cls: 'badge-amber' },
  PUBLISHED: { label: '已发布', cls: 'badge-indigo' },
  ONGOING: { label: '进行中', cls: 'badge-red' },
  ENDED: { label: '已结束', cls: 'badge-green' },
};

export default function ExamManage() {
  const [exams, setExams] = useState<Exam[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newCourseId, setNewCourseId] = useState('');
  const [newDuration, setNewDuration] = useState('60');
  const [newQuestionCount, setNewQuestionCount] = useState('30');
  const [newScheduledAt, setNewScheduledAt] = useState('');

  useEffect(() => {
    Promise.all([api.getExams(), api.getCourses()]).then(([examData, courseData]) => {
      setExams(examData);
      setCourses(courseData);
      setLoading(false);
    });
  }, []);

  const examCourseOptions = useMemo(() => {
    const published = courses.filter((c) => c.status === 'PUBLISHED');
    return [
      { value: '', label: '请选择课程' },
      ...published.map((c) => ({
        value: c.id,
        label: c.title,
        image: c.cover,
        badge: `${c.studentCount} 学员`,
      })),
    ];
  }, [courses]);

  useEffect(() => {
    if (!showCreate) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showCreate]);

  const handleCreate = async () => {
    if (!newTitle.trim() || !newCourseId) return;
    const course = await api.getCourse(newCourseId);
    await api.createExam({
      title: newTitle.trim(),
      courseId: newCourseId,
      courseName: course?.title ?? '',
      duration: Number(newDuration) || 60,
      questionCount: Number(newQuestionCount) || 0,
      scheduledAt: newScheduledAt || dayjs().add(7, 'day').toISOString(),
    });
    const updated = await api.getExams();
    setExams(updated);
    setShowCreate(false);
    setNewTitle('');
    setNewCourseId('');
  };

  const createModal = showCreate
    ? createPortal(
        <div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
          <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
            <div className="pointer-events-none absolute inset-0 overflow-hidden">
              <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
              <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
            </div>
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="create-exam-title"
              className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-lg overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
            >
              <h2 id="create-exam-title" className="font-display text-xl font-semibold text-ink-900">
                创建考试
              </h2>
              <div className="mt-4 space-y-4">
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">考试标题</label>
                  <input
                    type="text"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    className="input-field"
                    placeholder="例如：期末考试"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">关联课程</label>
                  <CustomSelect
                    options={examCourseOptions}
                    value={newCourseId}
                    onChange={setNewCourseId}
                    placeholder="请选择课程"
                    minWidth="w-full"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="mb-2 block text-sm font-medium text-ink-700">题目数量</label>
                    <input
                      type="number"
                      value={newQuestionCount}
                      onChange={(e) => setNewQuestionCount(e.target.value)}
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="mb-2 block text-sm font-medium text-ink-700">时长（分钟）</label>
                    <input
                      type="number"
                      value={newDuration}
                      onChange={(e) => setNewDuration(e.target.value)}
                      className="input-field"
                    />
                  </div>
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">开考时间</label>
                  <input
                    type="datetime-local"
                    value={newScheduledAt}
                    onChange={(e) => setNewScheduledAt(e.target.value)}
                    className="input-field"
                  />
                </div>
                <div className="flex gap-3 pt-1">
                  <button onClick={handleCreate} className="btn-primary flex-1">创建</button>
                  <button onClick={() => setShowCreate(false)} className="btn-outline flex-1">取消</button>
                </div>
              </div>
            </div>
          </div>
        </div>,
        document.body,
      )
    : null;

  return (
    <>
      <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <p className="section-label mb-2">考试管理</p>
          <h1 className="display-heading text-3xl md:text-4xl">考试中心</h1>
          <p className="text-ink-500 mt-2 text-sm">创建测验、管理题库与监控考试进度</p>
        </div>
        <button onClick={() => setShowCreate(true)} className="btn-primary">
          <Plus className="w-4 h-4" />
          创建考试
        </button>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="stat-card">
          <p className="text-xs text-ink-400 uppercase tracking-wider">考试总数</p>
          <p className="font-display text-3xl font-bold text-ink-900 mt-1">{exams.length}</p>
        </div>
        <div className="stat-card">
          <p className="text-xs text-ink-400 uppercase tracking-wider">进行中</p>
          <p className="font-display text-3xl font-bold text-red-600 mt-1">
            {exams.filter((e) => e.status === 'ONGOING').length}
          </p>
        </div>
        <div className="stat-card">
          <p className="text-xs text-ink-400 uppercase tracking-wider">已发布</p>
          <p className="font-display text-3xl font-bold text-indigo-600 mt-1">
            {exams.filter((e) => e.status === 'PUBLISHED').length}
          </p>
        </div>
        <div className="stat-card">
          <p className="text-xs text-ink-400 uppercase tracking-wider">累计参考</p>
          <p className="font-display text-3xl font-bold text-amber-600 mt-1">
            {exams.reduce((acc, e) => acc + e.studentCount, 0).toLocaleString()}
          </p>
        </div>
      </div>

      {/* Exam table */}
      <div className="card-editorial overflow-hidden">
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>考试名称</th>
                <th>题目数</th>
                <th>时长</th>
                <th>参考人数</th>
                <th>开考时间</th>
                <th>状态</th>
                <th className="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-12 text-ink-400">加载中…</td>
                </tr>
              ) : exams.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-12 text-ink-400">暂无考试</td>
                </tr>
              ) : (
                exams.map((exam) => (
                  <tr key={exam.id}>
                    <td>
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 bg-indigo-50 flex items-center justify-center flex-shrink-0 rounded-lg">
                          <FileQuestion className="w-4 h-4 text-indigo-600" />
                        </div>
                        <div>
                          <p className="font-medium text-ink-800">{exam.title}</p>
                          <p className="text-xs text-ink-400">{exam.courseName}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="text-ink-700">{exam.questionCount} 题</span>
                    </td>
                    <td>
                      <span className="flex items-center gap-1 text-ink-600">
                        <Clock className="w-3.5 h-3.5 text-ink-400" />
                        {exam.duration} 分钟
                      </span>
                    </td>
                    <td>
                      <span className="flex items-center gap-1 text-ink-700">
                        <Users className="w-3.5 h-3.5 text-ink-400" />
                        {exam.studentCount.toLocaleString()}
                      </span>
                    </td>
                    <td className="text-ink-600">
                      {dayjs(exam.scheduledAt).format('YYYY-MM-DD HH:mm')}
                    </td>
                    <td>
                      <span className={statusConfig[exam.status].cls}>
                        {exam.status === 'ONGOING' && (
                          <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                        )}
                        {statusConfig[exam.status].label}
                      </span>
                    </td>
                    <td>
                      <div className="flex items-center justify-end gap-1">
                        {exam.status === 'ONGOING' && (
                          <button className="btn-ghost text-red-600" title="监考">
                            <Eye className="w-4 h-4" />
                          </button>
                        )}
                        {(exam.status === 'DRAFT' || exam.status === 'PUBLISHED') && (
                          <button className="btn-ghost" title="编辑">
                            <Pencil className="w-4 h-4" />
                          </button>
                        )}
                        {exam.status === 'DRAFT' && (
                          <button className="btn-ghost text-green-600" title="发布">
                            <Play className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        </div>
      </div>
      {createModal}
    </>
  );
}
