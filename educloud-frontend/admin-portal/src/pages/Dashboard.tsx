import { useEffect, useState } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import {
  Users,
  BookOpen,
  CircleDollarSign,
  Wifi,
  UserPlus,
  ShoppingBag,
  ClipboardCheck,
  Settings,
  Activity,
} from 'lucide-react';
import StatsCard from '../components/StatsCard';
import { dashboardApi } from '../services/api';
import { useChartColors } from '../hooks/useChartColors';
import type {
  DashboardStats,
  UserGrowthPoint,
  CategoryStat,
  OrderStatusStat,
  ActivityItem,
} from '../types';

const activityIcon = {
  user: UserPlus,
  course: ClipboardCheck,
  order: ShoppingBag,
  system: Settings,
};

const activityColor = {
  user: 'bg-brand-500/15 text-brand-500 dark:text-brand-400',
  course: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  order: 'bg-green-500/15 text-green-600 dark:text-green-400',
  system: 'bg-ink-300/30 text-ink-500',
};

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [growth, setGrowth] = useState<UserGrowthPoint[]>([]);
  const [categories, setCategories] = useState<CategoryStat[]>([]);
  const [orderStats, setOrderStats] = useState<OrderStatusStat[]>([]);
  const [activities, setActivities] = useState<ActivityItem[]>([]);
  const chartColors = useChartColors();

  useEffect(() => {
    void Promise.all([
      dashboardApi.getStats(),
      dashboardApi.getUserGrowth(),
      dashboardApi.getCategoryStats(),
      dashboardApi.getOrderStatusStats(),
      dashboardApi.getActivities(),
    ]).then(([s, g, c, o, a]) => {
      setStats(s);
      setGrowth(g);
      setCategories(c);
      setOrderStats(o);
      setActivities(a);
    });
  }, []);

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="animate-fade-up opacity-0">
        <div className="section-label mb-2">概览</div>
        <h1 className="display-heading text-3xl md:text-4xl">数据看板</h1>
        <p className="text-ink-500 mt-2">平台运营核心指标与实时动态</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatsCard
          label="总用户数"
          value={stats?.totalUsers ?? 0}
          trend={stats?.userGrowth}
          trendLabel="较上周"
          icon={Users}
          delay="animation-delay-100"
        />
        <StatsCard
          label="总课程数"
          value={stats?.totalCourses ?? 0}
          trend={stats?.courseGrowth}
          trendLabel="较上周"
          icon={BookOpen}
          delay="animation-delay-200"
        />
        <StatsCard
          label="总收入"
          value={stats ? (stats.totalRevenue / 10000).toFixed(1) : 0}
          prefix="¥"
          suffix="万"
          trend={stats?.revenueGrowth}
          trendLabel="较上周"
          icon={CircleDollarSign}
          delay="animation-delay-300"
        />
        <StatsCard
          label="在线用户"
          value={stats?.onlineUsers ?? 0}
          trend={stats?.onlineGrowth}
          trendLabel="较昨日"
          icon={Wifi}
          delay="animation-delay-500"
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* User growth line chart */}
        <div className="lg:col-span-2 card-editorial p-6 animate-fade-up opacity-0 animation-delay-100">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="font-display text-xl font-bold text-ink-900">用户增长趋势</h2>
              <p className="text-sm text-ink-500">近 7 天累计用户与新增用户</p>
            </div>
            <span className="badge-indigo">7 天</span>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={growth} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} vertical={false} />
              <XAxis
                dataKey="date"
                tick={{ fontSize: 12, fill: chartColors.text }}
                axisLine={{ stroke: chartColors.axis }}
                tickLine={false}
              />
              <YAxis
                tick={{ fontSize: 12, fill: chartColors.text }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                contentStyle={{
                  background: chartColors.tooltipBg,
                  border: `1px solid ${chartColors.tooltipBorder}`,
                  borderRadius: '12px',
                  fontSize: 13,
                  color: chartColors.tooltipText,
                }}
                labelStyle={{ color: chartColors.tooltipLabel, fontWeight: 600 }}
              />
              <Line
                type="monotone"
                dataKey="users"
                name="累计用户"
                stroke={chartColors.primary}
                strokeWidth={2.5}
                dot={{ fill: chartColors.primary, r: 4 }}
                activeDot={{ r: 6 }}
              />
              <Line
                type="monotone"
                dataKey="newUsers"
                name="新增用户"
                stroke={chartColors.secondary}
                strokeWidth={2}
                strokeDasharray="5 3"
                dot={{ fill: chartColors.secondary, r: 3 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Order status pie */}
        <div className="card-editorial p-6 animate-fade-up opacity-0 animation-delay-200">
          <div className="mb-6">
            <h2 className="font-display text-xl font-bold text-ink-900">订单状态分布</h2>
            <p className="text-sm text-ink-500">全部订单状态占比</p>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <PieChart>
              <Pie
                data={orderStats}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                innerRadius={50}
                outerRadius={80}
                paddingAngle={2}
              >
                {orderStats.map((_, index) => (
                  <Cell key={index} fill={chartColors.pie[index % chartColors.pie.length]} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  background: chartColors.tooltipBg,
                  border: `1px solid ${chartColors.tooltipBorder}`,
                  borderRadius: '12px',
                  fontSize: 13,
                  color: chartColors.tooltipText,
                }}
              />
            </PieChart>
          </ResponsiveContainer>
          <div className="grid grid-cols-2 gap-2 mt-4">
            {orderStats.map((item, i) => (
              <div key={item.name} className="flex items-center gap-2 text-sm">
                <span
                  className="w-3 h-3 shrink-0"
                  style={{ backgroundColor: chartColors.pie[i] }}
                />
                <span className="text-ink-600">{item.name}</span>
                <span className="ml-auto font-medium text-ink-900">{item.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Bottom row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Category bar chart */}
        <div className="lg:col-span-2 card-editorial p-6 animate-fade-up opacity-0 animation-delay-300">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="font-display text-xl font-bold text-ink-900">课程分类分布</h2>
              <p className="text-sm text-ink-500">各分类课程数量统计</p>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={categories} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} vertical={false} />
              <XAxis
                dataKey="name"
                tick={{ fontSize: 12, fill: chartColors.text }}
                axisLine={{ stroke: chartColors.axis }}
                tickLine={false}
              />
              <YAxis
                tick={{ fontSize: 12, fill: chartColors.text }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                contentStyle={{
                  background: chartColors.tooltipBg,
                  border: `1px solid ${chartColors.tooltipBorder}`,
                  borderRadius: '12px',
                  fontSize: 13,
                  color: chartColors.tooltipText,
                }}
                cursor={{ fill: chartColors.cursor }}
              />
              <Bar dataKey="count" name="课程数" fill={chartColors.primary} radius={[4, 4, 0, 0]} barSize={36} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Activity feed */}
        <div className="card-editorial p-6 animate-fade-up opacity-0 animation-delay-500">
          <div className="flex items-center gap-2 mb-6">
            <Activity size={18} className="text-amber-500" />
            <h2 className="font-display text-xl font-bold text-ink-900">最近动态</h2>
          </div>
          <div className="space-y-4">
            {activities.map((act) => {
              const Icon = activityIcon[act.type];
              return (
                <div key={act.id} className="flex gap-3">
                  <span
                    className={`flex items-center justify-center w-8 h-8 shrink-0 rounded-xl ${activityColor[act.type]}`}
                  >
                    <Icon size={14} />
                  </span>
                  <div className="flex-1 min-w-0 pb-4 border-b border-ink-50 last:border-0">
                    <p className="text-sm text-ink-700 leading-relaxed">
                      <span className="font-medium text-ink-900">{act.user}</span>{' '}
                      {act.action}
                      <span className="text-brand-500 dark:text-brand-400"> {act.target}</span>
                    </p>
                    <span className="text-xs text-ink-400 mt-1 block">{act.time}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
