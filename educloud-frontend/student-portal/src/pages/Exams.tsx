import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Clock, FileQuestion, Award, Play, Calendar } from 'lucide-react';
import { examApi } from '@/services/api';
import type { Exam, ExamStatus } from '@/types';
import { cn } from '@/utils/cn';
import dayjs from 'dayjs';

const statusConfig: Record<ExamStatus, { label: string; className: string }> = {
  NOT_STARTED: { label: '未开始', className: 'badge-indigo' },
  IN_PROGRESS: { label: '进行中', className: 'badge-amber' },
  SUBMITTED: { label: '已提交', className: 'badge-indigo' },
  GRADED: { label: '已批改', className: 'badge-green' },
};

export default function Exams() {
  const [exams, setExams] = useState<Exam[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    examApi.getAll().then((data) => {
      setExams(data);
      setLoading(false);
    });
  }, []);

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
            const canStart = exam.status === 'NOT_STARTED' &&
              exam.startTime !== undefined &&
              exam.endTime !== undefined &&
              dayjs().isAfter(dayjs(exam.startTime)) &&
              dayjs().isBefore(dayjs(exam.endTime));
            const isUpcoming = exam.status === 'NOT_STARTED' &&
              exam.startTime !== undefined &&
              dayjs().isBefore(dayjs(exam.startTime));

            return (
              <div key={exam.id} className="card-editorial p-6 flex flex-col">
                <div className="flex items-start justify-between mb-4">
                  <div className="w-12 h-12 bg-indigo-50 flex items-center justify-center">
                    <FileQuestion size={24} className="text-indigo-800" strokeWidth={1.5} />
                  </div>
                  <span className={config.className}>{config.label}</span>
                </div>

                <h3 className="font-display text-lg font-bold text-ink-900 mb-1">
                  {exam.title}
                </h3>
                <Link
                  to={`/courses/${exam.courseId}`}
                  className="text-sm text-indigo-800 link-underline mb-4"
                >
                  {exam.courseTitle}
                </Link>

                <div className="space-y-2.5 text-sm text-ink-500 mb-6 flex-1">
                  <div className="flex items-center gap-2">
                    <Clock size={15} className="text-ink-300" />
                    考试时长：{exam.duration} 分钟
                  </div>
                  <div className="flex items-center gap-2">
                    <FileQuestion size={15} className="text-ink-300" />
                    题目数量：{exam.totalQuestions} 题
                  </div>
                  <div className="flex items-center gap-2">
                    <Award size={15} className="text-ink-300" />
                    总分：{exam.totalScore} 分
                  </div>
                  <div className="flex items-center gap-2">
                    <Calendar size={15} className="text-ink-300" />
                    {isUpcoming
                      ? `开始时间：${exam.startTime}`
                      : `截止时间：${exam.endTime}`}
                  </div>
                </div>

                {exam.status === 'GRADED' && exam.score !== undefined && (
                  <div className="bg-indigo-50 border border-indigo-100 p-4 mb-4 text-center">
                    <p className="text-xs text-ink-500 mb-1">考试成绩</p>
                    <p className="font-display text-4xl font-bold text-indigo-800">
                      {exam.score}
                      <span className="text-lg text-ink-400">/{exam.totalScore}</span>
                    </p>
                  </div>
                )}

                <div>
                  {exam.status === 'GRADED' || exam.status === 'SUBMITTED' ? (
                    <button type="button" className="btn-outline w-full">
                      查看详情
                    </button>
                  ) : canStart ? (
                    <button type="button" className="btn-primary w-full">
                      <Play size={16} />
                      开始考试
                    </button>
                  ) : isUpcoming ? (
                    <button type="button" disabled className="w-full py-3 bg-ink-100 text-ink-400 text-sm font-medium cursor-not-allowed">
                      尚未开放
                    </button>
                  ) : exam.status === 'NOT_STARTED' ? (
                    <button type="button" disabled className="w-full py-3 bg-ink-100 text-ink-400 text-sm font-medium cursor-not-allowed">
                      尚未开放
                    </button>
                  ) : (
                    <button type="button" className="btn-primary w-full">
                      <Play size={16} />
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
      <div className="mt-12 bg-amber-50 border border-amber-200 p-6">
        <h3 className="font-display text-lg font-bold text-amber-900 mb-3">考试须知</h3>
        <ul className="grid md:grid-cols-2 gap-2 text-sm text-amber-800">
          <li className="flex items-start gap-2">
            <span className="text-amber-600">1.</span>
            考试开始后请在规定时间内完成，超时将自动提交。
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600">2.</span>
            考试过程中请勿切换浏览器窗口，系统将自动记录。
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600">3.</span>
            考试仅有一次作答机会，请认真准备后再开始。
          </li>
          <li className="flex items-start gap-2">
            <span className="text-amber-600">4.</span>
            如遇技术问题请及时联系客服或授课讲师。
          </li>
        </ul>
      </div>
    </div>
  );
}
