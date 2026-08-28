import { useCallback, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Plus,
  FileQuestion,
  Clock,
  Users,
  Play,
  Trash2,
  Pencil,
  BookOpen,
  X,
  Eye,
} from 'lucide-react';
import CustomSelect, { type SelectOption } from '../components/CustomSelect';
import { apiErrorText } from '../services/http';
import { api } from '../services/api';
import type { Course, Exam, ExamBankQuestion, ExamQuestionType, ExamStatus } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<ExamStatus, { label: string; cls: string }> = {
  DRAFT: { label: '草稿', cls: 'badge-amber' },
  PUBLISHED: { label: '已发布', cls: 'badge-indigo' },
  ONGOING: { label: '进行中', cls: 'badge-red' },
  ENDED: { label: '已结束', cls: 'badge-green' },
};

const questionTypeConfig: Record<ExamQuestionType, { label: string; cls: string }> = {
  SINGLE: { label: '单选题', cls: 'badge-indigo' },
  MULTIPLE: { label: '多选题', cls: 'badge-amber' },
  JUDGE: { label: '判断题', cls: 'badge-green' },
};

const JUDGE_OPTIONS = ['正确', '错误'];

type ActiveTab = 'exams' | 'bank';

const emptyExamForm = {
  title: '',
  courseId: '',
  duration: '60',
  passScore: '60',
  startTime: '',
};

export default function ExamManage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('exams');
  const [exams, setExams] = useState<Exam[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 考试管理
  const [showCreate, setShowCreate] = useState(false);
  const [examForm, setExamForm] = useState(emptyExamForm);
  const [bankQuestions, setBankQuestions] = useState<ExamBankQuestion[]>([]);
  const [bankLoading, setBankLoading] = useState(false);
  // 题目勾选 + 分值（顺序即组卷顺序）
  const [selectedPaper, setSelectedPaper] = useState<
    Record<string, { score: number; order: number }>
  >({});

  // 题库管理
  const [bankCourseId, setBankCourseId] = useState('');
  const [questions, setQuestions] = useState<ExamBankQuestion[]>([]);
  const [questionsLoading, setQuestionsLoading] = useState(false);
  const [showQuestionModal, setShowQuestionModal] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<ExamBankQuestion | null>(null);

  const loadExams = useCallback(async () => {
    try {
      setExams(await api.getExams());
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '考试列表加载失败');
    }
  }, []);

  useEffect(() => {
    Promise.all([api.getExams(), api.getCourses()])
      .then(([examData, courseData]) => {
        setExams(examData);
        setCourses(courseData);
      })
      .catch((e) => {
        setLoadError(e instanceof Error ? e.message : '考试列表加载失败');
      })
      .finally(() => setLoading(false));
  }, []);

  // 题库：选中课程后加载题目
  useEffect(() => {
    let cancelled = false;
    if (!bankCourseId) {
      setQuestions([]);
      setQuestionsLoading(false);
      return;
    }
    setQuestionsLoading(true);
    api
      .getQuestions(bankCourseId)
      .then((data) => {
        if (!cancelled) setQuestions(data);
      })
      .finally(() => {
        if (!cancelled) setQuestionsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [bankCourseId]);

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

  // 题库用下拉：包含全部已发布课程
  const bankCourseOptions = useMemo(() => {
    const published = courses.filter((c) => c.status === 'PUBLISHED');
    return [
      { value: '', label: '请选择课程' },
      ...published.map((c) => ({ value: c.id, label: c.title, image: c.cover })),
    ];
  }, [courses]);

  // 组卷：勾选的题目（带分值），加载对应课程题库
  const loadPaperQuestions = useCallback(async (courseId: string) => {
    const data = await api.getQuestions(courseId);
    setBankQuestions(data);
  }, []);

  useEffect(() => {
    if (!showCreate || !examForm.courseId) {
      setBankQuestions([]);
      setSelectedPaper({});
      return;
    }
    loadPaperQuestions(examForm.courseId);
  }, [showCreate, examForm.courseId, loadPaperQuestions]);

  useEffect(() => {
    if (!showCreate && !showQuestionModal) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showCreate, showQuestionModal]);

  const togglePaperQuestion = (qid: string) => {
    setSelectedPaper((prev) => {
      const next = { ...prev };
      if (next[qid]) {
        delete next[qid];
      } else {
        next[qid] = { score: 0, order: Object.keys(prev).length };
      }
      return next;
    });
  };

  const setPaperScore = (qid: string, score: number) => {
    setSelectedPaper((prev) => {
      if (!prev[qid]) return prev;
      return { ...prev, [qid]: { ...prev[qid], score } };
    });
  };

  const orderedPaper = useMemo(() => {
    const entries = Object.entries(selectedPaper).sort((a, b) => a[1].order - b[1].order);
    return entries.map(([qid, cfg]) => {
      const q = bankQuestions.find((bq) => bq.id === qid);
      return { q, config: cfg };
    });
  }, [selectedPaper, bankQuestions]);

  const handleCreateExam = async (publish = false) => {
    if (!examForm.title.trim() || !examForm.courseId) return;
    if (orderedPaper.length === 0) {
      window.alert('请至少勾选一道题目进行组卷');
      return;
    }
    const start = examForm.startTime ? dayjs(examForm.startTime) : dayjs();
    const duration = Number(examForm.duration) || 60;
    const paper = orderedPaper
      .filter((item) => item.q)
      .map((item) => ({ questionId: item.q!.id, score: Number(item.config.score) || item.q!.defaultScore || 0 }));
    const paperTotal = paper.reduce((sum, item) => sum + item.score, 0);
    const passScore = Number(examForm.passScore) || 0;
    // 与后端 EXAM_INVALID_PASS_SCORE 同规则，提前拦下并说明原因（表单默认 60 分常大于小卷总分）
    if (passScore < 1 || passScore > paperTotal) {
      window.alert(`及格分需在 1 至 ${paperTotal} 分（组卷总分）之间`);
      return;
    }
    let created;
    try {
      created = await api.createExam({
        title: examForm.title.trim(),
        courseId: examForm.courseId,
        duration,
        passScore,
        questionCount: paper.length,
        startTime: start.format('YYYY-MM-DDTHH:mm:ss'),
        endTime: start.add(duration, 'minute').format('YYYY-MM-DDTHH:mm:ss'),
        paper,
      });
    } catch (e) {
      // 保留弹窗与已填表单，展示服务端原因，避免教师误以为创建成功
      window.alert(`创建考试失败：${apiErrorText(e)}`);
      return;
    }
    if (publish) {
      try {
        await api.publishExam(created.id);
      } catch (e) {
        window.alert(
          `考试已保存为草稿，但发布失败：${e instanceof Error ? e.message : '请重试'}。可在列表中再次发布。`,
        );
      }
    }
    await loadExams();
    setShowCreate(false);
    setExamForm(emptyExamForm);
    setSelectedPaper({});
  };

  const handlePublish = async (exam: Exam) => {
    try {
      await api.publishExam(exam.id);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : '发布考试失败');
      return;
    }
    // 接口成功后才更新本地状态 → PUBLISHED
    setExams((prev) =>
      prev.map((e) => (e.id === exam.id ? { ...e, status: 'PUBLISHED' as ExamStatus } : e)),
    );
  };

  const handleDeleteExam = async (exam: Exam) => {
    if (!window.confirm(`确认删除考试「${exam.title}」？此操作不可恢复。`)) return;
    try {
      await api.deleteExam(exam.id);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : '删除考试失败');
      return;
    }
    setExams((prev) => prev.filter((e) => e.id !== exam.id));
  };

  // ------- 题库 CRUD -------
  const openCreateQuestion = () => {
    setEditingQuestion(null);
    setShowQuestionModal(true);
  };

  const openEditQuestion = (q: ExamBankQuestion) => {
    setEditingQuestion(q);
    setShowQuestionModal(true);
  };

  const handleDeleteQuestion = async (q: ExamBankQuestion) => {
    if (!window.confirm(`确认删除题目「${q.stem}」？此操作不可恢复。`)) return;
    await api.deleteQuestion(q.id);
    setQuestions((prev) => prev.filter((item) => item.id !== q.id));
  };

  const handleQuestionSaved = async (q: ExamBankQuestion) => {
    const courseId = q.courseId;
    // 若正在浏览该课程，刷新列表
    if (bankCourseId === courseId) {
      const data = await api.getQuestions(courseId);
      setQuestions(data);
    }
    setShowQuestionModal(false);
  };

  const selectedPaperCount = Object.keys(selectedPaper).length;
  const totalPaperScore = orderedPaper.reduce(
    (acc, item) => acc + (item.q ? Number(item.config.score) || item.q.defaultScore || 0 : 0),
    0,
  );

  // 及格分需落在 1..组卷总分内（后端同规则校验）；表单默认 60 分对小卷会超限，随总分自动收敛
  useEffect(() => {
    if (!showCreate || totalPaperScore === 0) return;
    setExamForm((prev) => {
      const current = Number(prev.passScore) || 0;
      if (current >= 1 && current <= totalPaperScore) return prev;
      return { ...prev, passScore: String(Math.max(1, Math.ceil(totalPaperScore * 0.6))) };
    });
  }, [showCreate, totalPaperScore]);

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
          {activeTab === 'exams' ? (
            <div className="flex gap-2">
              <button onClick={() => {
                // 预填当前时间：留空会默认排到未来，学生当场考不了又看不到原因
                setExamForm({ ...emptyExamForm, startTime: dayjs().format('YYYY-MM-DDTHH:mm') });
                setSelectedPaper({});
                setShowCreate(true);
              }} className="btn-primary">
                <Plus className="w-4 h-4" />
                创建考试
              </button>
            </div>
          ) : (
            <div className="flex gap-2">
              <button onClick={openCreateQuestion} className="btn-primary">
                <Plus className="w-4 h-4" />
                新建题目
              </button>
            </div>
          )}
        </div>

        {/* Tabs */}
        <div className="flex gap-1 border-b border-ink-100">
          <button
            onClick={() => setActiveTab('exams')}
            className={cn(
              'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
              activeTab === 'exams'
                ? 'border-indigo-600 text-indigo-700'
                : 'border-transparent text-ink-500 hover:text-ink-700',
            )}
          >
            <FileQuestion className="w-4 h-4" />
            考试管理
          </button>
          <button
            onClick={() => setActiveTab('bank')}
            className={cn(
              'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
              activeTab === 'bank'
                ? 'border-indigo-600 text-indigo-700'
                : 'border-transparent text-ink-500 hover:text-ink-700',
            )}
          >
            <BookOpen className="w-4 h-4" />
            题库管理
          </button>
        </div>

        {/* ================ 考试管理视图 ================ */}
        {activeTab === 'exams' ? (
          <>
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
                    ) : loadError ? (
                      <tr>
                        <td colSpan={7} className="text-center py-12 space-y-3">
                          <p className="text-red-600 text-sm">{loadError}</p>
                          <button onClick={() => void loadExams()} className="btn-primary">重新加载</button>
                        </td>
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
                            {exam.scheduledAt ? dayjs(exam.scheduledAt).format('YYYY-MM-DD HH:mm') : '-'}
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
                              {exam.status === 'DRAFT' && (
                                <button
                                  className="btn-ghost text-green-600 flex items-center gap-1"
                                  title="发布"
                                  onClick={() => handlePublish(exam)}
                                >
                                  <Play className="w-4 h-4" />
                                  发布
                                </button>
                              )}
                              {exam.status === 'DRAFT' && (
                                <button
                                  className="btn-ghost text-red-600 flex items-center gap-1"
                                  title="删除"
                                  onClick={() => handleDeleteExam(exam)}
                                >
                                  <Trash2 className="w-4 h-4" />
                                  删除
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
          </>
        ) : (
          /* ================ 题库管理视图 ================ */
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-48">
                <CustomSelect
                  options={bankCourseOptions}
                  value={bankCourseId}
                  onChange={setBankCourseId}
                  placeholder="选择课程筛选"
                  minWidth="w-full"
                />
              </div>
              {bankCourseId && (
                <span className="text-sm text-ink-500">
                  共 {questions.length} 道题目
                </span>
              )}
            </div>

            <div className="card-editorial overflow-hidden">
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>题干</th>
                      <th>题型</th>
                      <th>选项</th>
                      <th>分值</th>
                      <th className="text-right">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {questionsLoading ? (
                      <tr>
                        <td colSpan={5} className="text-center py-12 text-ink-400">加载中…</td>
                      </tr>
                    ) : !bankCourseId ? (
                      <tr>
                        <td colSpan={5} className="text-center py-12 text-ink-400">
                          请先选择课程以浏览题库
                        </td>
                      </tr>
                    ) : questions.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="text-center py-12 text-ink-400">暂无题目</td>
                      </tr>
                    ) : (
                      questions.map((q) => (
                        <tr key={q.id}>
                          <td>
                            <p className="font-medium text-ink-800 max-w-xs">
                              {q.stem.length > 40 ? `${q.stem.slice(0, 40)}…` : q.stem}
                            </p>
                          </td>
                          <td>
                            <span className={questionTypeConfig[q.questionType]?.cls ?? 'badge-indigo'}>
                              {questionTypeConfig[q.questionType]?.label ?? q.questionType}
                            </span>
                          </td>
                          <td className="text-ink-600 text-xs">
                            {q.options?.length ?? 0} 项
                          </td>
                          <td className="text-ink-700">{q.defaultScore} 分</td>
                          <td>
                            <div className="flex items-center justify-end gap-1">
                              <button
                                className="btn-ghost"
                                title="编辑"
                                onClick={() => openEditQuestion(q)}
                              >
                                <Pencil className="w-4 h-4" />
                              </button>
                              <button
                                className="btn-ghost text-red-600"
                                title="删除"
                                onClick={() => handleDeleteQuestion(q)}
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
          </div>
        )}
      </div>

      {createExamModal(showCreate, examForm, setExamForm, examCourseOptions, bankQuestions, orderedPaper, selectedPaperCount, totalPaperScore, togglePaperQuestion, setPaperScore, () => setShowCreate(false), handleCreateExam, bankLoading)}
      {questionModal(showQuestionModal, setShowQuestionModal, editingQuestion, bankCourseOptions, courseIdForModal(editingQuestion?.courseId ?? '', courses, bankCourseId), handleQuestionSaved)}
    </>
  );
}

function courseIdForModal(preferred: string, courses: Course[], fallback: string): string {
  if (preferred) return preferred;
  if (fallback) return fallback;
  // 默认选第一门已发布课程
  const first = courses.find((c) => c.status === 'PUBLISHED');
  return first?.id ?? '';
}

function createExamModal(
  show: boolean,
  form: typeof emptyExamForm,
  setForm: (f: typeof emptyExamForm) => void,
  courseOptions: SelectOption[],
  bankQuestions: ExamBankQuestion[],
  orderedPaper: Array<{ q?: ExamBankQuestion; config: { score: number; order: number } }>,
  selectedCount: number,
  totalScore: number,
  togglePaper: (qid: string) => void,
  setScore: (qid: string, score: number) => void,
  onClose: () => void,
  onCreate: (publish: boolean) => void,
  _bankLoading: boolean,
) {
  if (!show) return null;
  return createPortal(
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
          className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-2xl overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
        >
          <div className="flex items-center justify-between">
            <h2 id="create-exam-title" className="font-display text-xl font-semibold text-ink-900">
              创建考试
            </h2>
            <button onClick={onClose} className="btn-ghost text-ink-400">
              <X className="w-5 h-5" />
            </button>
          </div>
          <div className="mt-4 space-y-4">
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">考试标题</label>
              <input
                type="text"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                className="input-field"
                placeholder="例如：期末考试"
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">关联课程</label>
              <CustomSelect
                options={courseOptions}
                value={form.courseId}
                onChange={(v) => setForm({ ...form, courseId: v })}
                placeholder="请选择课程"
                minWidth="w-full"
              />
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">时长（分钟）</label>
                <input
                  type="number"
                  value={form.duration}
                  onChange={(e) => setForm({ ...form, duration: e.target.value })}
                  className="input-field"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">及格分</label>
                <input
                  type="number"
                  value={form.passScore}
                  onChange={(e) => setForm({ ...form, passScore: e.target.value })}
                  className="input-field"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">开考时间</label>
                <input
                  type="datetime-local"
                  value={form.startTime}
                  onChange={(e) => setForm({ ...form, startTime: e.target.value })}
                  className="input-field"
                />
              </div>
            </div>

            {/* 组卷步骤 */}
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">
                组卷（已勾选 {selectedCount} 题 · 总分 {totalScore} 分）
              </label>
              <div className="rounded-xl border border-ink-200 divide-y divide-ink-100 max-h-64 overflow-y-auto">
                {!form.courseId ? (
                  <p className="text-sm text-ink-400 px-4 py-6 text-center">请先选择课程，再勾选题库题目</p>
                ) : bankQuestions.length === 0 ? (
                  <p className="text-sm text-ink-400 px-4 py-6 text-center">
                    该课程暂无题库题目，请先到「题库管理」中添加
                  </p>
                ) : (
                  bankQuestions.map((q) => {
                    const cfg = orderedPaper.find((item) => item.q?.id === q.id)?.config;
                    const checked = Boolean(cfg);
                    return (
                      <div key={q.id} className="px-3.5 py-2.5 flex items-center gap-3">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => togglePaper(q.id)}
                          className="w-4 h-4 accent-indigo-600 flex-shrink-0"
                        />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm text-ink-800 truncate">{q.stem}</p>
                          <p className="text-xs text-ink-400">
                            {questionTypeConfig[q.questionType]?.label} · 默认 {q.defaultScore} 分
                          </p>
                        </div>
                        <input
                          type="number"
                          value={cfg ? String(cfg.score || q.defaultScore) : q.defaultScore}
                          disabled={!checked}
                          onChange={(e) => setScore(q.id, Number(e.target.value))}
                          className="input-field w-20"
                        />
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            <div className="flex gap-3 pt-1">
              <button onClick={() => onCreate(true)} className="btn-primary flex-1">保存并发布</button>
              <button onClick={() => onCreate(false)} className="btn-outline flex-1">仅存草稿</button>
              <button onClick={onClose} className="btn-outline flex-1">取消</button>
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}

function questionModal(
  show: boolean,
  setShow: (v: boolean) => void,
  editing: ExamBankQuestion | null,
  bankCourseOptions: Array<{ value: string; label: string; image?: string }>,
  initialCourseId: string,
  onSaved: (q: ExamBankQuestion) => void,
) {
  if (!show) return null;
  return (
    <QuestionFormModal
      editing={editing}
      courseOptions={bankCourseOptions}
      initialCourseId={initialCourseId}
      onClose={() => setShow(false)}
      onSaved={onSaved}
    />
  );
}

function QuestionFormModal({
  editing,
  courseOptions,
  initialCourseId,
  onClose,
  onSaved,
}: {
  editing: ExamBankQuestion | null;
  courseOptions: Array<{ value: string; label: string; image?: string }>;
  initialCourseId: string;
  onClose: () => void;
  onSaved: (q: ExamBankQuestion) => void;
}) {
  const [courseId, setCourseId] = useState(initialCourseId);
  const [questionType, setQuestionType] = useState<ExamQuestionType>(editing?.questionType ?? 'SINGLE');
  const [stem, setStem] = useState(editing?.stem ?? '');
  const [options, setOptions] = useState<string[]>(
    editing ? [...(editing.options ?? [])] : ['', ''],
  );
  const [answerIdx, setAnswerIdx] = useState<number[]>(editing?.answer ?? []);
  const [analysis, setAnalysis] = useState(editing?.analysis ?? '');
  const [defaultScore, setDefaultScore] = useState(String(editing?.defaultScore ?? 10));
  const [saving, setSaving] = useState(false);

  // 判断题固定选项；单选/多选重置选项容器
  useEffect(() => {
    if (questionType === 'JUDGE') {
      setOptions([...JUDGE_OPTIONS]);
      setAnswerIdx((prev) => (prev.length ? prev : [0]));
    } else {
      setOptions((prev) => (prev.length ? prev : ['', '']));
    }
  }, [questionType]);

  const addOption = () => setOptions((prev) => [...prev, '']);
  const removeOption = (idx: number) => {
    setOptions((prev) => prev.filter((_, i) => i !== idx));
    setAnswerIdx((prev) => prev.filter((i) => i !== idx).map((i) => (i > idx ? i - 1 : i)));
  };
  const setOptionText = (idx: number, text: string) =>
    setOptions((prev) => prev.map((o, i) => (i === idx ? text : o)));

  const toggleAnswer = (idx: number) => {
    if (questionType === 'MULTIPLE') {
      setAnswerIdx((prev) =>
        prev.includes(idx) ? prev.filter((i) => i !== idx) : [...prev, idx].sort((a, b) => a - b),
      );
    } else {
      setAnswerIdx([idx]);
    }
  };

  const handleSave = async () => {
    if (!courseId) {
      window.alert('请选择课程');
      return;
    }
    if (!stem.trim()) {
      window.alert('请输入题干');
      return;
    }
    const cleanOptions = options.map((o) => o.trim());
    if (cleanOptions.some((o) => !o)) {
      window.alert('选项不能为空');
      return;
    }
    if (answerIdx.length === 0) {
      window.alert('请设置正确答案');
      return;
    }
    if (!Number.isFinite(Number(defaultScore)) || Number(defaultScore) <= 0) {
      window.alert('默认分值必须大于 0');
      return;
    }
    setSaving(true);
    const payload = {
      courseId,
      questionType,
      stem: stem.trim(),
      options: cleanOptions,
      answer: answerIdx,
      analysis: analysis.trim(),
      defaultScore: Number(defaultScore),
    };
    let saved: ExamBankQuestion;
    try {
      if (editing) {
        saved = await api.updateQuestion(editing.id, payload);
      } else {
        saved = await api.createQuestion(payload);
      }
      onSaved(saved);
    } finally {
      setSaving(false);
    }
  };

  const selectOptions: SelectOption[] = [
    { value: '', label: '请选择课程' },
    ...courseOptions.map((c) => ({ value: c.value, label: c.label, image: c.image })),
  ];

  return createPortal(
    <div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
      <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
          <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
        </div>
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="question-modal-title"
          className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-xl overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
        >
          <div className="flex items-center justify-between">
            <h2 id="question-modal-title" className="font-display text-xl font-semibold text-ink-900">
              {editing ? '编辑题目' : '新建题目'}
            </h2>
            <button onClick={onClose} className="btn-ghost text-ink-400">
              <X className="w-5 h-5" />
            </button>
          </div>
          <div className="mt-4 space-y-4">
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">所属课程</label>
              <CustomSelect
                options={selectOptions}
                value={courseId}
                onChange={setCourseId}
                placeholder="请选择课程"
                minWidth="w-full"
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">题型</label>
              <div className="flex gap-2">
                {(Object.keys(questionTypeConfig) as ExamQuestionType[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setQuestionType(t)}
                    className={cn(
                      'px-3 py-2 text-sm rounded-lg border font-medium transition-colors',
                      questionType === t
                        ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                        : 'border-ink-200 text-ink-600 hover:border-ink-300',
                    )}
                  >
                    {questionTypeConfig[t].label}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">题干</label>
              <textarea
                value={stem}
                onChange={(e) => setStem(e.target.value)}
                className="input-field min-h-[80px]"
                placeholder="请输入题目内容"
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-ink-700">
                选项（<span className="text-ink-500">{questionType === 'MULTIPLE' ? '多选' : '单选'}</span>，勾选正确答案）
              </label>
              <div className="space-y-2">
                {options.map((opt, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    {questionType === 'MULTIPLE' ? (
                      <input
                        type="checkbox"
                        checked={answerIdx.includes(idx)}
                        onChange={() => toggleAnswer(idx)}
                        className="w-4 h-4 accent-indigo-600 flex-shrink-0"
                      />
                    ) : (
                      <input
                        type="radio"
                        name="question-answer"
                        checked={answerIdx[0] === idx}
                        onChange={() => toggleAnswer(idx)}
                        className="w-4 h-4 accent-indigo-600 flex-shrink-0"
                      />
                    )}
                    <input
                      type="text"
                      value={opt}
                      disabled={questionType === 'JUDGE'}
                      onChange={(e) => setOptionText(idx, e.target.value)}
                      className="input-field"
                      placeholder={`选项 ${String.fromCharCode(65 + idx)}`}
                    />
                    {questionType !== 'JUDGE' && (
                      <button
                        type="button"
                        className="btn-ghost text-ink-400"
                        title="删除选项"
                        onClick={() => removeOption(idx)}
                      >
                        <X className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                ))}
              </div>
              {questionType !== 'JUDGE' && (
                <button
                  type="button"
                  onClick={addOption}
                  className="mt-2 inline-flex items-center gap-1 text-sm text-indigo-600 font-medium"
                >
                  <Plus className="w-4 h-4" /> 添加选项
                </button>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">默认分值</label>
                <input
                  type="number"
                  value={defaultScore}
                  onChange={(e) => setDefaultScore(e.target.value)}
                  className="input-field"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-ink-700">答案解析</label>
                <input
                  type="text"
                  value={analysis}
                  onChange={(e) => setAnalysis(e.target.value)}
                  className="input-field"
                  placeholder="可选"
                />
              </div>
            </div>
            <div className="flex gap-3 pt-1">
              <button onClick={handleSave} disabled={saving} className="btn-primary flex-1">
                {saving ? '保存中…' : editing ? '保存修改' : '确认添加'}
              </button>
              <button onClick={onClose} className="btn-outline flex-1">取消</button>
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
