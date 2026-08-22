import { useEffect, useMemo, useState } from 'react';
import { Search, Mail, BookOpen, Clock, Filter } from 'lucide-react';
import { api } from '../services/api';
import type { Student } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

export default function StudentList() {
  const [students, setStudents] = useState<Student[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [progressFilter, setProgressFilter] = useState<'ALL' | 'LOW' | 'MID' | 'HIGH'>('ALL');

  useEffect(() => {
    api.getStudents().then((data) => {
      setStudents(data);
      setLoading(false);
    });
  }, []);

  const filtered = useMemo(() => {
    return students.filter((s) => {
      const matchSearch =
        s.name.toLowerCase().includes(search.toLowerCase()) ||
        s.email.toLowerCase().includes(search.toLowerCase());
      let matchProgress = true;
      if (progressFilter === 'LOW') matchProgress = s.progress < 40;
      else if (progressFilter === 'MID') matchProgress = s.progress >= 40 && s.progress < 75;
      else if (progressFilter === 'HIGH') matchProgress = s.progress >= 75;
      return matchSearch && matchProgress;
    });
  }, [students, search, progressFilter]);

  const progressColor = (p: number) => {
    if (p >= 75) return 'bg-green-500';
    if (p >= 40) return 'bg-amber-500';
    return 'bg-red-500';
  };

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div>
        <p className="section-label mb-2">学生管理</p>
        <h1 className="display-heading text-3xl md:text-4xl">学员名录</h1>
        <p className="text-ink-500 mt-2 text-sm">共 {students.length} 名学员，查看学习进度与活跃度</p>
      </div>

      {/* Filters */}
      <div className="flex flex-col md:flex-row gap-4 items-start md:items-center">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-300" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索学员姓名或邮箱……"
            className="input-field pl-11"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter className="w-4 h-4 text-ink-400" />
          {([
            { key: 'ALL', label: '全部' },
            { key: 'HIGH', label: '高进度' },
            { key: 'MID', label: '中进度' },
            { key: 'LOW', label: '低进度' },
          ] as const).map((f) => (
            <button
              key={f.key}
              onClick={() => setProgressFilter(f.key)}
              className={cn(
                'px-3 py-1.5 text-xs font-medium border transition-all rounded-lg',
                progressFilter === f.key
                  ? 'border-indigo-800 bg-indigo-800 text-white'
                  : 'border-ink-200 text-ink-600 hover:border-ink-400'
              )}
            >
              {f.label}
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
                <th>学员</th>
                <th>邮箱</th>
                <th>报名课程</th>
                <th>学习进度</th>
                <th>最近活跃</th>
                <th>加入时间</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-ink-400">加载中…</td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 text-ink-400">未找到匹配的学员</td>
                </tr>
              ) : (
                filtered.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <div className="flex items-center gap-3">
                        <img
                          src={s.avatar}
                          alt={s.name}
                          className="w-10 h-10 rounded-full bg-ink-100"
                        />
                        <span className="font-medium text-ink-800">{s.name}</span>
                      </div>
                    </td>
                    <td>
                      <span className="flex items-center gap-1.5 text-ink-600">
                        <Mail className="w-3.5 h-3.5 text-ink-400" />
                        {s.email}
                      </span>
                    </td>
                    <td>
                      <span className="flex items-center gap-1.5 text-ink-700">
                        <BookOpen className="w-3.5 h-3.5 text-ink-400" />
                        {s.enrolledCourses} 门
                      </span>
                    </td>
                    <td>
                      <div className="flex items-center gap-3 min-w-[160px]">
                        <div className="progress-track flex-1">
                          <div
                            className={cn('progress-fill', progressColor(s.progress))}
                            style={{ width: `${s.progress}%` }}
                          />
                        </div>
                        <span className="text-sm font-medium text-ink-700 w-10 text-right">
                          {s.progress}%
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className="flex items-center gap-1.5 text-ink-500 text-sm">
                        <Clock className="w-3.5 h-3.5 text-ink-400" />
                        {dayjs(s.lastActive).fromNow()}
                      </span>
                    </td>
                    <td className="text-ink-500 text-sm">
                      {dayjs(s.joinDate).format('YYYY-MM-DD')}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
