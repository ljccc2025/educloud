import { Play, Square, Users, Clock, Calendar } from 'lucide-react';
import type { LiveRoom } from '../types';
import { cn } from '../utils/cn';
import dayjs from 'dayjs';

interface LivePreviewProps {
  room: LiveRoom;
  onStart?: (id: string) => void;
  onEnd?: (id: string) => void;
}

const statusConfig = {
  CREATED: { label: '未开始', cls: 'badge-indigo' },
  LIVING: { label: '直播中', cls: 'badge-red' },
  ENDED: { label: '已结束', cls: 'badge-amber' },
};

export default function LivePreview({ room, onStart, onEnd }: LivePreviewProps) {
  const cfg = statusConfig[room.status];
  const start = dayjs(room.startTime);

  return (
    <div className="card-editorial overflow-hidden group">
      {/* Thumbnail */}
      <div className="relative aspect-video overflow-hidden bg-ink-100">
        <img
          src={room.thumbnail}
          alt={room.title}
          className={cn(
            'w-full h-full object-cover transition-transform duration-500',
            room.status === 'LIVING' ? 'scale-100' : 'group-hover:scale-105'
          )}
        />
        {/* Overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-ink-900/60 via-transparent to-transparent" />

        {/* Status badge */}
        <div className="absolute top-3 left-3">
          <span className={cn(cfg.cls, 'backdrop-blur-sm')}>
            {room.status === 'LIVING' && (
              <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
            )}
            {cfg.label}
          </span>
        </div>

        {/* Viewer count */}
        {room.status === 'LIVING' && (
          <div className="absolute top-3 right-3 flex items-center gap-1.5 px-2 py-0.5 bg-red-600/90 text-white text-xs font-medium backdrop-blur-sm">
            <Users className="w-3 h-3" />
            {room.viewerCount}
          </div>
        )}

        {/* Play button for living */}
        {room.status === 'LIVING' && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-16 h-16 rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center border-2 border-white/40">
              <Play className="w-7 h-7 text-white fill-white ml-1" />
            </div>
          </div>
        )}

        {/* Scheduled time overlay */}
        {room.status === 'CREATED' && (
          <div className="absolute bottom-3 left-3 flex items-center gap-2 text-white/90 text-xs">
            <Calendar className="w-3.5 h-3.5" />
            <span>{start.format('MM月DD日 HH:mm')}</span>
          </div>
        )}
      </div>

      {/* Content */}
      <div className="p-4 space-y-3">
        <div>
          <p className="text-xs text-amber-600 font-medium uppercase tracking-wider mb-1">
            {room.courseName}
          </p>
          <h3 className="font-display text-lg font-semibold text-ink-900 leading-snug line-clamp-2">
            {room.title}
          </h3>
        </div>

        {room.description && (
          <p className="text-sm text-ink-500 line-clamp-2">{room.description}</p>
        )}

        <div className="flex items-center gap-4 text-xs text-ink-400">
          <span className="flex items-center gap-1">
            <Clock className="w-3.5 h-3.5" />
            {start.format('YYYY-MM-DD HH:mm')}
          </span>
          {room.status === 'ENDED' && room.viewerCount > 0 && (
            <span className="flex items-center gap-1">
              <Users className="w-3.5 h-3.5" />
              {room.viewerCount} 人观看
            </span>
          )}
        </div>

        {/* Actions */}
        <div className="flex gap-2 pt-2 border-t border-ink-50">
          {room.status === 'CREATED' && onStart && (
            <button
              onClick={() => onStart(room.id)}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors"
            >
              <Play className="w-4 h-4" />
              开始直播
            </button>
          )}
          {room.status === 'LIVING' && onEnd && (
            <button
              onClick={() => onEnd(room.id)}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-ink-800 text-white text-sm font-medium hover:bg-ink-900 transition-colors"
            >
              <Square className="w-4 h-4" />
              结束直播
            </button>
          )}
          {room.status === 'ENDED' && (
            <button className="flex-1 flex items-center justify-center gap-2 px-4 py-2 border border-ink-200 text-ink-600 text-sm font-medium hover:border-ink-400 transition-colors">
              查看回放
            </button>
          )}
          {room.status === 'CREATED' && (
            <button className="btn-outline px-4 py-2">编辑</button>
          )}
        </div>
      </div>
    </div>
  );
}
