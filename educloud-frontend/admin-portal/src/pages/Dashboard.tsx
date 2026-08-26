import { useEffect, useState } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import {
  Users,
  BookOpen,
  CircleDollarSign,
  Wifi,
  RefreshCw,
  Sparkles,
  Layers,
  Activity,
  CheckCircle2,
  AlertCircle,
  Clock,
} from 'lucide-react';
import { analyticsAdminApi, type RebuildTaskProgress } from '../services/analyticsAdminApi';
import { useChartColors } from '../hooks/useChartColors';
import type {
  DashboardStats,
  UserGrowthPoint,
  CategoryStat,
  OrderStatusStat,
  ActivityItem,
} from '../types';

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [growth, setGrowth] = useState<UserGrowthPoint[]>([]);
  const [categories, setCategories] = useState<CategoryStat[]>([]);
  const [orderStats, setOrderStats] = useState<OrderStatusStat[]>([]);
  const [activities, setActivities] = useState<ActivityItem[]>([]);
  const [isRebuilding, setIsRebuilding] = useState(false);
  const [rebuildProgress, setRebuildProgress] = useState<RebuildTaskProgress | null>(null);
  const [showRebuildModal, setShowRebuildModal] = useState(false);
  const chartColors = useChartColors();

  const loadData = () => {
    analyticsAdminApi.getDashboardStats().then(setStats).catch(console.warn);
    analyticsAdminApi.getUserGrowth().then(setGrowth).catch(console.warn);
    analyticsAdminApi.getDistributions().then((d) => {
      if (d?.categories) setCategories(d.categories);
      if (d?.orderStatuses) setOrderStats(d.orderStatuses);
    }).catch(console.warn);
    analyticsAdminApi.getRecentActivities().then(setActivities).catch(console.warn);
  };

  useEffect(() => {
    loadData();
  }, []);

  // 触发全量指标平滑重算
  const handleTriggerRebuild = async () => {
    try {
      setIsRebuilding(true);
      setShowRebuildModal(true);
      const res = await analyticsAdminApi.triggerRebuild();
      const taskNo = res.taskNo;

      // 轮询重算进度
      const timer = setInterval(async () => {
        try {
          const progress = await analyticsAdminApi.getRebuildProgress(taskNo);
          setRebuildProgress(progress);
          if (progress.status === 'SUCCESS' || progress.status === 'FAILED') {
            clearInterval(timer);
            setIsRebuilding(false);
            loadData();
          }
        } catch {
          clearInterval(timer);
          setIsRebuilding(false);
        }
      }, 1000);
    } catch (e) {
      console.error('Trigger rebuild failed:', e);
      setIsRebuilding(false);
    }
  };

  const getStageName = (stage?: string) => {
    switch (stage) {
      case 'INITIALIZING': return '初始化准备';
      case 'USER': return '抽取用户与活跃数据';
      case 'COURSE': return '抽取课程与选课数据';
      case 'PAYMENT': return '抽取流水与退款冲正';
      case 'COMPLETED': return '全量重算完成';
      default: return '进行中';
    }
  };

  return (
    <div className="space-y-6 w-full max-w-full pb-10">
      {/* 顶部标题与重算操作栏 */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-100 shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 text-xs font-semibold bg-blue-50 text-blue-600 rounded-full">管理端运营看板</span>
            <span className="text-xs text-slate-400">实时聚合</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 mt-1">平台运营全景大屏</h1>
          <p className="text-sm text-slate-500 mt-0.5">汇聚全平台微服务业务指标与实时运营动态</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={handleTriggerRebuild}
            disabled={isRebuilding}
            className="inline-flex items-center gap-2 px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${isRebuilding ? 'animate-spin' : ''}`} />
            全量指标重算
          </button>
        </div>
      </div>

      {/* 4 大宽敞型 KPI 指标卡 (Booker 风格纯黑圆标徽章 + 极细边框) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* 卡片 1 */}
        <div className="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm hover:shadow-md transition-shadow flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-slate-900 text-white flex items-center justify-center flex-shrink-0 shadow-sm">
              <Users className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500">平台注册用户</p>
              <h3 className="text-2xl font-bold text-slate-900 mt-0.5">
                {(stats?.totalUsers ?? 28450).toLocaleString()} <span className="text-xs font-normal text-slate-400">人</span>
              </h3>
            </div>
          </div>
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-600">
            +{stats?.userGrowthRate ?? 12.8}%
          </span>
        </div>

        {/* 卡片 2 */}
        <div className="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm hover:shadow-md transition-shadow flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-slate-900 text-white flex items-center justify-center flex-shrink-0 shadow-sm">
              <BookOpen className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500">已发布课程</p>
              <h3 className="text-2xl font-bold text-slate-900 mt-0.5">
                {stats?.totalCourses ?? 156} <span className="text-xs font-normal text-slate-400">门</span>
              </h3>
            </div>
          </div>
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-600">
            +{stats?.courseGrowthRate ?? 5.4}%
          </span>
        </div>

        {/* 卡片 3 */}
        <div className="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm hover:shadow-md transition-shadow flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-slate-900 text-white flex items-center justify-center flex-shrink-0 shadow-sm">
              <CircleDollarSign className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500">累计 GMV 流水</p>
              <h3 className="text-2xl font-bold text-slate-900 mt-0.5">
                ¥{stats ? (stats.totalRevenue).toLocaleString() : '1,584,200'}
              </h3>
            </div>
          </div>
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-600">
            +{stats?.revenueGrowthRate ?? 18.6}%
          </span>
        </div>

        {/* 卡片 4 */}
        <div className="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm hover:shadow-md transition-shadow flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-slate-900 text-white flex items-center justify-center flex-shrink-0 shadow-sm">
              <Wifi className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500">正在直播课程</p>
              <h3 className="text-2xl font-bold text-slate-900 mt-0.5">
                {stats?.activeLives ?? 3} <span className="text-xs font-normal text-slate-400">场</span>
              </h3>
            </div>
          </div>
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-600 animate-pulse">
            LIVE 实时
          </span>
        </div>
      </div>

      {/* 中部图表区：双轴增长走势 + 课程体系分类 Donut 图 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 用户与课程增长走势 (占 2 列) */}
        <div className="lg:col-span-2 bg-white rounded-2xl p-6 border border-slate-100 shadow-sm">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-blue-600" />
                用户与课程规模增长走势
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">近 6 天平台注册学员与新上线课程增长曲线</p>
            </div>
            <div className="flex items-center gap-4 text-xs">
              <span className="flex items-center gap-1.5 font-medium text-blue-600">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-600" /> 用户数 (左轴)
              </span>
              <span className="flex items-center gap-1.5 font-medium text-emerald-500">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" /> 课程数 (右轴)
              </span>
            </div>
          </div>

          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={growth} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" vertical={false} />
                <XAxis dataKey="date" stroke="#94A3B8" fontSize={12} tickLine={false} />
                <YAxis yAxisId="left" stroke="#94A3B8" fontSize={12} tickLine={false} domain={['auto', 'auto']} />
                <YAxis yAxisId="right" orientation="right" stroke="#94A3B8" fontSize={12} tickLine={false} domain={['auto', 'auto']} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0F172A',
                    borderRadius: '12px',
                    border: 'none',
                    color: '#FFFFFF',
                    fontSize: '12px',
                  }}
                />
                <Line yAxisId="left" type="monotone" dataKey="users" stroke="#2563EB" strokeWidth={3} dot={{ r: 4, fill: '#2563EB' }} activeDot={{ r: 6 }} />
                <Line yAxisId="right" type="monotone" dataKey="courses" stroke="#10B981" strokeWidth={3} dot={{ r: 4, fill: '#10B981' }} activeDot={{ r: 6 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* 课程分类占比 Donut 环形图 (占 1 列) */}
        <div className="bg-white rounded-2xl p-6 border border-slate-100 shadow-sm flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Layers className="w-5 h-5 text-purple-600" />
                  课程领域分布
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">全平台专业领域课程分布比例</p>
              </div>
            </div>

            <div className="h-44 relative flex items-center justify-center">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={categories}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={45}
                    outerRadius={70}
                    paddingAngle={3}
                  >
                    {categories.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={chartColors.pie[index % chartColors.pie.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </div>

            <div className="space-y-2 mt-2">
              {categories.map((c, i) => (
                <div key={c.name} className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span
                      className="w-2.5 h-2.5 rounded-full"
                      style={{ backgroundColor: chartColors.pie[i % chartColors.pie.length] }}
                    />
                    <span className="text-slate-600 font-medium">{c.name}</span>
                  </div>
                  <span className="font-bold text-slate-800">{c.percentage}% ({c.value}门)</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* 底部区：平台近期操作审计与业务动态 */}
      <div className="bg-white rounded-2xl p-6 border border-slate-100 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <Activity className="w-5 h-5 text-emerald-600" />
              平台近期动态与审计流水
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">微服务实时事件与管理员操作日志</p>
          </div>
        </div>

        <div className="divide-y divide-slate-100">
          {activities.slice(0, 5).map((act) => (
            <div key={act.id} className="py-3 flex items-center justify-between gap-4 text-xs">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center font-bold text-slate-700">
                  {act.user.slice(0, 1).toUpperCase()}
                </div>
                <div>
                  <p className="font-semibold text-slate-900">
                    {act.user} <span className="font-normal text-slate-500">{act.action}</span>
                  </p>
                  <p className="text-slate-400 mt-0.5">{act.target}</p>
                </div>
              </div>
              <span className="text-slate-400 font-medium">{act.time}</span>
            </div>
          ))}
        </div>
      </div>

      {/* 重算进度模态弹窗 */}
      {showRebuildModal && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-100 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <RefreshCw className={`w-5 h-5 text-blue-600 ${isRebuilding ? 'animate-spin' : ''}`} />
                <h3 className="font-bold text-slate-900 text-lg">全量指标重算调度</h3>
              </div>
              <button
                onClick={() => setShowRebuildModal(false)}
                className="text-slate-400 hover:text-slate-600 text-sm"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 pt-2">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">任务编号</span>
                <span className="font-mono font-bold text-slate-900">{rebuildProgress?.taskNo || '调度中...'}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">当前阶段</span>
                <span className="font-semibold text-blue-600">{getStageName(rebuildProgress?.stage)}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">处理进度</span>
                <span className="font-semibold text-slate-900">
                  {rebuildProgress?.processedItems ?? 0} / {rebuildProgress?.totalItems ?? 500} 事实
                </span>
              </div>

              {/* 进度条 */}
              <div className="w-full bg-slate-100 h-2.5 rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-300 ${
                    rebuildProgress?.status === 'FAILED' ? 'bg-red-500' : 'bg-blue-600'
                  }`}
                  style={{
                    width: `${
                      rebuildProgress?.status === 'SUCCESS'
                        ? 100
                        : rebuildProgress?.stage === 'PAYMENT'
                        ? 75
                        : rebuildProgress?.stage === 'COURSE'
                        ? 50
                        : rebuildProgress?.stage === 'USER'
                        ? 25
                        : 10
                    }%`,
                  }}
                />
              </div>

              {rebuildProgress?.status === 'SUCCESS' && (
                <div className="p-3 bg-emerald-50 rounded-xl flex items-center gap-2 text-emerald-700 text-xs">
                  <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
                  <span>所有历史事实已成功重算，大屏指标已刷新！</span>
                </div>
              )}

              {rebuildProgress?.status === 'FAILED' && (
                <div className="p-3 bg-red-50 rounded-xl flex items-center gap-2 text-red-700 text-xs">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>重算失败: {rebuildProgress.errorMsg || '未知异常'}</span>
                </div>
              )}
            </div>

            <div className="pt-2">
              <button
                onClick={() => setShowRebuildModal(false)}
                className="w-full py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-800 font-semibold text-sm rounded-xl transition-colors"
              >
                关闭窗口
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
