import { useEffect, useRef, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Inbox,
  Pencil,
  Plus,
  Send,
} from 'lucide-react';
import { api } from '../services/api';
import AssignmentPublishModal from '../components/AssignmentPublishModal';
import GradeSheet from '../components/GradeSheet';
import type {
  Assignment,
  AssignmentDraftInput,
  AssignmentStatus,
  Course,
} from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

const assignmentStatusConfig = {
  DRAFT: { label: '草稿', className: 'badge-amber' },
  PUBLISHED: { label: '已发布', className: 'badge-green' },
  CLOSED: { label: '已关闭', className: 'badge-indigo' },
} satisfies Record<AssignmentStatus, { label: string; className: string }>;

export default function AssignmentGrade() {
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [selectedId, setSelectedId] = useState<string>('');
  const [selectedSubmissionIds, setSelectedSubmissionIds] = useState<Record<string, string>>({});
  const [editingAssignment, setEditingAssignment] = useState<Assignment | null>(null);
  const [showPublishModal, setShowPublishModal] = useState(false);
  const [loading, setLoading] = useState(true);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const publishButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let active = true;
    Promise.all([api.getAssignments(), api.getCourses()])
      .then(([assignmentData, courseData]) => {
        if (!active) return;
        setAssignments(assignmentData);
        setCourses(courseData);
        if (assignmentData.length > 0) setSelectedId(assignmentData[0].id);
      })
      .catch(() => {
        if (!active) return;
        setAssignments([]);
        setCourses([]);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const selected = assignments.find((a) => a.id === selectedId);
  const rememberedSubmissionId = selected ? selectedSubmissionIds[selected.id] : undefined;
  const selectedSubmissionId = selected
    ? selected.submissions.some((submission) => submission.id === rememberedSubmissionId)
      ? rememberedSubmissionId ?? ''
      : selected.submissions[0]?.id ?? ''
    : '';

  const closePublishModal = () => {
    setShowPublishModal(false);
    setEditingAssignment(null);
  };

  const openCreate = (event: React.MouseEvent<HTMLButtonElement>) => {
    returnFocusRef.current = event.currentTarget;
    setEditingAssignment(null);
    setShowPublishModal(true);
  };

  const openEdit = (event: React.MouseEvent<HTMLButtonElement>, assignment: Assignment) => {
    returnFocusRef.current = event.currentTarget;
    setEditingAssignment(assignment);
    setShowPublishModal(true);
  };

  const upsertAssignment = (assignment: Assignment) => {
    setAssignments((current) => [
      assignment,
      ...current.filter((item) => item.id !== assignment.id),
    ]);
    setSelectedId(assignment.id);
  };

  const handleSaveDraft = async (values: AssignmentDraftInput) => {
    const saved = editingAssignment
      ? await api.updateAssignmentDraft(editingAssignment.id, values)
      : await api.createAssignmentDraft(values);
    upsertAssignment(saved);
    closePublishModal();
  };

  const handlePublish = async (values: AssignmentDraftInput) => {
    const draft = editingAssignment
      ? await api.updateAssignmentDraft(editingAssignment.id, values)
      : await api.createAssignmentDraft(values);
    const published = await api.publishAssignment(draft.id);
    upsertAssignment(published);
    closePublishModal();
  };

  const handleGrade = async (submissionId: string, score: number, feedback: string) => {
    await api.gradeSubmission(submissionId, score, feedback);
    // Refresh
    const updated = await api.getAssignments();
    setAssignments(updated);
  };

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="section-label mb-2">作业批改</p>
          <h1 className="display-heading text-3xl md:text-4xl">作业批改</h1>
          <p className="text-ink-500 mt-2 text-sm">发布课程作业，批阅学员提交并给出反馈</p>
        </div>
        <button
          ref={publishButtonRef}
          onClick={openCreate}
          className="btn-primary w-full sm:w-auto"
        >
          <Plus className="h-4 w-4" />
          发布作业
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Assignment list */}
        <div className="lg:col-span-1">
          <div className="card-editorial p-4 sticky top-24">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-400 mb-3">
              作业列表
            </h3>
            <div className="space-y-2">
              {loading ? (
                <p className="text-sm text-ink-400 py-4 text-center">加载中…</p>
              ) : (
                assignments.map((a) => {
                  const pending = a.submissionCount - a.gradedCount;
                  const isActive = selectedId === a.id;
                  const status = assignmentStatusConfig[a.status];
                  return (
                    <button
                      key={a.id}
                      onClick={() => setSelectedId(a.id)}
                      className={cn(
                        'w-full text-left p-3 border transition-all rounded-lg',
                        isActive
                          ? 'border-indigo-800 bg-indigo-50/50'
                          : 'border-ink-100 hover:border-ink-300 bg-white'
                      )}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <p className={cn(
                          'text-sm font-medium line-clamp-2',
                          isActive ? 'text-indigo-800' : 'text-ink-800'
                        )}>
                          {a.title}
                        </p>
                        <span className={cn(status.className, 'shrink-0')}>{status.label}</span>
                      </div>
                      <p className="text-xs text-ink-400 mt-1">{a.courseName}</p>
                      <div className="flex items-center gap-3 mt-2 text-xs">
                        {a.status === 'DRAFT' ? (
                          <span className="flex items-center gap-1 text-amber-700">
                            <Pencil className="h-3 w-3" />
                            尚未发布
                          </span>
                        ) : (
                          <>
                            <span className="flex items-center gap-1 text-ink-500">
                              <ClipboardCheck className="w-3 h-3" />
                              {a.submissionCount}
                            </span>
                            {a.submissionCount === 0 ? (
                              <span className="flex items-center gap-1 text-indigo-600">
                                <Inbox className="h-3 w-3" />
                                等待提交
                              </span>
                            ) : pending > 0 ? (
                              <span className="flex items-center gap-1 text-amber-600">
                                <AlertCircle className="w-3 h-3" />
                                {pending} 待批
                              </span>
                            ) : (
                              <span className="flex items-center gap-1 text-green-600">
                                <CheckCircle2 className="w-3 h-3" />
                                已批完
                              </span>
                            )}
                          </>
                        )}
                        <span className="flex items-center gap-1 text-ink-400 ml-auto">
                          <Clock className="w-3 h-3" />
                          {a.dueDate ? dayjs(a.dueDate).format('MM-DD') : '未设置'}
                        </span>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </div>

        {/* Grade sheet */}
        <div className="lg:col-span-3">
          {selected?.status === 'DRAFT' ? (
            <div className="space-y-4">
              <div className="card-editorial p-5 sm:p-6">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <p className="text-xs font-medium uppercase tracking-wider text-amber-600">
                        {selected.courseName}
                      </p>
                      <span className="badge-amber">草稿</span>
                    </div>
                    <h2 className="font-display text-xl font-semibold text-ink-900">
                      {selected.title}
                    </h2>
                    <p className="mt-2 max-w-2xl text-sm text-ink-500">
                      {selected.description || '尚未填写作业说明。'}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    <button
                      onClick={(event) => openEdit(event, selected)}
                      className="btn-outline"
                    >
                      <Pencil className="h-4 w-4" />
                      编辑草稿
                    </button>
                    <button
                      onClick={(event) => openEdit(event, selected)}
                      className="btn-primary"
                    >
                      <Send className="h-4 w-4" />
                      立即发布
                    </button>
                  </div>
                </div>

                <div className="mt-5 grid grid-cols-2 gap-4 border-t border-ink-100 pt-5 sm:grid-cols-4">
                  <div>
                    <p className="text-xs text-ink-400">截止时间</p>
                    <p className="mt-1 text-sm font-medium text-ink-700">
                      {selected.dueDate
                        ? dayjs(selected.dueDate).format('YYYY-MM-DD HH:mm')
                        : '未设置'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-400">满分</p>
                    <p className="mt-1 text-sm font-medium text-ink-700">{selected.totalScore}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-400">提交次数</p>
                    <p className="mt-1 text-sm font-medium text-ink-700">
                      最多 {selected.maxAttempts} 次
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-400">迟交规则</p>
                    <p className="mt-1 text-sm font-medium text-ink-700">
                      {selected.allowLateSubmission ? '允许迟交' : '不允许迟交'}
                    </p>
                  </div>
                </div>
              </div>

              <div className="card-editorial px-6 py-14 text-center">
                <Pencil className="mx-auto mb-3 h-10 w-10 text-amber-300" />
                <p className="font-medium text-ink-600">作业尚未发布</p>
                <p className="mt-1 text-sm text-ink-400">完善作业信息后发布，学生才能查看和提交。</p>
              </div>
            </div>
          ) : selected ? (
            <div className="space-y-4">
              {/* Assignment header */}
              <div className="card-editorial p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <p className="text-xs font-medium uppercase tracking-wider text-amber-600">
                        {selected.courseName}
                      </p>
                      <span className={assignmentStatusConfig[selected.status].className}>
                        {assignmentStatusConfig[selected.status].label}
                      </span>
                    </div>
                    <h2 className="font-display text-xl font-semibold text-ink-900">
                      {selected.title}
                    </h2>
                    <p className="text-sm text-ink-500 mt-1 max-w-2xl">{selected.description}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-ink-400">截止时间</p>
                    <p className="font-medium text-ink-700">
                      {selected.dueDate
                        ? dayjs(selected.dueDate).format('YYYY-MM-DD HH:mm')
                        : '未设置'}
                    </p>
                  </div>
                </div>
                <div className="flex gap-6 mt-4 pt-4 border-t border-ink-100">
                  <div>
                    <p className="text-2xl font-display font-bold text-ink-900">
                      {selected.submissionCount}
                    </p>
                    <p className="text-xs text-ink-400">已提交</p>
                  </div>
                  <div>
                    <p className="text-2xl font-display font-bold text-green-600">
                      {selected.gradedCount}
                    </p>
                    <p className="text-xs text-ink-400">已批改</p>
                  </div>
                  <div>
                    <p className="text-2xl font-display font-bold text-amber-600">
                      {selected.submissionCount - selected.gradedCount}
                    </p>
                    <p className="text-xs text-ink-400">待批改</p>
                  </div>
                  <div>
                    <p className="text-2xl font-display font-bold text-indigo-800">
                      {selected.totalScore}
                    </p>
                    <p className="text-xs text-ink-400">满分</p>
                  </div>
                </div>
              </div>

              {selected.submissions.length > 0 ? (
                <GradeSheet
                  submissions={selected.submissions}
                  totalScore={selected.totalScore}
                  selectedSubmissionId={selectedSubmissionId}
                  onSelectSubmission={(submissionId) =>
                    setSelectedSubmissionIds((current) => ({
                      ...current,
                      [selected.id]: submissionId,
                    }))
                  }
                  onGrade={handleGrade}
                />
              ) : (
                <div className="card-editorial px-6 py-16 text-center">
                  <Inbox className="mx-auto mb-3 h-11 w-11 text-indigo-200" />
                  <p className="font-medium text-ink-600">
                    {selected.status === 'CLOSED' ? '暂无历史提交' : '等待学生提交作业'}
                  </p>
                  <p className="mt-1 text-sm text-ink-400">
                    {selected.status === 'CLOSED'
                      ? '该作业已关闭，当前没有可查看的提交记录。'
                      : '学生提交后，将在这里显示批改列表。'}
                  </p>
                </div>
              )}
            </div>
          ) : (
            <div className="card-editorial p-16 text-center text-ink-400">
              请从左侧选择一份作业
            </div>
          )}
        </div>
      </div>

      {showPublishModal && (
        <AssignmentPublishModal
          courses={courses}
          initialAssignment={editingAssignment}
          returnFocusRef={returnFocusRef}
          fallbackFocusRef={publishButtonRef}
          onClose={closePublishModal}
          onSaveDraft={handleSaveDraft}
          onPublish={handlePublish}
        />
      )}
    </div>
  );
}
