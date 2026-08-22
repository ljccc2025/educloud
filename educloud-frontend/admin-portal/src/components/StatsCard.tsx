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
    <div className={cn('stat-card animate-fade-up opacity-0 group', delay)}>
      <div className="flex items-start justify-between mb-4">
        <span className="text-xs font-medium uppercase tracking-widest text-ink-500">
          {label}
        </span>
        <span className="flex items-center justify-center w-10 h-10 bg-brand-500/10 text-brand-500 dark:text-brand-400 rounded-xl group-hover:bg-brand-500/20 transition-colors">
          <Icon size={18} />
        </span>
      </div>
      <div className="font-sans text-3xl lg:text-4xl font-bold text-ink-900 leading-none mb-3 tracking-tight">
        {prefix}
        {formattedValue}
        {suffix}
      </div>
      {trend !== undefined && (
        <div className="flex items-center gap-2 text-sm">
          <span
            className={cn(
              'inline-flex items-center gap-0.5 font-medium px-1.5 py-0.5 rounded-md text-xs',
              isUp ? 'text-green-600 dark:text-green-400 bg-green-500/10' : 'text-red-600 dark:text-red-400 bg-red-500/10',
            )}
          >
            {isUp ? <ArrowUp size={12} /> : <ArrowDown size={12} />}
            {Math.abs(trend)}%
          </span>
          <span className="text-ink-500">{trendLabel ?? '较上周'}</span>
        </div>
      )}
    </div>
  );
}
