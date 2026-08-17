import { ArrowUp, ArrowDown, type LucideIcon } from 'lucide-react';
import { cn } from '../utils/cn';

interface StatsCardProps {
  label: string;
  value: number | string;
  trend?: number;
  trendLabel?: string;
  icon: LucideIcon;
  prefix?: string;
  suffix?: string;
  delay?: string;
}

export default function StatsCard({
  label,
  value,
  trend,
  trendLabel,
  icon: Icon,
  prefix = '',
  suffix = '',
  delay = '',
}: StatsCardProps) {
  const isUp = trend !== undefined && trend >= 0;
  const isDown = trend !== undefined && trend < 0;

  const formattedValue =
    typeof value === 'number' ? value.toLocaleString('zh-CN') : value;

  return (
    <div className={cn('stat-card animate-fade-up opacity-0', delay)}>
      <div className="flex items-start justify-between mb-4">
        <span className="text-xs font-medium uppercase tracking-widest text-ink-400">
          {label}
        </span>
        <span className="flex items-center justify-center w-9 h-9 bg-indigo-50 text-indigo-800">
          <Icon size={18} />
        </span>
      </div>
      <div className="font-display text-4xl font-700 text-ink-900 leading-none mb-3">
        {prefix}
        {formattedValue}
        {suffix}
      </div>
      {trend !== undefined && (
        <div className="flex items-center gap-2 text-sm">
          <span
            className={cn(
              'inline-flex items-center gap-0.5 font-medium',
              isUp ? 'text-green-600' : 'text-red-600',
            )}
          >
            {isUp ? <ArrowUp size={14} /> : <ArrowDown size={14} />}
            {Math.abs(trend)}%
          </span>
          <span className="text-ink-400">{trendLabel ?? '较上周'}</span>
        </div>
      )}
    </div>
  );
}
