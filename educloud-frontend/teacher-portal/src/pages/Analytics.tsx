import { useEffect, useState } from 'react';
import { TrendingUp, BookOpen, Users, CircleDollarSign, Award, BarChart3 } from 'lucide-react';
import { api } from '../services/api';
import type { AnalyticsStats, EnrollmentTrend, RevenueData, EngagementData } from '../types';
import { cn } from '../utils/cn';

export default function Analytics() {
  const [stats, setStats] = useState<AnalyticsStats | null>(null);
  const [enrollment, setEnrollment] = useState<EnrollmentTrend[]>([]);
  const [revenue, setRevenue] = useState<RevenueData[]>([]);
  const [engagement, setEngagement] = useState<EngagementData[]>([]);

  useEffect(() => {
    api.getStats().then(setStats);
    api.getEnrollmentTrend().then(setEnrollment);
    api.getRevenueData().then(setRevenue);
    api.getEngagementData().then(setEngagement);
  }, []);

  const maxEnrollment = Math.max(...enrollment.map((e) => e.count), 1);
  const maxRevenue = Math.max(...revenue.map((r) => r.amount), 1);
  const maxEngagement = 100;

  const statCards = [
    { label: '课程总数', value: stats?.totalCourses ?? 0, suffix: '门', icon: BookOpen, color: 'text-indigo-600 bg-indigo-50' },
    { label: '学员总数', value: stats?.totalStudents.toLocaleString() ?? 0, suffix: '人', icon: Users, color: 'text-amber-600 bg-amber-50' },
    { label: '累计收入', value: stats ? `¥${(stats.totalRevenue / 10000).toFixed(1)}万` : '¥0', suffix: '', icon: CircleDollarSign, color: 'text-green-600 bg-green-50' },
    { label: '完课率', value: stats?.completionRate ?? 0, suffix: '%', icon: Award, color: 'text-indigo-600 bg-indigo-50' },
  ];

  return (
    <div className="space-y-8 animate-fade-up">
      {/* Header */}
      <div>
        <p className="section-label mb-2">数据分析</p>
        <h1 className="display-heading text-3xl md:text-4xl">教学数据概览</h1>
        <p className="text-ink-500 mt-2 text-sm">追踪课程报名、收入趋势与学员参与度</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((card) => (
          <div key={card.label} className="stat-card">
            <div className={cn('w-10 h-10 flex items-center justify-center mb-4 rounded-lg', card.color)}>
              <card.icon className="w-5 h-5" strokeWidth={1.5} />
            </div>
            <p className="font-display text-3xl font-bold text-ink-900">
              {card.value}
              <span className="text-base font-normal text-ink-400 ml-1">{card.suffix}</span>
            </p>
            <p className="text-sm text-ink-500 mt-1">{card.label}</p>
          </div>
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Enrollment trend */}
        <div className="card-editorial p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="font-display text-lg font-semibold text-ink-900 flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-amber-600" />
                报名趋势
              </h2>
              <p className="text-xs text-ink-400 mt-1">近 6 个月新增学员数</p>
            </div>
            <span className="badge-indigo">月度</span>
          </div>
          <div className="flex items-end gap-3 h-56">
            {enrollment.map((d, i) => (
              <div key={d.month} className="h-full flex-1 flex flex-col items-center gap-2 group">
                <span className="text-xs font-semibold text-indigo-800 opacity-0 group-hover:opacity-100 transition-opacity">
                  {d.count}
                </span>
                <div className="w-full relative flex flex-1 min-h-0 items-end">
                  <div
                    className="w-full bg-indigo-800/80 group-hover:bg-indigo-800 transition-all duration-500 relative rounded-t-lg"
                    style={{
                      height: `${(d.count / maxEnrollment) * 100}%`,
                      animationDelay: `${i * 80}ms`,
                    }}
                  >
                    <div className="absolute -top-1 left-0 right-0 h-1 bg-amber-400 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                </div>
                <span className="text-xs text-ink-400">{d.month}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Revenue chart */}
        <div className="card-editorial p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="font-display text-lg font-semibold text-ink-900 flex items-center gap-2">
                <CircleDollarSign className="w-5 h-5 text-green-600" />
                收入趋势
              </h2>
              <p className="text-xs text-ink-400 mt-1">近 6 个月课程收入（元）</p>
            </div>
            <span className="badge-green">月度</span>
          </div>
          <div className="flex items-end gap-3 h-56">
            {revenue.map((d, i) => (
              <div key={d.month} className="h-full flex-1 flex flex-col items-center gap-2 group">
                <span className="text-xs font-semibold text-green-700 opacity-0 group-hover:opacity-100 transition-opacity">
                  ¥{d.amount.toLocaleString()}
                </span>
                <div className="w-full relative flex flex-1 min-h-0 items-end">
                  <div
                    className="w-full bg-gradient-to-t from-green-600 to-green-400 group-hover:from-green-700 group-hover:to-green-500 transition-all duration-500 rounded-t-lg"
                    style={{
                      height: `${(d.amount / maxRevenue) * 100}%`,
                      animationDelay: `${i * 80}ms`,
                    }}
                  />
                </div>
                <span className="text-xs text-ink-400">{d.month}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Engagement horizontal bars */}
        <div className="card-editorial p-6 lg:col-span-2">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="font-display text-lg font-semibold text-ink-900 flex items-center gap-2">
                <BarChart3 className="w-5 h-5 text-indigo-600" />
                学员参与度
              </h2>
              <p className="text-xs text-ink-400 mt-1">各项学习行为的参与比例</p>
            </div>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-5">
            {engagement.map((d, i) => (
              <div key={d.label} className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-ink-700 font-medium">{d.label}</span>
                  <span className="font-display font-bold text-ink-900">{d.value}%</span>
                </div>
                <div className="h-3 bg-ink-100 overflow-hidden rounded-full">
                  <div
                    className={cn(
                      'h-full transition-all duration-700 rounded-full',
                      i % 3 === 0 ? 'bg-indigo-800' : i % 3 === 1 ? 'bg-amber-500' : 'bg-green-600'
                    )}
                    style={{
                      width: `${(d.value / maxEngagement) * 100}%`,
                      animationDelay: `${i * 100}ms`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Course performance table */}
      <div className="card-editorial p-6">
        <h2 className="font-display text-lg font-semibold text-ink-900 mb-4">课程表现排行</h2>
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>排名</th>
                <th>课程名称</th>
                <th>学员数</th>
                <th>完课率</th>
                <th>收入贡献</th>
              </tr>
            </thead>
            <tbody>
              {[
                { rank: 1, title: 'Spring Boot 3 实战：从入门到微服务架构', students: 1284, completion: 78, revenue: 128400 },
                { rank: 2, title: 'Python 数据分析与可视化实战', students: 892, completion: 82, revenue: 89200 },
                { rank: 3, title: 'React 18 + TypeScript 现代前端工程化', students: 656, completion: 65, revenue: 65600 },
                { rank: 4, title: '机器学习入门：Scikit-Learn 实战', students: 445, completion: 58, revenue: 44500 },
                { rank: 5, title: 'Flutter 跨平台移动应用开发', students: 312, completion: 71, revenue: 31200 },
              ].map((row) => (
                <tr key={row.rank}>
                  <td>
                    <span className={cn(
                      'font-display text-xl font-bold w-8 h-8 flex items-center justify-center rounded-full',
                      row.rank <= 3 ? 'text-amber-600' : 'text-ink-300'
                    )}>
                      {row.rank}
                    </span>
                  </td>
                  <td className="font-medium text-ink-800">{row.title}</td>
                  <td className="text-ink-700">{row.students.toLocaleString()} 人</td>
                  <td>
                    <div className="flex items-center gap-2 min-w-[120px]">
                      <div className="progress-track flex-1">
                        <div
                          className={cn(
                            'progress-fill',
                            row.completion >= 75 ? 'bg-green-500' : row.completion >= 60 ? 'bg-amber-500' : 'bg-red-500'
                          )}
                          style={{ width: `${row.completion}%` }}
                        />
                      </div>
                      <span className="text-sm text-ink-600 w-10 text-right">{row.completion}%</span>
                    </div>
                  </td>
                  <td className="font-medium text-ink-800">¥{row.revenue.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
