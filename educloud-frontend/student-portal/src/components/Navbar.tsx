import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  GraduationCap, Search, Bell, ShoppingCart, Menu, X, User,
  BookOpen, Video, FileText, ClipboardList, LogOut,
} from 'lucide-react';
import { useAuthStore } from '../stores/useAuthStore';
import { useNotificationStore } from '../features/engagement/useNotificationStore';
import { cn } from '../utils/cn';

const navLinks = [
  { to: '/', label: '首页', end: true },
  { to: '/courses', label: '课程中心' },
  { to: '/my-courses', label: '我的课程' },
  { to: '/live/1', label: '直播课堂' },
  { to: '/assignments', label: '作业考试' },
];

export default function Navbar() {
  const navigate = useNavigate();
  const { token, logout } = useAuthStore();
  const unreadCount = useNotificationStore((state) =>
    state.notifications.reduce((count, notification) => count + Number(!notification.read), 0),
  );
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState('');

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (search.trim()) {
      navigate(`/courses?keyword=${encodeURIComponent(search.trim())}`);
      setMenuOpen(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/');
    setMenuOpen(false);
  };

  return (
    <header className="sticky top-0 z-50 bg-paper/85 backdrop-blur-md border-b border-ink-100">
      <div className="max-w-7xl mx-auto px-4 md:px-8">
        <div className="flex items-center gap-6 h-16">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 shrink-0">
            <div className="w-9 h-9 bg-indigo-800 flex items-center justify-center">
              <GraduationCap size={20} className="text-paper" />
            </div>
            <div className="hidden sm:block">
              <div className="font-display text-lg font-700 text-ink-900 leading-none">EduCloud</div>
              <div className="text-[10px] text-ink-400 tracking-widest uppercase mt-0.5">Education Cloud</div>
            </div>
          </Link>

          {/* Desktop nav */}
          <nav className="hidden lg:flex items-center gap-7">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) => (isActive ? 'nav-link nav-link-active' : 'nav-link')}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          {/* Search */}
          <form onSubmit={handleSearch} className="hidden md:flex relative flex-1 max-w-xs ml-auto">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索课程..."
              className="w-full pl-9 pr-4 py-2 text-sm bg-white border border-ink-200 focus:border-indigo-800 focus:ring-1 focus:ring-indigo-800 focus:outline-none transition-all"
            />
          </form>

          {/* Right actions */}
          <div className="flex items-center gap-1 ml-auto md:ml-0">
            {token ? (
              <>
                <Link
                  to="/notifications"
                  aria-label="通知中心"
                  className="hidden sm:flex relative p-2 text-ink-500 hover:text-indigo-800 transition-colors"
                >
                  <Bell size={19} />
                  {unreadCount > 0 && (
                    <span className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-amber-600 px-1 text-center text-[10px] font-semibold leading-4 text-white">
                      {unreadCount > 9 ? '9+' : unreadCount}
                    </span>
                  )}
                </Link>
                <Link to="/orders" className="hidden sm:flex p-2 text-ink-500 hover:text-indigo-800 transition-colors">
                  <ShoppingCart size={19} />
                </Link>
                <Link to="/profile" className="flex items-center gap-2 ml-1 p-1 hover:bg-ink-50 transition-colors">
                  <img
                    src="https://api.dicebear.com/7.x/initials/svg?seed=林晓&backgroundColor=1e1b4b&textColor=ffffff"
                    alt="用户头像"
                    className="w-8 h-8 bg-indigo-100 border border-ink-200"
                  />
                </Link>
              </>
            ) : (
              <Link to="/login" className="btn-primary py-2 px-5 text-sm hidden sm:inline-flex">
                登录
              </Link>
            )}
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="lg:hidden p-2 text-ink-600 hover:text-indigo-800 ml-1"
            >
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="lg:hidden border-t border-ink-100 bg-white animate-fade-in">
          <div className="px-4 py-4 space-y-1">
            <form onSubmit={handleSearch} className="relative mb-3 md:hidden">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="搜索课程..."
                className="w-full pl-9 pr-4 py-2.5 text-sm bg-ink-50 border border-ink-200 focus:border-indigo-800 focus:outline-none"
              />
            </form>
            {[
              { to: '/', label: '首页', icon: GraduationCap, end: true },
              { to: '/courses', label: '课程中心', icon: BookOpen },
              { to: '/my-courses', label: '我的课程', icon: BookOpen },
              { to: '/live/1', label: '直播课堂', icon: Video },
              { to: '/assignments', label: '作业', icon: FileText },
              { to: '/exams', label: '考试中心', icon: ClipboardList },
            ].map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 px-3 py-2.5 text-sm rounded-none transition-colors',
                    isActive ? 'bg-indigo-50 text-indigo-800 font-medium' : 'text-ink-600 hover:bg-ink-50',
                  )
                }
              >
                <item.icon size={18} />
                {item.label}
              </NavLink>
            ))}
            <div className="pt-2 border-t border-ink-100 mt-2">
              {token ? (
                <>
                  <NavLink to="/notifications" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-3 py-2.5 text-sm text-ink-600 hover:bg-ink-50">
                    <Bell size={18} /> 通知中心
                    {unreadCount > 0 && <span className="ml-auto text-xs text-amber-700">{unreadCount}</span>}
                  </NavLink>
                  <NavLink to="/profile" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-3 py-2.5 text-sm text-ink-600 hover:bg-ink-50">
                    <User size={18} /> 个人中心
                  </NavLink>
                  <NavLink to="/orders" onClick={() => setMenuOpen(false)} className="flex items-center gap-3 px-3 py-2.5 text-sm text-ink-600 hover:bg-ink-50">
                    <ShoppingCart size={18} /> 我的订单
                  </NavLink>
                  <button onClick={handleLogout} className="flex items-center gap-3 px-3 py-2.5 text-sm text-red-600 hover:bg-red-50 w-full">
                    <LogOut size={18} /> 退出登录
                  </button>
                </>
              ) : (
                <Link to="/login" onClick={() => setMenuOpen(false)} className="btn-primary w-full mt-2">
                  登录 / 注册
                </Link>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
