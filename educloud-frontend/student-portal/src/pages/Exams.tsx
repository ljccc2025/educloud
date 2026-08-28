import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Clock, FileQuestion, Award, Play, Calendar, CheckCircle2 } from 'lucide-react';
import { studentAssignmentService } from '@/services/studentAssignmentService';
import ExamSessionModal from '@/components/exams/ExamSessionModal';
import type { Exam, ExamStatus } from '@/types';
import { cn } from '@/utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<ExamStatus, { label: string; className: string }> = {
  NOT_STARTED: { label: '未开始', className: 'badge-indigo' },
  IN_PROGRESS: { label: '进行中', className: 'badge-amber' },
  SUBMITTED: { label: '已提交', className: 'badge-indigo' },
  GRADED: { label: '已批改', className: 'badge-green' },
  ENDED: { label: '已结束', className: 'badge-red' },
};

export default function Exams() {
  const [exams, setExams] = useState<Exam[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeExam, setActiveExam] = useState<Exam | null>(null);

  const loadExams = async () => {
    try {
      const data = await studentAssignmentService.getExams();
      setExams(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadExams();
  }, []);

  const handleExamComplete = (updated: Exam) => {
    setExams((prev) =>
      prev.map((item) => (String(item.id) === String(updated.id) ? updated : item))
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <span className="section-label mb-3">考试中心</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">我的考试</h1>
        <p className="text-ink-500 mt-3">检验学习成果，迎接每一次挑战</p>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {exams.map((exam) => {
            const config = statusConfig[exam.status];
            const isCompleted = exam.status === 'GRADED' || exam.status === 'SUBMITTED';

            return (
              <div key={exam.id} className="card-editorial p-6 flex flex-col justify-between">
                <div>
                  <div className="flex items-start justify-between mb-4">
                    <div className="w-12 h-12 bg-indigo-50 flex items-center justify-center rounded-2xl">
                      <FileQuestion size={24} className="text-indigo-800" strokeWidth={1.5} />
                    </div>
                    <span className={config.className}>{config.label}</span>
                  </div>

                  <h3 className="font-display text-lg font-bold text-ink-900 mb-1">
                    {exam.title}
                  </h3>
                  <p className="text-sm font-medium text-indigo-800 mb-4">
                    {exam.courseTitle}
                  </p>

                  <div className="space-y-2.5 text-sm text-ink-500 mb-6">
                    <div className="flex items-center gap-2">
                      <Clock size={15} className="text-ink-300" />
                      考试时长：{exam.duration} 分钟
                    </div>
                    <div className="flex items-center gap-2">
                      <FileQuestion size={15} className="text-ink-300" />
                      题目数量：{exam.questions?.length || exam.totalQuestions} 题
                    </div>
                    <div className="flex items-center gap-2">
                      <Award size={15} className="text-ink-300" />
                      满分：{exam.totalScore} 分（及格：{exam.passScore || 60} 分）
                    </div>
                    <div className="flex items-center gap-2">
                      <Calendar size={15} className="text-ink-300" />
                      截止时间：{exam.endTime || '长期有效'}
                    </div>
                  </div>

                  {exam.status === 'GRADED' && exam.score !== undefined && (
                    <div className="bg-indigo-50/70 border border-indigo-100 p-4 mb-4 text-center rounded-2xl">
                      <p className="text-xs text-ink-500 mb-1">考核成绩</p>
                      <p className="font-display text-3xl font-bold text-indigo-800">
                        {exam.score}
                        <span className="text-base text-ink-400 font-normal">/{exam.totalScore}</span>
                      </p>
                    </div>
                  )}
                </div>

                <div className="mt-2">
                  {isCompleted ? (
                    <button
                      type="button"
                      onClick={() => setActiveExam(exam)}
                      className="btn-outline w-full cursor-pointer text-xs !py-2.5"
                    >
                      查看答卷与解析
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setActiveExam(exam)}
                      className="btn-primary w-full flex items-center justify-center gap-2 cursor-pointer text-xs !py-2.5"
                    >
                      <Play size={15} />
                      开始考试
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Exam Rules */}
      <div className="mt-12 bg-amber-50/70 border border-amber-200/80 rounded-2xl p-6">
        <h3 className="font-display text-lg font-bold text-amber-900 mb-3">考试须知</h3>
        <ul className="grid md:grid-cols-2 gap-2 text-sm text-amber-800">
          <li className="flex items-start gap-2">
            <span className="text-amber-600 font-bold">1.</span>
            考试开始后请在规定时间内完成作答，系统将自动计时。
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600 font-bold">2.</span>
            提交后系统将根据标准答案即时阅卷并生成能力考核评估。
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600 font-bold">3.</span>
            考试支持查阅解析与错题回顾，助力查漏补缺。
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600 font-bold">4.</span>
            如遇网络或技术问题，可刷新重新进入作答。
          </li>
        </ul>
      </div>

      {/* Online Exam Session Modal */}
      {activeExam && (
        <ExamSessionModal
          exam={activeExam}
          isOpen={Boolean(activeExam)}
          onClose={() => setActiveExam(null)}
          onExamComplete={handleExamComplete}
        />
      )}
    </div>
  );
}
