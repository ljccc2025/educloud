import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  ClipboardCheck,
  FileCheck2,
  ShoppingBag,
  Wallet,
  Settings,
  ScrollText,
  Search,
  Bell,
  LogOut,
  Menu,
  X,
  GraduationCap,
  Sun,
  Moon,
} from 'lucide-react';
import { useAuthStore } from '../stores/useAuthStore';
import { useThemeStore } from '../stores/useThemeStore';

const navItems = [
  { to: '/', label: '数据看板', icon: LayoutDashboard, end: true },
  { to: '/users', label: '用户管理', icon: Users },
  { to: '/course-audit', label: '课程审核', icon: ClipboardCheck },
  { to: '/content-audit', label: '内容审核', icon: FileCheck2 },
  { to: '/orders', label: '订单管理', icon: ShoppingBag },
  { to: '/finance', label: '财务管理', icon: Wallet },
  { to: '/config', label: '系统配置', icon: Settings },
  { to: '/logs', label: '操作日志', icon: ScrollText },
];

export default function AdminLayout() {
  const { admin, logout } = useAuthStore();
  const { theme, toggleTheme } = useThemeStore();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const sidebar = (
    <aside className="flex flex-col h-full w-64 bg-surface-dark border-r border-ink-200/60">
      {/* Logo */}
      <div className="flex items-center gap-3 px-5 py-5 border-b border-ink-200/60">
        <div className="w-10 h-10 bg-gradient-to-br from-brand-500 to-brand-700 rounded-xl flex items-center justify-center shrink-0 shadow-glow-purple">
          <GraduationCap size={22} className="text-white" />
        </div>
        <div>
          <div className="font-sans text-lg font-bold text-ink-900 leading-tight">
            EduCloud
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-semibold uppercase tracking-widest text-brand-500 dark:text-brand-400 bg-brand-500/15 px-1.5 py-0.5 rounded-md border border-brand-500/20">
              Admin
            </span>
            <span className="text-xs text-ink-600 dark:text-ink-400">管理后台</span>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 py-4 overflow-y-auto">
        <div className="px-5 mb-2 text-[10px] font-semibold uppercase tracking-widest text-ink-500 dark:text-ink-500">
          管理菜单
        </div>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={() => setSidebarOpen(false)}
            className={({ isActive }) => (isActive ? 'nav-item-active' : 'nav-item')}
          >
            <item.icon size={18} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* User */}
      <div className="border-t border-ink-200/60 p-4">
        <div className="flex items-center gap-3 mb-3 px-1">
          <img
            src={admin?.avatarUrl ?? admin?.avatar}
            alt={admin?.realName}
            className="w-9 h-9 rounded-full bg-ink-100 border border-ink-300 object-cover"
          />
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-ink-800 truncate">
              {admin?.realName ?? '管理员'}
            </div>
            <div className="text-xs text-ink-500 dark:text-ink-400 truncate">{admin?.email}</div>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="w-full inline-flex items-center justify-center gap-2 px-3 py-2 text-sm text-ink-500 border border-ink-300 rounded-xl hover:border-red-500/50 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-500/10 transition-all"
        >
          <LogOut size={14} />
          退出登录
        </button>
      </div>
    </aside>
  );

  return (
    <div className="flex h-screen bg-paper">
      {/* Desktop sidebar */}
      <div className="hidden lg:flex shrink-0">{sidebar}</div>

      {/* Mobile sidebar overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div
            className="absolute inset-0 bg-black/50 backdrop-blur-sm"
            onClick={() => setSidebarOpen(false)}
          />
          <div className="relative h-full animate-fade-in">{sidebar}</div>
        </div>
      )}

      {/* Main */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar */}
        <header className="flex items-center gap-4 px-4 md:px-6 h-16 bg-surface/80 backdrop-blur-xl border-b border-ink-200/60 shrink-0 sticky top-0 z-30">
          <button
            onClick={() => setSidebarOpen(true)}
            className="lg:hidden p-2 text-ink-600 hover:text-brand-500 dark:hover:text-brand-400 rounded-lg hover:bg-ink-500/10"
          >
            <Menu size={20} />
          </button>

          {/* Search */}
          <div className="relative flex-1 max-w-md">
            <Search
              size={16}
              className="absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-500"
            />
            <input
              type="text"
              placeholder="搜索用户、课程、订单..."
              className="w-full pl-10 pr-4 py-2 text-sm bg-ink-100 border border-ink-300 rounded-xl text-ink-800 placeholder:text-ink-500 focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 transition-all"
            />
          </div>

          <div className="flex items-center gap-1 ml-auto">
            {/* Theme toggle */}
            <button
              onClick={toggleTheme}
              title={theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'}
              className="relative p-2.5 text-ink-500 hover:text-brand-500 dark:hover:text-brand-400 rounded-xl hover:bg-ink-500/10 transition-all"
            >
              {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
            </button>

            <button className="relative p-2.5 text-ink-500 hover:text-brand-500 dark:hover:text-brand-400 rounded-xl hover:bg-ink-500/10 transition-all">
              <Bell size={18} />
              <span className="absolute top-2 right-2 w-2 h-2 bg-brand-500 rounded-full shadow-glow-purple" />
            </button>
            <div className="hidden sm:flex items-center gap-2.5 pl-3 ml-1 border-l border-ink-300">
              <img
                src={admin?.avatarUrl ?? admin?.avatar}
                alt={admin?.realName}
                className="w-8 h-8 rounded-full bg-ink-100 border border-ink-300 object-cover"
              />
              <div className="text-sm">
                <span className="font-medium text-ink-800">
                  {admin?.realName ?? '管理员'}
                </span>
              </div>
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto">
          <div className="p-4 md:p-6 lg:p-8 max-w-[1400px] mx-auto">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Mobile close button when sidebar open */}
      {sidebarOpen && (
        <button
          onClick={() => setSidebarOpen(false)}
          className="fixed top-4 right-4 z-50 lg:hidden p-2 bg-surface text-ink-800 rounded-xl border border-ink-300 shadow-lg"
        >
          <X size={20} />
        </button>
      )}
    </div>
  );
}
