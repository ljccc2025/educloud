import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X, Clock, Award } from 'lucide-react';
import type { Exam, ExamQuestion } from '../../types';
import { studentAssignmentService } from '../../services/studentAssignmentService';
import { cn } from '../../utils/cn';

interface ExamSessionModalProps {
  exam: Exam | null;
  isOpen: boolean;
  onClose: () => void;
  onExamComplete: (updated: Exam) => void;
}

export default function ExamSessionModal({
  exam,
  isOpen,
  onClose,
  onExamComplete,
}: ExamSessionModalProps) {
  const [answers, setAnswers] = useState<Record<number, number[]>>({});
  const [timeLeftSeconds, setTimeLeftSeconds] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ score: number; passed: boolean } | null>(null);
  const [tabSwitchCount, setTabSwitchCount] = useState(0);
  // 同步 ref：防止 handleFinish 在并发闭包中被重复触发
  const submittingRef = useRef(false);
  // 超时自动交卷只触发一次
  const autoSubmittedRef = useRef(false);
  // 缓存已创建的考试 attemptId，避免重复 startExam 触发 409
  const attemptIdRef = useRef<string | number | null>(null);

  useEffect(() => {
    if (isOpen && exam) {
      attemptIdRef.current = null;
      setAnswers({});
      setResult(null);
      setTabSwitchCount(0);
      setTimeLeftSeconds(exam.duration * 60);
      submittingRef.current = false;
      autoSubmittedRef.current = false;
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen, exam]);

  // 弹窗打开时启动考试并缓存 attemptId（仅一次）
  useEffect(() => {
    if (isOpen && exam && attemptIdRef.current === null) {
      studentAssignmentService.startExam(exam.id).then(({ attemptId }) => {
        attemptIdRef.current = attemptId;
      }).catch(() => {
        attemptIdRef.current = `local-${Date.now()}`;
      });
    }
  }, [isOpen, exam]);

  useEffect(() => {
    if (!isOpen || !exam) return;
    const onBlur = () => setTabSwitchCount((c) => c + 1);
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') setTabSwitchCount((c) => c + 1);
    };
    window.addEventListener('blur', onBlur);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      window.removeEventListener('blur', onBlur);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [isOpen, exam]);

  useEffect(() => {
    if (!isOpen || !exam || result || timeLeftSeconds <= 0) return;
    const timer = setInterval(() => {
      setTimeLeftSeconds((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          // 倒计时归零自动交卷（超时锁定）：在 interval 内触发，避免弹窗打开首帧
          // timeLeftSeconds 初始为 0 时被独立 effect 误判为超时立即交卷。
          if (!autoSubmittedRef.current) {
            autoSubmittedRef.current = true;
            void handleFinish();
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [isOpen, exam, result, timeLeftSeconds]);

  if (!isOpen || !exam) return null;

  const questions: ExamQuestion[] = exam.questions || [
    {
      id: 1,
      question: '以下关于微服务架构治理与高可用设计的原则中，哪一项是推荐做法？',
      options: [
        '所有微服务共享同一个单体大数据库以简化关联查询',
        '服务间通过网关统一鉴权与路由，并配置熔断降级策略',
        '直接使用同步 HTTP 长连接替代所有异步消息解耦',
        '微服务内部禁止封装业务模型',
      ],
      questionType: 'SINGLE',
      answer: [1],
    },
    {
      id: 2,
      question: '在分布式一致性协议中，Raft 算法通过什么机制保证日志的一致性？',
      options: [
        'Leader 节点的日志单向复制与多数派确认 (Quorum)',
        '所有节点随机写入',
        '定时将数据全量清空重放',
        '通过客户端协商仲裁',
      ],
      questionType: 'SINGLE',
      answer: [0],
    },
    {
      id: 3,
      question: 'Redis 分布式锁在释放锁时，为什么通常使用 Lua 脚本？',
      options: [
        '为了加快网络传输',
        '保证“判断锁持有者”和“删除锁”这两个操作的原子性',
        '必须通过 Lua 脚本生成 UUID',
        '为了使 Redis 自动开启 AOF 持久化',
      ],
      questionType: 'SINGLE',
      answer: [1],
    },
  ];

  const handleSelectOption = (questionId: number, optionIdx: number, questionType?: string) => {
    if (result || timeLeftSeconds <= 0) return;
    setAnswers((prev) => {
      const current = prev[questionId] ?? [];
      if (questionType === 'MULTIPLE') {
        const next = current.includes(optionIdx)
          ? current.filter((i) => i !== optionIdx)
          : [...current, optionIdx];
        return { ...prev, [questionId]: next };
      }
      return { ...prev, [questionId]: [optionIdx] };
    });
  };

  const handleFinish = async () => {
    if (submitting || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      let attemptId = attemptIdRef.current;
      if (attemptId === null) {
        const started = await studentAssignmentService.startExam(exam.id);
        attemptId = started.attemptId;
        attemptIdRef.current = attemptId;
      }
      const res = await studentAssignmentService.submitExam(exam.id, attemptId, answers, tabSwitchCount);
      setResult(res);
      onExamComplete(res.exam);
    } catch (err) {
      alert(err instanceof Error ? err.message : '交卷失败');
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const formatTime = (secs: number) => {
    const mins = Math.floor(secs / 60);
    const s = secs % 60;
    return `${String(mins).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const answeredCount = Object.keys(answers).length;

  return createPortal(
    <div className="fixed inset-0 z-[100] overflow-y-auto bg-indigo-950/40 backdrop-blur-md flex items-center justify-center p-4 sm:p-6 animate-fade-in">
      <div
        className="relative w-full max-w-3xl bg-white rounded-3xl shadow-2xl border border-ink-100 overflow-hidden flex flex-col max-h-[92vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-ink-100 bg-slate-50/70">
          <div>
            <span className="px-2.5 py-0.5 text-xs font-semibold bg-indigo-50 text-indigo-800 rounded-full">
              在线考试考核
            </span>
            <h2 className="text-lg font-bold text-ink-900 mt-1">{exam.title}</h2>
          </div>

          <div className="flex items-center gap-4">
            {!result && (
              <div className="flex items-center gap-2 px-3 py-1.5 bg-amber-50 border border-amber-200 text-amber-900 rounded-xl text-xs font-mono font-bold">
                <Clock size={15} className="text-amber-600 animate-pulse" />
                <span>剩余时间：{formatTime(timeLeftSeconds)}</span>
              </div>
            )}
            <button
              type="button"
              onClick={onClose}
              className="w-9 h-9 rounded-full bg-white border border-ink-200 text-ink-500 hover:text-ink-900 hover:bg-ink-50 flex items-center justify-center transition-colors"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Result Banner */}
          {result && (
            <div
              className={cn(
                'rounded-2xl p-6 text-center space-y-3 border shadow-sm',
                result.passed
                  ? 'bg-emerald-50/80 border-emerald-200 text-emerald-900'
                  : 'bg-red-50/80 border-red-200 text-red-900'
              )}
            >
              <div
                className={cn(
                  'w-16 h-16 mx-auto rounded-full flex items-center justify-center text-white shadow-md',
                  result.passed ? 'bg-emerald-600' : 'bg-red-600'
                )}
              >
                <Award size={32} />
              </div>
              <h3 className="text-2xl font-bold">
                {result.passed ? '恭喜您，顺利通过本次考核！' : '本次考试未达到及格线，请再接再厉！'}
              </h3>
              <p className="text-sm font-medium">
                考试得分：<span className="text-3xl font-extrabold">{result.score}</span> / {exam.totalScore} 分
                （及格分：{exam.passScore || 60} 分）
              </p>
            </div>
          )}

          {/* Question List */}
          <div className="space-y-6">
            {questions.map((q, qIndex) => {
              const currentAns: number[] = answers[q.id] ?? [];
              return (
                <div
                  key={q.id}
                  className="p-5 bg-slate-50/60 border border-ink-100 rounded-2xl space-y-3 text-sm"
                >
                  <div className="flex items-start gap-2.5 font-semibold text-ink-900">
                    <span className="w-6 h-6 rounded-full bg-indigo-100 text-indigo-800 text-xs flex items-center justify-center shrink-0 mt-0.5">
                      {qIndex + 1}
                    </span>
                    <span className="flex-1">{q.question}</span>
                  </div>

                  <div className="space-y-2 pl-8">
                    {q.options.map((opt, optIndex) => {
                      const isSelected = currentAns.includes(optIndex);
                      return (
                        <div
                          key={optIndex}
                          onClick={() => handleSelectOption(q.id, optIndex, q.questionType)}
                          className={cn(
                            'p-3 rounded-xl border text-xs cursor-pointer transition-all flex items-center justify-between',
                            isSelected
                              ? 'bg-indigo-50/80 border-indigo-600 text-indigo-950 font-medium'
                              : 'bg-white border-ink-100 text-ink-700 hover:border-indigo-300'
                          )}
                        >
                          <div className="flex items-center gap-3">
                            <span
                              className={cn(
                                'w-5 h-5 rounded-full border text-[11px] font-mono flex items-center justify-center shrink-0',
                                isSelected
                                  ? 'bg-indigo-800 text-white border-indigo-800'
                                  : 'border-ink-300 text-ink-500'
                              )}
                            >
                              {String.fromCharCode(65 + optIndex)}
                            </span>
                            <span>{opt}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-ink-100 flex items-center justify-between bg-slate-50/70">
          <div className="text-xs text-ink-500">
            答题进度：<b>{answeredCount}</b> / {questions.length} 题
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 rounded-xl border border-ink-200 text-xs font-semibold text-ink-600 hover:bg-ink-50 transition-colors"
            >
              {result ? '返回考试列表' : '退出'}
            </button>
            {!result && (
              <button
                type="button"
                onClick={handleFinish}
                disabled={submitting || timeLeftSeconds <= 0}
                className="px-6 py-2.5 rounded-xl bg-indigo-800 hover:bg-indigo-900 text-white text-xs font-semibold shadow-md flex items-center gap-2 transition-all disabled:opacity-50"
              >
                {submitting ? '正在阅卷...' : timeLeftSeconds <= 0 ? '时间到，正在交卷...' : '确认交卷'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}
