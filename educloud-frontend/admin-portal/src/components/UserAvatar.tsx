import React, { useState } from 'react';
import { cn } from '../utils/cn';

interface UserAvatarProps {
  name?: string | null;
  src?: string | null;
  size?: 'sm' | 'md' | 'lg' | 'xl' | number;
  className?: string;
}

/**
 * 优雅单字/首字符提取算法：
 * 1. 若包含汉字，取最后一个汉字（如「张伟」→「伟」，「王芳」→「芳」）；
 * 2. 若为英文/数字，取首字母大写（如「user1000」→「U」）；
 * 3. 兜底为「用」。
 */
export function getAvatarChar(name?: string | null): string {
  if (!name || !name.trim()) return '用';
  const trimmed = name.trim();
  const chineseMatches = trimmed.match(/[\u4e00-\u9fa5]/g);
  if (chineseMatches && chineseMatches.length > 0) {
    return chineseMatches[chineseMatches.length - 1];
  }
  const firstAlpha = trimmed.match(/[a-zA-Z0-9]/);
  if (firstAlpha) {
    return firstAlpha[0].toUpperCase();
  }
  return trimmed.slice(-1);
}

/**
 * 基于字符串哈希确定性映射到 8 组高端低饱和商务微渐变色
 */
const AVATAR_GRADIENTS = [
  'from-indigo-600 to-indigo-700',   // 靛蓝
  'from-blue-600 to-blue-700',       // 暮光蓝
  'from-emerald-600 to-emerald-700', // 翡翠黛绿
  'from-amber-500 to-amber-600',     // 暖阳琥珀
  'from-purple-600 to-purple-700',   // 高雅紫罗兰
  'from-rose-500 to-rose-600',       // 雅致石榴红
  'from-teal-600 to-teal-700',       // 松石青蓝
  'from-slate-600 to-slate-700',     // 高级青灰
];

export function getAvatarGradient(name?: string | null): string {
  if (!name) return AVATAR_GRADIENTS[0];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  const index = Math.abs(hash) % AVATAR_GRADIENTS.length;
  return AVATAR_GRADIENTS[index];
}

const sizeClasses = {
  sm: 'w-8 h-8 text-xs',
  md: 'w-10 h-10 text-[15px]',
  lg: 'w-12 h-12 text-lg',
  xl: 'w-14 h-14 text-xl',
};

export const UserAvatar: React.FC<UserAvatarProps> = ({
  name,
  src,
  size = 'md',
  className,
}) => {
  const [imgError, setImgError] = useState(false);
  const showImg = src && !imgError && !src.includes('dicebear');

  const char = getAvatarChar(name);
  const gradient = getAvatarGradient(name);
  const sizeCls = typeof size === 'string' ? sizeClasses[size] : '';
  const customStyle = typeof size === 'number' ? { width: size, height: size, fontSize: Math.round(size * 0.38) } : undefined;

  if (showImg) {
    return (
      <img
        src={src}
        alt={name ?? '用户头像'}
        onError={() => setImgError(true)}
        style={customStyle}
        className={cn(
          'rounded-full object-cover shrink-0 ring-1 ring-black/5 dark:ring-white/10 shadow-sm',
          sizeCls,
          className,
        )}
      />
    );
  }

  return (
    <div
      style={customStyle}
      className={cn(
        'rounded-full shrink-0 flex items-center justify-center font-medium select-none shadow-sm',
        'text-white antialiased bg-gradient-to-br ring-1 ring-black/10 dark:ring-white/15',
        'tracking-normal leading-none font-sans',
        gradient,
        sizeCls,
        className,
      )}
      title={name ?? undefined}
    >
      <span className="transform translate-y-[-0.5px] drop-shadow-[0_1px_1px_rgba(0,0,0,0.2)]">
        {char}
      </span>
    </div>
  );
};
