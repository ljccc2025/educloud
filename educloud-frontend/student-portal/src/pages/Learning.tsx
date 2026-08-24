import { useEffect, useState, useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  BookOpen,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Play,
  FileText,
  ExternalLink,
  CheckCircle2,
  Lock,
  Sparkles,
  Info,
  Clock,
  User,
  HelpCircle,
} from 'lucide-react';
import { useCourseStore } from '@/stores/useCourseStore';
import VideoPlayer from '@/components/VideoPlayer';
import ProgressBar from '@/components/ProgressBar';
import { courseApi } from '@/services/api';
import { cn } from '@/utils/cn';
import type { Chapter, Courseware } from '@/types';

export default function Learning() {
  const { courseId } = useParams<{ courseId: string }>();
  const { currentCourse, loading, error, fetchCourse, fetchChapters } = useCourseStore();

  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [activeCourseware, setActiveCourseware] = useState<Courseware | null>(null);
  const [activeVideoUrl, setActiveVideoUrl] = useState<string | null>(null);
  const [expandedChapters, setExpandedChapters] = useState<Record<string, boolean>>({});
  const [activeTab, setActiveTab] = useState<'info' | 'materials' | 'faq'>('info');
  const [loadingChapters, setLoadingChapters] = useState(false);
  const [completedIds, setCompletedIds] = useState<Set<string>>(new Set());

  // 加载课程与章节
  useEffect(() => {
    if (!courseId) return;
    void fetchCourse(courseId);

    setLoadingChapters(true);
    fetchChapters(courseId)
      .then((data) => {
        setChapters(data);
        if (data.length > 0) {
          // 默认展开所有章节
          const initialExpanded: Record<string, boolean> = {};
          data.forEach((ch) => {
            initialExpanded[ch.id] = true;
          });
          setExpandedChapters(initialExpanded);

          // 默认选中第一个课件
          const firstCw = data.find((ch) => ch.coursewares.length > 0)?.coursewares[0];
          if (firstCw) {
            setActiveCourseware(firstCw);
          }
        }
      })
      .finally(() => {
        setLoadingChapters(false);
      });
  }, [courseId, fetchCourse, fetchChapters]);

  // 当选中课件切换时，解析播放 URL 并上报进度
  useEffect(() => {
    if (!activeCourseware) {
      setActiveVideoUrl(null);
      return;
    }

    if (activeCourseware.externalUrl) {
      setActiveVideoUrl(activeCourseware.externalUrl);
    } else if (activeCourseware.fileId) {
      courseApi
        .getCoursewareUrl(activeCourseware.id)
        .then((res) => {
          if (res?.downloadUrl) {
            setActiveVideoUrl(res.downloadUrl);
          }
        })
        .catch(() => {
          setActiveVideoUrl(null);
        });
    } else {
      setActiveVideoUrl(null);
    }
  }, [activeCourseware]);

  // 扁平化课件列表，便于前后翻页
  const flatCoursewares = useMemo(() => {
    const list: Array<{ chapterTitle: string; courseware: Courseware }> = [];
    chapters.forEach((ch) => {
      ch.coursewares.forEach((cw) => {
        list.push({ chapterTitle: ch.title, courseware: cw });
      });
    });
    return list;
  }, [chapters]);

  const currentIndex = useMemo(() => {
    if (!activeCourseware) return -1;
    return flatCoursewares.findIndex((item) => item.courseware.id === activeCourseware.id);
  }, [flatCoursewares, activeCourseware]);

  const prevItem = currentIndex > 0 ? flatCoursewares[currentIndex - 1] : null;
  const nextItem = currentIndex >= 0 && currentIndex < flatCoursewares.length - 1 ? flatCoursewares[currentIndex + 1] : null;

  // 完成进度统计
  const totalCount = flatCoursewares.length;
  const completedCount = completedIds.size;
  const progressPercent = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  const [isReporting, setIsReporting] = useState(false);

  const handleMarkComplete = async (cw: Courseware) => {
    if (isReporting) return;
    setIsReporting(true);
    setCompletedIds((prev) => new Set([...prev, cw.id]));
    try {
      if (courseId) {
        await courseApi.reportProgress(courseId, cw.id, cw.durationSeconds || 60, true);
      }
      if (nextItem) {
        setActiveCourseware(nextItem.courseware);
      }
    } finally {
      setIsReporting(false);
    }
  };

  const toggleChapter = (chapterId: string) => {
    setExpandedChapters((prev) => ({
      ...prev,
      [chapterId]: !prev[chapterId],
    }));
  };

  if (loading && !currentCourse) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin rounded-full" />
      </div>
    );
  }

  if (!currentCourse) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <div className="w-16 h-16 bg-ink-100 rounded-full flex items-center justify-center mx-auto mb-4 text-ink-400">
          <BookOpen size={32} />
        </div>
        <p className="font-display text-xl text-ink-700 font-bold mb-2">课程加载失败</p>
        <p className="text-sm text-ink-400 mb-6">{error ?? '课程不存在或已下架'}</p>
        <Link to="/courses" className="btn-primary inline-flex items-center gap-2">
          返回课程广场
        </Link>
      </div>
    );
  }

  const course = currentCourse;

  return (
    <div className="bg-ink-50/50 min-h-[calc(100vh-4rem)] py-6 lg:py-8">
      {/* 居中版心容器 */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* 顶部面包屑与标题条 */}
        <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-4 sm:p-5 rounded-2xl border border-ink-100 shadow-sm">
          <div className="min-w-0">
            <nav className="flex items-center gap-2 text-xs text-ink-400 mb-1">
              <Link to="/courses" className="hover:text-indigo-800 transition-colors">
                课程中心
              </Link>
              <span>/</span>
              <Link to={`/courses/${course.id}`} className="hover:text-indigo-800 transition-colors truncate max-w-[200px] sm:max-w-xs">
                {course.title}
              </Link>
              {activeCourseware && (
                <>
                  <span>/</span>
                  <span className="text-ink-600 font-medium truncate max-w-[150px] sm:max-w-xs">
                    {activeCourseware.title}
                  </span>
                </>
              )}
            </nav>
            <h1 className="font-display text-lg sm:text-xl font-bold text-ink-900 truncate">
              {activeCourseware?.title ?? course.title}
            </h1>
          </div>

          <div className="flex items-center gap-3 self-end sm:self-auto flex-shrink-0">
            <Link
              to={`/courses/${course.id}`}
              className="px-3.5 py-1.5 text-xs text-ink-600 hover:text-indigo-800 bg-ink-50 hover:bg-indigo-50 border border-ink-200 hover:border-indigo-200 rounded-lg transition-colors flex items-center gap-1.5"
            >
              <Info size={14} />
              课程主页
            </Link>
            <Link
              to="/my-courses"
              className="px-3.5 py-1.5 text-xs text-white bg-indigo-900 hover:bg-indigo-800 rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
            >
              <BookOpen size={14} />
              我的课程
            </Link>
          </div>
        </div>

        {/* 主体经典双栏网格 (8:4 比例) */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-start">
          {/* 左侧主体播放与课件详情区 (占 8 列) */}
          <div className="lg:col-span-8 space-y-6">
            {/* 播放器卡片 */}
            <div className="bg-ink-950 rounded-2xl overflow-hidden shadow-md border border-ink-800">
              <VideoPlayer
                title={activeCourseware?.title}
                videoUrl={activeVideoUrl}
                coursewareId={activeCourseware?.id}
                coursewareType={activeCourseware?.coursewareType}
                initialPositionSeconds={activeCourseware?.positionSeconds || 0}
                onComplete={() => activeCourseware && handleMarkComplete(activeCourseware)}
              />
            </div>

            {/* 课件操作条与翻页控制 */}
            <div className="bg-white rounded-2xl p-5 border border-ink-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="min-w-0">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <span className="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-indigo-50 text-indigo-800 border border-indigo-200">
                    {activeCourseware?.coursewareType === 'VIDEO' ? '视频课件' : '文档资料'}
                  </span>
                  {activeCourseware?.freePreview && (
                    <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                      免费试看
                    </span>
                  )}
                  {activeCourseware && completedIds.has(activeCourseware.id) && (
                    <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-green-50 text-green-700 border border-green-200 flex items-center gap-1">
                      <CheckCircle2 size={12} />
                      已学完
                    </span>
                  )}
                </div>
                <h2 className="font-display text-base sm:text-lg font-bold text-ink-900 truncate">
                  {activeCourseware?.title ?? '请在右侧目录选择课件开始学习'}
                </h2>
                {activeCourseware?.durationSeconds ? (
                  <p className="text-xs text-ink-400 mt-1 flex items-center gap-1">
                    <Clock size={12} />
                    预估时长：{Math.ceil(activeCourseware.durationSeconds / 60)} 分钟
                  </p>
                ) : null}
              </div>

              <div className="flex items-center gap-2.5 flex-shrink-0 self-end sm:self-auto">
                <button
                  type="button"
                  disabled={!prevItem}
                  onClick={() => prevItem && setActiveCourseware(prevItem.courseware)}
                  className={cn(
                    'px-3 py-1.5 text-xs font-medium rounded-lg border border-ink-200 bg-white hover:bg-ink-50 text-ink-700 transition-colors flex items-center gap-1',
                    !prevItem && 'opacity-40 cursor-not-allowed pointer-events-none',
                  )}
                >
                  <ChevronLeft size={14} />
                  上一节
                </button>

                {activeCourseware && !completedIds.has(activeCourseware.id) && (
                  <button
                    type="button"
                    onClick={() => handleMarkComplete(activeCourseware)}
                    className="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-amber-500 hover:bg-amber-600 text-white shadow-sm transition-colors flex items-center gap-1"
                  >
                    <CheckCircle2 size={14} />
                    标记学完
                  </button>
                )}

                <button
                  type="button"
                  disabled={!nextItem}
                  onClick={() => nextItem && setActiveCourseware(nextItem.courseware)}
                  className={cn(
                    'px-3 py-1.5 text-xs font-medium rounded-lg border border-ink-200 bg-white hover:bg-ink-50 text-ink-700 transition-colors flex items-center gap-1',
                    !nextItem && 'opacity-40 cursor-not-allowed pointer-events-none',
                  )}
                >
                  下一节
                  <ChevronRight size={14} />
                </button>
              </div>
            </div>

            {/* 下方 Tabs 详情卡片 */}
            <div className="bg-white rounded-2xl border border-ink-100 shadow-sm overflow-hidden">
              <div className="flex border-b border-ink-100 px-6">
                <button
                  type="button"
                  onClick={() => setActiveTab('info')}
                  className={cn(
                    'py-4 text-sm font-semibold border-b-2 transition-colors mr-6 flex items-center gap-1.5',
                    activeTab === 'info'
                      ? 'border-indigo-900 text-indigo-900'
                      : 'border-transparent text-ink-400 hover:text-ink-700',
                  )}
                >
                  <Info size={16} />
                  课程介绍
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('materials')}
                  className={cn(
                    'py-4 text-sm font-semibold border-b-2 transition-colors mr-6 flex items-center gap-1.5',
                    activeTab === 'materials'
                      ? 'border-indigo-900 text-indigo-900'
                      : 'border-transparent text-ink-400 hover:text-ink-700',
                  )}
                >
                  <FileText size={16} />
                  配套资料
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('faq')}
                  className={cn(
                    'py-4 text-sm font-semibold border-b-2 transition-colors flex items-center gap-1.5',
                    activeTab === 'faq'
                      ? 'border-indigo-900 text-indigo-900'
                      : 'border-transparent text-ink-400 hover:text-ink-700',
                  )}
                >
                  <HelpCircle size={16} />
                  常见问题
                </button>
              </div>

              <div className="p-6">
                {activeTab === 'info' && (
                  <div className="space-y-4">
                    <h3 className="font-display text-base font-bold text-ink-900">关于这门课程</h3>
                    <p className="text-sm text-ink-600 leading-relaxed whitespace-pre-line">
                      {course.description || '暂无详细介绍。'}
                    </p>
                    <div className="pt-4 border-t border-ink-100 flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-indigo-100 text-indigo-900 flex items-center justify-center font-bold text-sm">
                        <User size={18} />
                      </div>
                      <div>
                        <p className="text-sm font-bold text-ink-900">
                          {course.teachers?.[0]?.teacherId ? `主讲讲师 ID ···${course.teachers[0].teacherId.slice(-4)}` : 'EduCloud 认证名师团队'}
                        </p>
                        <p className="text-xs text-ink-400">资深全栈架构师 · 课程总设计师</p>
                      </div>
                    </div>
                  </div>
                )}

                {activeTab === 'materials' && (
                  <div className="space-y-3">
                    <p className="text-xs text-ink-400 mb-3">本课程随堂课件与资料清单：</p>
                    {flatCoursewares.filter((item) => item.courseware.coursewareType === 'DOCUMENT').length > 0 ? (
                      flatCoursewares
                        .filter((item) => item.courseware.coursewareType === 'DOCUMENT')
                        .map((item) => (
                          <div
                            key={item.courseware.id}
                            className="flex items-center justify-between p-3.5 rounded-xl bg-ink-50 border border-ink-100 hover:bg-indigo-50/50 transition-colors"
                          >
                            <div className="flex items-center gap-3">
                              <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center">
                                <FileText size={18} />
                              </div>
                              <div>
                                <p className="text-sm font-medium text-ink-900">{item.courseware.title}</p>
                                <p className="text-xs text-ink-400">{item.chapterTitle}</p>
                              </div>
                            </div>
                            <button
                              type="button"
                              onClick={() => setActiveCourseware(item.courseware)}
                              className="px-3 py-1 text-xs text-indigo-900 font-semibold hover:underline"
                            >
                              在线预览
                            </button>
                          </div>
                        ))
                    ) : (
                      <div className="py-8 text-center text-ink-400 text-sm">
                        暂无独立文档附件，视频配套讲义请直接点击大纲课件学习。
                      </div>
                    )}
                  </div>
                )}

                {activeTab === 'faq' && (
                  <div className="space-y-3 text-sm text-ink-600">
                    <div className="p-3.5 rounded-xl bg-ink-50 border border-ink-100">
                      <p className="font-semibold text-ink-900 mb-1">Q: 学习进度会自动保存吗？</p>
                      <p className="text-xs text-ink-500">A: 会的。系统内置防作弊智能进度追踪，每次播放都会自动记录您的观看时长并同步至云端。</p>
                    </div>
                    <div className="p-3.5 rounded-xl bg-ink-50 border border-ink-100">
                      <p className="font-semibold text-ink-900 mb-1">Q: 购买后是否支持反复观看？</p>
                      <p className="text-xs text-ink-500">A: 支持。已购课程享有永久回放与在线讲义查阅权益。</p>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 右侧课程目录与学习进度栏 (占 4 列) */}
          <div className="lg:col-span-4 space-y-6">
            {/* 进度概览卡片 */}
            <div className="bg-white rounded-2xl p-5 border border-ink-100 shadow-sm">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Sparkles size={16} className="text-amber-500" />
                  <h3 className="font-display text-sm font-bold text-ink-900">学习进度</h3>
                </div>
                <span className="text-xs font-bold text-indigo-900 bg-indigo-50 px-2.5 py-0.5 rounded-full">
                  {completedCount} / {totalCount} 课时
                </span>
              </div>
              <ProgressBar progress={progressPercent} className="h-2.5" />
              <div className="mt-3 flex items-center justify-between text-xs text-ink-400">
                <span>总体完成度</span>
                <span className="font-bold text-ink-700">{progressPercent}%</span>
              </div>
            </div>

            {/* 课程大纲树卡片 */}
            <div className="bg-white rounded-2xl border border-ink-100 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-ink-100 flex items-center justify-between bg-ink-50/40">
                <div>
                  <h3 className="font-display text-base font-bold text-ink-900">课程目录</h3>
                  <p className="text-xs text-ink-400 mt-0.5">
                    共 {chapters.length} 章节 · {totalCount} 个课件
                  </p>
                </div>
              </div>

              {loadingChapters ? (
                <div className="p-8 text-center">
                  <div className="w-6 h-6 border-2 border-indigo-900 border-t-transparent animate-spin rounded-full mx-auto mb-2" />
                  <p className="text-xs text-ink-400">加载课程大纲中...</p>
                </div>
              ) : chapters.length === 0 ? (
                <div className="p-8 text-center text-ink-400">
                  <BookOpen size={28} className="mx-auto mb-2 text-ink-300" strokeWidth={1.5} />
                  <p className="text-sm font-medium">章节内容准备中</p>
                  <p className="text-xs text-ink-400 mt-1">讲师正在上传课程课件与资料</p>
                </div>
              ) : (
                <div className="divide-y divide-ink-100 max-h-[640px] overflow-y-auto">
                  {chapters.map((chapter, chIdx) => {
                    const isExpanded = expandedChapters[chapter.id] ?? true;
                    return (
                      <div key={chapter.id} className="bg-white">
                        {/* 章节标题头 */}
                        <button
                          type="button"
                          onClick={() => toggleChapter(chapter.id)}
                          className="w-full px-4 py-3.5 flex items-center justify-between text-left hover:bg-ink-50/80 transition-colors"
                        >
                          <div className="min-w-0 pr-2">
                            <span className="text-xs font-semibold text-indigo-900 block mb-0.5">
                              第 {chIdx + 1} 章
                            </span>
                            <h4 className="text-sm font-bold text-ink-900 truncate">
                              {chapter.title}
                            </h4>
                          </div>
                          <ChevronDown
                            size={16}
                            className={cn(
                              'text-ink-400 flex-shrink-0 transition-transform duration-200',
                              isExpanded ? 'rotate-180' : '',
                            )}
                          />
                        </button>

                        {/* 章节下课件列表 */}
                        {isExpanded && (
                          <div className="bg-ink-50/30 px-2 py-1 space-y-1">
                            {chapter.coursewares.map((cw) => {
                              const isActive = activeCourseware?.id === cw.id;
                              const isCompleted = completedIds.has(cw.id);

                              return (
                                <button
                                  key={cw.id}
                                  type="button"
                                  onClick={() => setActiveCourseware(cw)}
                                  className={cn(
                                    'w-full px-3 py-2.5 rounded-xl flex items-center justify-between text-left transition-all text-xs',
                                    isActive
                                      ? 'bg-indigo-900 text-white shadow-sm'
                                      : 'hover:bg-ink-100/70 text-ink-700',
                                  )}
                                >
                                  <div className="flex items-center gap-2.5 min-w-0 pr-2">
                                    <div
                                      className={cn(
                                        'w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0',
                                        isActive
                                          ? 'bg-white/20 text-white'
                                          : isCompleted
                                            ? 'bg-green-100 text-green-700'
                                            : 'bg-ink-100 text-ink-500',
                                      )}
                                    >
                                      {isCompleted ? (
                                        <CheckCircle2 size={13} />
                                      ) : cw.coursewareType === 'VIDEO' ? (
                                        <Play size={12} fill={isActive ? 'currentColor' : 'none'} />
                                      ) : (
                                        <FileText size={12} />
                                      )}
                                    </div>
                                    <span
                                      className={cn(
                                        'truncate font-medium',
                                        isActive ? 'text-white' : 'text-ink-800',
                                      )}
                                    >
                                      {cw.title}
                                    </span>
                                  </div>

                                  <div className="flex items-center gap-1.5 flex-shrink-0">
                                    {cw.freePreview && !isActive && (
                                      <span className="px-1.5 py-0.5 text-[10px] rounded bg-emerald-100 text-emerald-800 font-semibold">
                                        试看
                                      </span>
                                    )}
                                    {cw.durationSeconds ? (
                                      <span
                                        className={cn(
                                          'text-[11px]',
                                          isActive ? 'text-indigo-200' : 'text-ink-400',
                                        )}
                                      >
                                        {Math.ceil(cw.durationSeconds / 60)}m
                                      </span>
                                    ) : null}
                                  </div>
                                </button>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}