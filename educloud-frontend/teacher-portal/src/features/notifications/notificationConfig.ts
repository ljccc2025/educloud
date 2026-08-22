import {
  BookOpen,
  ClipboardCheck,
  FileQuestion,
  Radio,
  Settings,
  UserPlus,
  type LucideIcon,
} from 'lucide-react';
import type { TeacherNotificationKind } from './types';

interface NotificationKindConfig {
  label: string;
  icon: LucideIcon;
  className: string;
}

export const teacherNotificationKindConfig: Record<
  TeacherNotificationKind,
  NotificationKindConfig
> = {
  ASSIGNMENT: {
    label: '作业',
    icon: ClipboardCheck,
    className: 'bg-amber-50 text-amber-700',
  },
  LIVE: {
    label: '直播',
    icon: Radio,
    className: 'bg-red-50 text-red-700',
  },
  STUDENT: {
    label: '学员',
    icon: UserPlus,
    className: 'bg-indigo-50 text-indigo-700',
  },
  EXAM: {
    label: '考试',
    icon: FileQuestion,
    className: 'bg-emerald-50 text-emerald-700',
  },
  COURSE: {
    label: '课程',
    icon: BookOpen,
    className: 'bg-blue-50 text-blue-700',
  },
  SYSTEM: {
    label: '系统',
    icon: Settings,
    className: 'bg-ink-100 text-ink-600',
  },
};
