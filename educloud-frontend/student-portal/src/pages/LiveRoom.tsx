import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Radio, Users, Clock, ArrowLeft, Share2, Heart,
  Volume2, Maximize, Play,
} from 'lucide-react';
import { liveApi } from '@/services/api';
import type { LiveRoom as LiveRoomType, ChatMessage } from '@/types';
import ChatBox from '@/components/ChatBox';
import { cn } from '@/utils/cn';
import dayjs from 'dayjs';

export default function LiveRoom() {
  const { id } = useParams<{ id: string }>();
  const [room, setRoom] = useState<LiveRoomType | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([liveApi.getById(Number(id)), liveApi.getMessages()]).then(([roomData, msgData]) => {
      setRoom(roomData ?? null);
      setMessages(msgData);
      setLoading(false);
    });
  }, [id]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-2 border-indigo-800 border-t-transparent animate-spin" />
      </div>
    );
  }

  if (!room) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <p className="font-display text-2xl text-ink-400">直播间不存在</p>
        <Link to="/courses" className="btn-primary mt-6">返回课程</Link>
      </div>
    );
  }

  const isLive = room.status === 'LIVE';

  return (
    <div className="bg-paper dark:bg-ink-900 min-h-[calc(100vh-4rem)] transition-colors">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* Breadcrumb */}
        <Link
          to="/courses"
          className="inline-flex items-center gap-2 text-sm text-ink-500 dark:text-ink-300 hover:text-indigo-800 dark:hover:text-white transition-colors mb-4"
        >
          <ArrowLeft size={14} />
          返回课程
        </Link>

        <div className="grid items-start lg:grid-cols-3 gap-6">
          {/* Video Area */}
          <div className="lg:col-span-2">
            <div className="relative aspect-video bg-gradient-to-br from-ink-800 to-ink-900 overflow-hidden">
              {/* Decorative pattern */}
              <div
                className="absolute inset-0 opacity-5"
                style={{
                  backgroundImage:
                    'linear-gradient(white 1px, transparent 1px), linear-gradient(90deg, white 1px, transparent 1px)',
                  backgroundSize: '40px 40px',
                }}
              />

              {isLive ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center">
                  <div className="flex items-center gap-2 mb-4">
                    <span className="flex items-center gap-1.5 bg-red-600 text-white text-xs font-bold px-3 py-1 uppercase tracking-wider">
                      <Radio size={12} className="animate-pulse" />
                      LIVE
                    </span>
                  </div>
                  <div className="w-20 h-20 rounded-full bg-white/10 backdrop-blur-sm border border-white/20 flex items-center justify-center mb-4 hover:bg-amber-600 hover:border-amber-500 transition-all cursor-pointer">
                    <Play size={32} className="text-white ml-1" fill="currentColor" />
                  </div>
                  <p className="text-white/60 text-sm">点击播放直播</p>
                </div>
              ) : room.status === 'SCHEDULED' ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center px-6">
                  <Clock size={48} className="text-amber-500 mb-4" strokeWidth={1} />
                  <p className="text-white/80 text-lg font-medium mb-2">直播尚未开始</p>
                  <p className="text-white/50 text-sm">
                    开播时间：{dayjs(room.startTime).format('YYYY-MM-DD HH:mm')}
                  </p>
                </div>
              ) : (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center px-6">
                  <p className="text-white/60 text-lg font-medium mb-2">直播已结束</p>
                  <p className="text-white/40 text-sm mb-4">感谢观看，回放即将上线</p>
                  <button type="button" className="btn-outline !text-white !border-white/30 hover:!border-white">
                    <Play size={14} />
                    观看回放
                  </button>
                </div>
              )}

              {/* Live overlay info */}
              {isLive && (
                <>
                  <div className="absolute top-4 left-4 flex items-center gap-3">
                    <span className="flex items-center gap-1.5 bg-red-600 text-white text-xs font-bold px-2.5 py-1">
                      <span className="w-1.5 h-1.5 bg-white rounded-full animate-pulse" />
                      LIVE
                    </span>
                    <span className="flex items-center gap-1.5 bg-black/50 text-white text-xs px-2.5 py-1 backdrop-blur-sm">
                      <Users size={12} />
                      {room.viewerCount.toLocaleString()} 人观看
                    </span>
                  </div>
                  <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/70 to-transparent">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-indigo-600 flex items-center justify-center text-white font-semibold text-sm">
                          {room.teacherName.charAt(0)}
                        </div>
                        <div>
                          <p className="text-white font-medium text-sm">{room.teacherName}</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button type="button" className="p-2 text-white/70 hover:text-white transition-colors">
                          <Volume2 size={18} />
                        </button>
                        <button type="button" className="p-2 text-white/70 hover:text-white transition-colors">
                          <Maximize size={18} />
                        </button>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>

            {/* Video controls bar */}
            <div className="bg-ink-800 px-4 py-2 flex items-center gap-3">
              <div className="flex-1 h-1 bg-white/20 rounded-full overflow-hidden">
                <div className="h-full w-0 bg-red-500 rounded-full" />
              </div>
              <span className="text-white/50 text-xs">LIVE</span>
            </div>

            {/* Room Info */}
            <div className="bg-white dark:bg-ink-800 p-6 mt-px transition-colors">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div>
                  <h1 className="font-display text-2xl font-bold text-ink-900 dark:text-white">{room.title}</h1>
                  <p className="text-sm text-ink-500 dark:text-ink-300 mt-1">
                    所属课程：
                    <Link to={`/courses/${room.courseId}`} className="text-indigo-800 dark:text-indigo-300 link-underline">
                      {room.courseTitle}
                    </Link>
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button type="button" className="p-2 border border-ink-200 dark:border-ink-600 text-ink-500 dark:text-ink-300 hover:text-red-500 hover:border-red-200 transition-colors">
                    <Heart size={18} />
                  </button>
                  <button type="button" className="p-2 border border-ink-200 dark:border-ink-600 text-ink-500 dark:text-ink-300 hover:text-indigo-800 dark:hover:text-indigo-300 hover:border-indigo-200 transition-colors">
                    <Share2 size={18} />
                  </button>
                </div>
              </div>
              <div className="flex items-center gap-6 mt-4 pt-4 border-t border-ink-100 dark:border-ink-700 text-sm text-ink-500 dark:text-ink-300">
                <span className="flex items-center gap-1.5">
                  <Users size={16} />
                  {room.viewerCount.toLocaleString()} 人观看
                </span>
                <span className="flex items-center gap-1.5">
                  <Clock size={16} />
                  {dayjs(room.startTime).format('YYYY-MM-DD HH:mm')}
                </span>
                <span className={cn(
                  'badge',
                  isLive ? 'bg-red-50 dark:bg-red-950/40 text-red-600 dark:text-red-300 border border-red-200 dark:border-red-900'
                    : room.status === 'SCHEDULED'
                      ? 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-900'
                      : 'bg-ink-50 dark:bg-ink-700 text-ink-500 dark:text-ink-300 border border-ink-200 dark:border-ink-600'
                )}>
                  {isLive ? '直播中' : room.status === 'SCHEDULED' ? '未开始' : '已结束'}
                </span>
              </div>
            </div>
          </div>

          {/* Chat — viewport-aware height with independent scroll, sticky on desktop */}
          <div className="lg:col-span-1 live-chat-height lg:sticky lg:top-20 lg:self-start lg:min-h-0">
            <ChatBox messages={messages} />
          </div>
        </div>
      </div>
    </div>
  );
}
