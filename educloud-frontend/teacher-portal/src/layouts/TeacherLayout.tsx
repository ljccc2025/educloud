import { useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  BookOpen,
  FolderTree,
  Radio,
  ClipboardCheck,
  FileQuestion,
  Users,
  BarChart3,
  Search,
  LogOut,
  Menu,
  X,
} from 'lucide-react';
import { cn } from '../utils/cn';
import NotificationPopover from '../features/notifications/NotificationPopover';
import { useAuthStore } from '../stores/useAuthStore';

// M04 审查修复：presigned 头像 URL 5 分钟过期后破图，onError 兜底回退占位头像。
const FALLBACK_AVATAR =
  'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangming&backgroundColor=1e1b4b';

const navItems = [
  { to: '/', label: '工作台', icon: LayoutDashboard, end: true },
  { to: '/courses', label: '课程管理', icon: BookOpen, end: false },
  { to: '/content', label: '内容管理', icon: FolderTree, end: false },
  { to: '/live', label: '直播管理', icon: Radio, end: false },
  { to: '/assignments', label: '作业批改', icon: ClipboardCheck, end: false },
  { to: '/exams', label: '考试管理', icon: FileQuestion, end: false },
  { to: '/students', label: '学生管理', icon: Users, end: false },
  { to: '/analytics', label: '数据分析', icon: BarChart3, end: false },
];

const breadcrumbMap: Record<string, string> = {
  '/': '工作台',
  '/courses': '课程管理',
  '/content': '内容管理',
  '/live': '直播管理',
  '/assignments': '作业批改',
  '/exams': '考试管理',
  '/students': '学生管理',
  '/analytics': '数据分析',
  '/notifications': '通知中心',
};

export default function TeacherLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const pathParts = location.pathname.split('/').filter(Boolean);
  const currentLabel = breadcrumbMap[`/${pathParts[0] ?? ''}`] ?? '工作台';

  const handleLogout = () => {
    // 真实退出：撤销服务端会话 + 清理本地登录态，再回登录页。
    void useAuthStore.getState().logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen flex bg-paper">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-ink-900/40 z-30 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed lg:sticky top-0 left-0 z-40 h-screen w-64 bg-white border-r border-ink-100 flex flex-col transition-transform duration-300 rounded-r-2xl',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
        )}
      >
        {/* Logo */}
        <div className="flex items-center gap-3 px-6 py-5 border-b border-ink-100">
          <div className="w-9 h-9 bg-indigo-800 flex items-center justify-center rounded-lg">
            <BookOpen className="w-5 h-5 text-amber-400" strokeWidth={2} />
          </div>
          <div>
            <h1 className="font-display text-lg font-bold text-ink-900 leading-none">EduCloud</h1>
            <p className="text-[10px] text-ink-400 uppercase tracking-widest mt-0.5">教师端</p>
          </div>
          <button
            onClick={() => setSidebarOpen(false)}
            className="ml-auto lg:hidden text-ink-400 rounded-lg p-1 hover:bg-ink-50"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Nav */}
        <nav className="flex-1 py-4 overflow-y-auto">
          <p className="px-6 py-2 text-[10px] font-semibold uppercase tracking-widest text-ink-300">
            教学管理
          </p>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) => (isActive ? 'nav-item-active' : 'nav-item')}
            >
              <item.icon className="w-4 h-4 flex-shrink-0" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* User card */}
        <div className="p-4 border-t border-ink-100">
          <div className="flex items-center gap-3 p-3 bg-ink-50/50 rounded-xl">
            <img
              src={user?.avatarUrl ?? 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangming&backgroundColor=1e1b4b'}
              alt={user?.name ?? '张明教授'}
              onError={(e) => {
                const img = e.currentTarget;
                img.onerror = null;
                img.src = FALLBACK_AVATAR;
              }}
              className="w-10 h-10 rounded-full bg-indigo-100 object-cover"
            />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-ink-800 truncate">{user?.name ?? '张明教授'}</p>
              <p className="text-xs text-ink-400 truncate">{user?.title ?? '高级讲师'}</p>
            </div>
            <button
              onClick={handleLogout}
              className="text-ink-300 hover:text-red-600 transition-colors rounded-lg p-1 hover:bg-white"
              title="退出登录"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="sticky top-0 z-20 bg-paper/80 backdrop-blur-md border-b border-ink-100">
          <div className="flex items-center gap-4 px-6 py-4">
            <button
              onClick={() => setSidebarOpen(true)}
              className="lg:hidden text-ink-600 rounded-lg p-1 hover:bg-ink-50"
            >
              <Menu className="w-5 h-5" />
            </button>

            {/* Breadcrumb */}
            <nav className="flex items-center gap-2 text-sm">
              <span className="text-ink-400">EduCloud</span>
              <span className="text-ink-300">/</span>
              <span className="font-medium text-ink-800">{currentLabel}</span>
            </nav>

            {/* Search */}
            <div className="hidden md:flex items-center ml-6 flex-1 max-w-md">
              <div className="relative w-full">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-300" />
                <input
                  type="text"
                  placeholder="搜索课程、学生、作业……"
                  className="w-full pl-10 pr-4 py-2 bg-white border border-ink-200 text-sm text-ink-700 placeholder:text-ink-300 focus:outline-none focus:border-indigo-800 transition-colors rounded-xl"
                />
              </div>
            </div>

            {/* Right actions */}
            <div className="ml-auto flex items-center gap-3">
              <NotificationPopover />
              <img
                src={user?.avatarUrl ?? 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangming&backgroundColor=1e1b4b'}
                alt="头像"
                onError={(e) => {
                  const img = e.currentTarget;
                  img.onerror = null;
                  img.src = FALLBACK_AVATAR;
                }}
                className="w-9 h-9 rounded-full bg-indigo-100 object-cover cursor-pointer ring-2 ring-transparent hover:ring-amber-400 transition-all"
              />
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 p-6 lg:p-8 overflow-x-hidden">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
