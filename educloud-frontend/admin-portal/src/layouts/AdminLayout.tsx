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
} from 'lucide-react';
import { useAuthStore } from '../stores/useAuthStore';
import { cn } from '../utils/cn';

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
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const sidebar = (
    <aside className="flex flex-col h-full w-64 bg-white border-r border-ink-100">
      {/* Logo */}
      <div className="flex items-center gap-3 px-6 py-6 border-b border-ink-100">
        <div className="w-10 h-10 bg-indigo-800 flex items-center justify-center shrink-0">
          <GraduationCap size={22} className="text-paper" />
        </div>
        <div>
          <div className="font-display text-lg font-700 text-ink-900 leading-tight">
            EduCloud
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-semibold uppercase tracking-widest text-amber-600 bg-amber-50 px-1.5 py-0.5 border border-amber-200">
              Admin
            </span>
            <span className="text-xs text-ink-400">管理后台</span>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 py-4 overflow-y-auto">
        <div className="px-4 mb-2 text-[10px] font-semibold uppercase tracking-widest text-ink-300">
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
      <div className="border-t border-ink-100 p-4">
        <div className="flex items-center gap-3 mb-3">
          <img
            src={admin?.avatar}
            alt={admin?.realName}
            className="w-9 h-9 bg-indigo-100 border border-ink-200"
          />
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-ink-800 truncate">
              {admin?.realName ?? '管理员'}
            </div>
            <div className="text-xs text-ink-400 truncate">{admin?.email}</div>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="w-full inline-flex items-center justify-center gap-2 px-3 py-2 text-sm text-ink-500 border border-ink-200 hover:border-red-300 hover:text-red-600 transition-colors"
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
            className="absolute inset-0 bg-indigo-800/50"
            onClick={() => setSidebarOpen(false)}
          />
          <div className="relative h-full animate-fade-in">{sidebar}</div>
        </div>
      )}

      {/* Main */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar */}
        <header className="flex items-center gap-4 px-4 md:px-8 h-16 bg-white/80 backdrop-blur border-b border-ink-100 shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="lg:hidden p-2 text-ink-600 hover:text-indigo-800"
          >
            <Menu size={20} />
          </button>

          {/* Search */}
          <div className="relative flex-1 max-w-md">
            <Search
              size={16}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400"
            />
            <input
              type="text"
              placeholder="搜索用户、课程、订单..."
              className="w-full pl-9 pr-4 py-2 text-sm bg-ink-50 border border-transparent focus:bg-white focus:border-indigo-800 focus:ring-1 focus:ring-indigo-800 focus:outline-none transition-all"
            />
          </div>

          <div className="flex items-center gap-2 ml-auto">
            <button className="relative p-2 text-ink-500 hover:text-indigo-800 transition-colors">
              <Bell size={18} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-amber-600 rounded-full" />
            </button>
            <div className="hidden sm:flex items-center gap-2 pl-3 ml-1 border-l border-ink-200">
              <img
                src={admin?.avatar}
                alt={admin?.realName}
                className="w-8 h-8 bg-indigo-100 border border-ink-200"
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
          <div className="p-4 md:p-8 max-w-[1400px] mx-auto">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Mobile close button when sidebar open */}
      {sidebarOpen && (
        <button
          onClick={() => setSidebarOpen(false)}
          className="fixed top-4 right-4 z-50 lg:hidden p-2 bg-white text-ink-800 shadow-lg"
        >
          <X size={20} />
        </button>
      )}
    </div>
  );
}
