import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Plus, Calendar, Radio, Clock, CheckCircle2 } from 'lucide-react';
import { useLiveStore } from '../stores/useLiveStore';
import { useCourseStore } from '../stores/useCourseStore';
import LivePreview from '../components/LivePreview';
import type { LiveStatus } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

type Filter = 'ALL' | LiveStatus;

export default function LiveManage() {
  const { liveRooms, loading, fetchLiveRooms, startLive, endLive, createLiveRoom } = useLiveStore();
  const { courses, fetchCourses } = useCourseStore();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [showCreate, setShowCreate] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newCourseId, setNewCourseId] = useState('');
  const [newStartTime, setNewStartTime] = useState('');
  const [newDesc, setNewDesc] = useState('');

  useEffect(() => {
    fetchLiveRooms();
    fetchCourses();
  }, [fetchLiveRooms, fetchCourses]);

  const filtered = filter === 'ALL' ? liveRooms : liveRooms.filter((r) => r.status === filter);

  const living = liveRooms.filter((r) => r.status === 'LIVING');
  const scheduled = liveRooms.filter((r) => r.status === 'CREATED');
  const ended = liveRooms.filter((r) => r.status === 'ENDED');

  const handleCreate = async () => {
    if (!newTitle.trim() || !newCourseId) return;
    const course = courses.find((c) => c.id === newCourseId);
    await createLiveRoom({
      title: newTitle.trim(),
      courseId: newCourseId,
      courseName: course?.title ?? '',
      startTime: newStartTime || dayjs().add(1, 'day').hour(20).minute(0).second(0).toISOString(),
      description: newDesc,
    });
    setShowCreate(false);
    setNewTitle('');
    setNewCourseId('');
    setNewStartTime('');
    setNewDesc('');
  };

  useEffect(() => {
    if (!showCreate) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showCreate]);

  const filterTabs: { key: Filter; label: string; count: number }[] = [
    { key: 'ALL', label: '全部', count: liveRooms.length },
    { key: 'LIVING', label: '直播中', count: living.length },
    { key: 'CREATED', label: '未开始', count: scheduled.length },
    { key: 'ENDED', label: '已结束', count: ended.length },
  ];

  const createModal = showCreate
    ? createPortal(
        <div className="fixed inset-0 z-[100] overflow-hidden bg-indigo-950/25 backdrop-blur-xl">
          <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
            <div className="pointer-events-none absolute inset-0 overflow-hidden">
              <div className="absolute -left-20 top-1/4 h-72 w-72 rounded-full bg-amber-300/25 blur-3xl" />
              <div className="absolute -right-16 bottom-1/4 h-80 w-80 rounded-full bg-indigo-300/25 blur-3xl" />
            </div>
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="create-live-title"
              className="relative z-10 my-auto max-h-[calc(100dvh-2rem)] w-full max-w-lg overflow-y-auto overscroll-contain rounded-2xl border border-white/70 bg-white/75 p-5 shadow-2xl shadow-indigo-950/20 backdrop-blur-2xl sm:max-h-[calc(100dvh-3rem)] sm:p-6"
            >
              <h2 id="create-live-title" className="font-display text-xl font-semibold text-ink-900">创建直播</h2>
              <div className="mt-4 space-y-4">
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">直播标题</label>
                  <input
                    type="text"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    className="input-field"
                    placeholder="例如：Spring Boot 微服务架构直播答疑"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">关联课程</label>
                  <select
                    value={newCourseId}
                    onChange={(e) => setNewCourseId(e.target.value)}
                    className="input-field cursor-pointer appearance-none"
                  >
                    <option value="">请选择课程</option>
                    {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
                  </select>
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">开播时间</label>
                  <input
                    type="datetime-local"
                    value={newStartTime}
                    onChange={(e) => setNewStartTime(e.target.value)}
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-ink-700">直播简介</label>
                  <textarea
                    value={newDesc}
                    onChange={(e) => setNewDesc(e.target.value)}
                    rows={3}
                    className="input-field resize-none"
                    placeholder="简要介绍本次直播内容……"
                  />
                </div>
                <div className="flex gap-3 pt-1">
                  <button onClick={handleCreate} className="btn-primary flex-1">
                    <Radio className="h-4 w-4" />
                    创建直播
                  </button>
                  <button onClick={() => setShowCreate(false)} className="btn-outline flex-1">取消</button>
                </div>
              </div>
            </div>
          </div>
        </div>,
        document.body,
      )
    : null;

  return (
    <>
      <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <p className="section-label mb-2">直播管理</p>
          <h1 className="display-heading text-3xl md:text-4xl">直播课堂</h1>
          <p className="text-ink-500 mt-2 text-sm">管理直播日程、实时授课与回放</p>
        </div>
        <button onClick={() => setShowCreate(true)} className="btn-primary">
          <Plus className="w-4 h-4" />
          创建直播
        </button>
      </div>

      {/* Quick stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="stat-card flex items-center gap-4">
          <div className="w-12 h-12 bg-red-50 flex items-center justify-center">
            <Radio className="w-6 h-6 text-red-600" />
          </div>
          <div>
            <p className="font-display text-2xl font-bold text-ink-900">{living.length}</p>
            <p className="text-sm text-ink-500">正在直播</p>
          </div>
        </div>
        <div className="stat-card flex items-center gap-4">
          <div className="w-12 h-12 bg-indigo-50 flex items-center justify-center">
            <Calendar className="w-6 h-6 text-indigo-600" />
          </div>
          <div>
            <p className="font-display text-2xl font-bold text-ink-900">{scheduled.length}</p>
            <p className="text-sm text-ink-500">待开播</p>
          </div>
        </div>
        <div className="stat-card flex items-center gap-4">
          <div className="w-12 h-12 bg-amber-50 flex items-center justify-center">
            <CheckCircle2 className="w-6 h-6 text-amber-600" />
          </div>
          <div>
            <p className="font-display text-2xl font-bold text-ink-900">{ended.length}</p>
            <p className="text-sm text-ink-500">已结束</p>
          </div>
        </div>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-2 border-b border-ink-200">
        {filterTabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setFilter(tab.key)}
            className={cn(
              'flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-all -mb-px',
              filter === tab.key
                ? 'border-amber-600 text-indigo-800'
                : 'border-transparent text-ink-400 hover:text-ink-700'
            )}
          >
            {tab.label}
            <span className={cn(
              'text-xs px-1.5 py-0.5',
              filter === tab.key ? 'bg-amber-100 text-amber-700' : 'bg-ink-100 text-ink-500'
            )}>
              {tab.count}
            </span>
          </button>
        ))}
      </div>

      {/* Grid */}
      {loading ? (
        <div className="text-center py-16 text-ink-400">加载中…</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
          {filtered.map((room) => (
            <LivePreview
              key={room.id}
              room={room}
              onStart={startLive}
              onEnd={endLive}
            />
          ))}
        </div>
      )}

      {filtered.length === 0 && !loading && (
        <div className="card-editorial p-16 text-center">
          <Clock className="w-12 h-12 mx-auto text-ink-200 mb-4" />
          <p className="text-ink-500">暂无直播记录</p>
          <p className="text-sm text-ink-400 mt-1">点击右上角「创建直播」开始安排</p>
        </div>
      )}
      </div>
      {createModal}
    </>
  );
}
