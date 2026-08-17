import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BookOpen,
  Users,
  CircleDollarSign,
  ClipboardList,
  TrendingUp,
  ArrowRight,
  Radio,
  Calendar,
  UserPlus,
  MessageSquare,
  Settings,
} from 'lucide-react';
import { api } from '../services/api';
import type { AnalyticsStats, Activity, LiveRoom } from '../types';
import dayjs from 'dayjs';

const activityIcons: Record<Activity['type'], typeof BookOpen> = {
  enrollment: UserPlus,
  submission: ClipboardList,
  live: Radio,
  comment: MessageSquare,
  system: Settings,
};

export default function Dashboard() {
  const [stats, setStats] = useState<AnalyticsStats | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [upcomingLives, setUpcomingLives] = useState<LiveRoom[]>([]);

  useEffect(() => {
    api.getStats().then(setStats);
    api.getActivities().then(setActivities);
    api.getLiveRooms().then((rooms) =>
      setUpcomingLives(rooms.filter((r) => r.status === 'CREATED' || r.status === 'LIVING'))
    );
  }, []);

  const statCards = [
    { label: '课程总数', value: stats?.totalCourses ?? 0, icon: BookOpen, suffix: '门', accent: 'text-indigo-600' },
    { label: '学员总数', value: stats?.totalStudents.toLocaleString() ?? 0, icon: Users, suffix: '人', accent: 'text-amber-600' },
    { label: '本月收入', value: stats ? `¥${stats.monthlyRevenue.toLocaleString()}` : '¥0', icon: CircleDollarSign, suffix: '', accent: 'text-green-600' },
    { label: '待批改作业', value: stats?.pendingGrading ?? 0, icon: ClipboardList, suffix: '份', accent: 'text-red-600' },
  ];

  return (
    <div className="space-y-8 animate-fade-up">
      {/* Welcome */}
      <section className="relative overflow-hidden bg-indigo-800 text-paper p-8 md:p-12">
        <div className="absolute top-0 right-0 w-96 h-96 bg-amber-500/10 rounded-full -translate-y-1/2 translate-x-1/2" />
        <div className="relative">
          <p className="section-label !text-amber-400/80 mb-3">
            <span className="text-amber-400">{dayjs().format('YYYY年MM月DD日')}</span>
          </p>
          <h1 className="display-heading !text-paper text-4xl md:text-5xl mb-3">
            欢迎回来，张明教授
          </h1>
          <p className="text-indigo-200 max-w-xl leading-relaxed">
            今天有 <span className="text-amber-400 font-semibold">{upcomingLives.length}</span> 场直播待进行，
            <span className="text-amber-400 font-semibold">{stats?.pendingGrading ?? 0}</span> 份作业等待批改。
            祝您教学愉快。
          </p>
        </div>
      </section>

      {/* Stat cards */}
      <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((card, i) => (
          <div
            key={card.label}
            className={`stat-card animate-fade-up animation-delay-${(i + 1) * 100}`}
          >
            <div className="flex items-start justify-between mb-4">
              <div className={`w-10 h-10 flex items-center justify-center bg-ink-50 ${card.accent}`}>
                <card.icon className="w-5 h-5" strokeWidth={1.5} />
              </div>
              <TrendingUp className="w-4 h-4 text-green-500" />
            </div>
            <p className="font-display text-3xl font-bold text-ink-900">
              {card.value}
              <span className="text-base font-normal text-ink-400 ml-1">{card.suffix}</span>
            </p>
            <p className="text-sm text-ink-500 mt-1">{card.label}</p>
          </div>
        ))}
      </section>

      {/* Activity + Upcoming lives */}
      <section className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent activity */}
        <div className="lg:col-span-2 card-editorial p-6">
          <div className="flex items-center justify-between mb-5">
            <h2 className="font-display text-xl font-semibold text-ink-900">最近动态</h2>
            <Link to="/analytics" className="link-underline text-sm flex items-center gap-1">
              查看全部 <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="space-y-1">
            {activities.map((act) => {
              const Icon = activityIcons[act.type];
              return (
                <div
                  key={act.id}
                  className="flex items-start gap-4 py-3 border-b border-ink-50 last:border-b-0"
                >
                  <div className="w-8 h-8 flex-shrink-0 flex items-center justify-center bg-indigo-50 text-indigo-600">
                    <Icon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-ink-700">{act.content}</p>
                    <p className="text-xs text-ink-400 mt-0.5">
                      {dayjs(act.time).format('MM-DD HH:mm')}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Upcoming lives */}
        <div className="card-editorial p-6">
          <div className="flex items-center justify-between mb-5">
            <h2 className="font-display text-xl font-semibold text-ink-900">即将直播</h2>
            <Link to="/live" className="link-underline text-sm flex items-center gap-1">
              管理 <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          {upcomingLives.length === 0 ? (
            <p className="text-sm text-ink-400 text-center py-8">暂无安排中的直播</p>
          ) : (
            <div className="space-y-4">
              {upcomingLives.slice(0, 4).map((room) => (
                <div key={room.id} className="flex gap-3 group">
                  <div className="relative w-20 h-14 flex-shrink-0 overflow-hidden bg-ink-100">
                    <img
                      src={room.thumbnail}
                      alt={room.title}
                      className="w-full h-full object-cover"
                    />
                    {room.status === 'LIVING' && (
                      <div className="absolute inset-0 bg-red-600/60 flex items-center justify-center">
                        <Radio className="w-4 h-4 text-white animate-pulse" />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-ink-800 line-clamp-2 group-hover:text-indigo-800 transition-colors">
                      {room.title}
                    </p>
                    <p className="text-xs text-ink-400 flex items-center gap-1 mt-1">
                      <Calendar className="w-3 h-3" />
                      {dayjs(room.startTime).format('MM-DD HH:mm')}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
