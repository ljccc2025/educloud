import { useState, useRef, useEffect, useCallback } from 'react';
import { Play, Pause, FileText, ExternalLink, RotateCcw, Volume2, VolumeX, Maximize } from 'lucide-react';
import { cn } from '@/utils/cn';
import type { CoursewareType } from '@/types';

interface VideoPlayerProps {
  title?: string;
  videoUrl?: string | null;
  coursewareId?: string;
  coursewareType?: CoursewareType;
  initialPositionSeconds?: number;
  className?: string;
  onComplete?: () => void;
  onTimeUpdate?: (seconds: number) => void;
}

export default function VideoPlayer({
  title,
  videoUrl,
  coursewareId,
  coursewareType = 'VIDEO',
  initialPositionSeconds = 0,
  className,
  onComplete,
  onTimeUpdate,
}: VideoPlayerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [resumeTip, setResumeTip] = useState<string | null>(null);

  // 切换课件时重置状态
  useEffect(() => {
    setIsPlaying(false);
    if (initialPositionSeconds > 0) {
      const minutes = Math.floor(initialPositionSeconds / 60);
      const seconds = initialPositionSeconds % 60;
      setResumeTip(`已为您记忆断点：${minutes}:${seconds < 10 ? '0' : ''}${seconds}`);
      const timer = setTimeout(() => setResumeTip(null), 4000);
      return () => clearTimeout(timer);
    } else {
      setResumeTip(null);
    }
  }, [videoUrl, coursewareId, initialPositionSeconds]);

  // 原生视频加载后跳转断点
  const handleLoadedMetadata = () => {
    if (videoRef.current && initialPositionSeconds > 0) {
      videoRef.current.currentTime = initialPositionSeconds;
    }
  };

  // 全屏切换
  const toggleFullscreen = useCallback(() => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      void containerRef.current.requestFullscreen?.();
    } else {
      void document.exitFullscreen?.();
    }
  }, []);

  // 键盘快捷键监听
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // 忽略在输入框中的按键
      const target = e.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
        return;
      }

      if (e.code === 'Space') {
        e.preventDefault();
        if (videoRef.current) {
          if (videoRef.current.paused) {
            void videoRef.current.play();
          } else {
            videoRef.current.pause();
          }
        } else {
          setIsPlaying((prev) => !prev);
        }
      } else if (e.code === 'ArrowRight') {
        e.preventDefault();
        if (videoRef.current) {
          videoRef.current.currentTime = Math.min(videoRef.current.duration, videoRef.current.currentTime + 5);
        }
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault();
        if (videoRef.current) {
          videoRef.current.currentTime = Math.max(0, videoRef.current.currentTime - 5);
        }
      } else if (e.code === 'KeyM') {
        e.preventDefault();
        if (videoRef.current) {
          videoRef.current.muted = !videoRef.current.muted;
          setIsMuted(videoRef.current.muted);
        }
      } else if (e.code === 'KeyF') {
        e.preventDefault();
        toggleFullscreen();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [toggleFullscreen]);

  if (coursewareType === 'DOCUMENT') {
    return (
      <div
        className={cn(
          'relative w-full aspect-video bg-ink-900 rounded-2xl flex flex-col items-center justify-center text-white p-8 text-center shadow-lg',
          className,
        )}
      >
        <div className="w-16 h-16 rounded-2xl bg-white/10 flex items-center justify-center mb-4 text-amber-400">
          <FileText size={36} />
        </div>
        <h3 className="font-display text-lg font-bold mb-2">{title || '文档课件'}</h3>
        <p className="text-sm text-ink-300 max-w-md mb-6">
          当前课件为讲义/PDF 文档资源，支持在线预览与配套查阅。
        </p>
        {videoUrl ? (
          <a
            href={videoUrl}
            target="_blank"
            rel="noreferrer"
            className="btn-primary inline-flex items-center gap-2 shadow-md hover:scale-105 transition-transform"
          >
            <ExternalLink size={16} />
            打开讲义文档
          </a>
        ) : (
          <span className="text-xs text-ink-400">文档加载中...</span>
        )}
      </div>
    );
  }

  if (
    videoUrl &&
    (videoUrl.startsWith('http://') || videoUrl.startsWith('https://') || videoUrl.startsWith('/'))
  ) {
    return (
      <div
        ref={containerRef}
        className={cn(
          'relative w-full aspect-video bg-black rounded-2xl overflow-hidden shadow-lg border border-ink-800 group',
          className,
        )}
      >
        {resumeTip && (
          <div className="absolute top-4 left-4 z-30 bg-ink-900/90 text-amber-400 text-xs px-3 py-1.5 rounded-lg backdrop-blur-sm border border-amber-500/20 shadow-md animate-fade-in flex items-center gap-1.5">
            <RotateCcw size={13} />
            {resumeTip}
          </div>
        )}
        <video
          ref={videoRef}
          src={videoUrl}
          controls
          playsInline
          className="w-full h-full object-contain"
          onLoadedMetadata={handleLoadedMetadata}
          onTimeUpdate={() => {
            if (videoRef.current) {
              onTimeUpdate?.(Math.floor(videoRef.current.currentTime));
            }
          }}
          onPlay={() => setIsPlaying(true)}
          onPause={() => setIsPlaying(false)}
          onEnded={() => {
            setIsPlaying(false);
            onComplete?.();
          }}
        />
      </div>
    );
  }

  // 优雅模拟播放器
  return (
    <div
      ref={containerRef}
      className={cn(
        'relative w-full aspect-video bg-ink-950 rounded-2xl overflow-hidden shadow-lg border border-ink-800 group',
        className,
      )}
    >
      <div
        className="absolute inset-0 opacity-15"
        style={{
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)',
          backgroundSize: '36px 36px',
        }}
      />

      {resumeTip && (
        <div className="absolute top-4 left-4 z-30 bg-ink-900/90 text-amber-400 text-xs px-3 py-1.5 rounded-lg backdrop-blur-sm border border-amber-500/20 shadow-md flex items-center gap-1.5">
          <RotateCcw size={13} />
          {resumeTip}
        </div>
      )}

      {!isPlaying ? (
        <button
          type="button"
          onClick={() => setIsPlaying(true)}
          className="absolute inset-0 flex flex-col items-center justify-center gap-4 z-10 hover:bg-black/20 transition-all"
        >
          <div className="w-20 h-20 rounded-full bg-amber-500 text-white flex items-center justify-center shadow-lg transition-transform duration-300 group-hover:scale-110">
            <Play size={32} className="ml-1" fill="currentColor" />
          </div>
          {title && (
            <p className="text-white font-medium text-base tracking-wide px-4 text-center">
              {title}
            </p>
          )}
          <div className="flex items-center gap-2">
            <span className="text-xs text-amber-400/80 bg-amber-500/10 px-3 py-1 rounded-full border border-amber-500/20">
              点击开始学习
            </span>
            <span className="text-[11px] text-white/40 hidden sm:inline">
              (按 Space 播放/暂停 · F 全屏)
            </span>
          </div>
        </button>
      ) : (
        <div className="absolute inset-0 flex flex-col items-center justify-center z-10">
          <div className="flex items-center justify-center gap-1.5 mb-4">
            <span className="w-1.5 h-8 bg-amber-500 rounded-full animate-pulse" />
            <span
              className="w-1.5 h-12 bg-amber-500 rounded-full animate-pulse"
              style={{ animationDelay: '150ms' }}
            />
            <span
              className="w-1.5 h-6 bg-amber-500 rounded-full animate-pulse"
              style={{ animationDelay: '300ms' }}
            />
            <span
              className="w-1.5 h-10 bg-amber-500 rounded-full animate-pulse"
              style={{ animationDelay: '450ms' }}
            />
            <span
              className="w-1.5 h-7 bg-amber-500 rounded-full animate-pulse"
              style={{ animationDelay: '600ms' }}
            />
          </div>
          <p className="text-white/80 text-sm font-medium">{title || '课程视频播放中'}</p>
          <div className="flex items-center gap-3 mt-4">
            <button
              type="button"
              onClick={() => setIsPlaying(false)}
              className="px-4 py-1.5 text-xs text-white/70 bg-white/10 hover:bg-white/20 rounded-full transition-colors flex items-center gap-1.5"
            >
              <Pause size={14} />
              暂停 (Space)
            </button>
            <button
              type="button"
              onClick={() => {
                setIsPlaying(false);
                onComplete?.();
              }}
              className="px-4 py-1.5 text-xs text-amber-400 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 rounded-full transition-colors"
            >
              标记已完成
            </button>
          </div>
        </div>
      )}

      {/* 底部控制栏 */}
      <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-black/80 to-transparent flex items-center px-4 gap-3 z-20">
        <button
          type="button"
          onClick={() => setIsPlaying(!isPlaying)}
          className="text-white/80 hover:text-white transition-colors"
        >
          {isPlaying ? <Pause size={16} /> : <Play size={16} fill="currentColor" />}
        </button>

        <div className="flex-1 h-1.5 bg-white/20 rounded-full overflow-hidden">
          <div
            className={cn(
              'h-full bg-amber-500 rounded-full transition-all duration-300',
              isPlaying ? 'w-2/3' : 'w-1/4',
            )}
          />
        </div>
        <span className="text-white/70 text-xs font-mono">12:34 / 38:20</span>

        <button
          type="button"
          onClick={() => setIsMuted(!isMuted)}
          className="text-white/70 hover:text-white transition-colors ml-2"
          title="静音切换 (M)"
        >
          {isMuted ? <VolumeX size={16} /> : <Volume2 size={16} />}
        </button>

        <button
          type="button"
          onClick={toggleFullscreen}
          className="text-white/70 hover:text-white transition-colors"
          title="全屏切换 (F)"
        >
          <Maximize size={16} />
        </button>
      </div>
    </div>
  );
}
