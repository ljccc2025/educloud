import dayjs from 'dayjs';

/**
 * 相对时间工具（角色化动态流阶段 4）。
 *
 * 必须基于后端返回的 `timestamp`（ISO-8601，如 2026-08-27T10:30:00）计算；
 * 绝不使用后端中文相对时间字段（timeAgo，如“10分钟前”），
 * 中文文案无法被 dayjs 解析，会导致 Invalid Date（已修复过的 bug）。
 */
export function relativeTime(timestamp?: string | null): string {
  if (!timestamp) return '';
  const t = dayjs(timestamp);
  if (!t.isValid()) return '';
  const minutes = dayjs().diff(t, 'minute');
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = dayjs().diff(t, 'hour');
  if (hours < 24) return `${hours} 小时前`;
  const days = dayjs().diff(t, 'day');
  if (days < 7) return `${days} 天前`;
  return t.format('YYYY-MM-DD');
}
