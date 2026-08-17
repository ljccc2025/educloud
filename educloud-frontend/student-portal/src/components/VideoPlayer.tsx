import { useState } from 'react';
import { Play } from 'lucide-react';
import { cn } from '@/utils/cn';

interface VideoPlayerProps {
  title?: string;
  className?: string;
}

export default function VideoPlayer({ title, className }: VideoPlayerProps) {
  const [playing, setPlaying] = useState(false);

  return (
    <div className={cn('relative w-full aspect-video bg-indigo-900 overflow-hidden group', className)}>
      {/* Decorative grid pattern */}
      <div
        className="absolute inset-0 opacity-10"
        style={{
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)',
          backgroundSize: '40px 40px',
        }}
      />

      {!playing ? (
        <button
          type="button"
          onClick={() => setPlaying(true)}
          className="absolute inset-0 flex flex-col items-center justify-center gap-4 z-10"
        >
          <div className="w-20 h-20 bg-white/10 backdrop-blur-sm border border-white/20 flex items-center justify-center transition-all duration-300 group-hover:bg-amber-600 group-hover:border-amber-500 group-hover:scale-110">
            <Play size={32} className="text-white ml-1" fill="currentColor" />
          </div>
          {title && (
            <p className="text-white/80 text-sm font-medium tracking-wide">{title}</p>
          )}
          <p className="text-white/40 text-xs">点击播放</p>
        </button>
      ) : (
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="text-center">
            <div className="flex items-center justify-center gap-1 mb-3">
              <span className="w-1.5 h-8 bg-amber-500 animate-pulse" />
              <span className="w-1.5 h-12 bg-amber-500 animate-pulse" style={{ animationDelay: '150ms' }} />
              <span className="w-1.5 h-6 bg-amber-500 animate-pulse" style={{ animationDelay: '300ms' }} />
              <span className="w-1.5 h-10 bg-amber-500 animate-pulse" style={{ animationDelay: '450ms' }} />
              <span className="w-1.5 h-7 bg-amber-500 animate-pulse" style={{ animationDelay: '600ms' }} />
            </div>
            <p className="text-white/60 text-sm">视频播放中...</p>
            <button
              type="button"
              onClick={() => setPlaying(false)}
              className="mt-3 text-xs text-white/40 hover:text-white/80 transition-colors"
            >
              暂停
            </button>
          </div>
        </div>
      )}

      {/* Bottom control bar */}
      <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-black/60 to-transparent flex items-center px-4 gap-3 z-20">
        <div className="flex-1 h-1 bg-white/20 rounded-full overflow-hidden">
          <div className="h-full w-1/3 bg-amber-500 rounded-full" />
        </div>
        <span className="text-white/60 text-xs">12:34 / 38:20</span>
      </div>
    </div>
  );
}
