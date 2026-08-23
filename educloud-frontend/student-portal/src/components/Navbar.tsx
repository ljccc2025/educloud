import { useState, useEffect, useRef } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  GraduationCap, Search, Bell, ShoppingCart, Menu, X, User,
  BookOpen, Video, FileText, ClipboardList, LogOut, Sparkles, MessageCircle,
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
  { to: '/ai-assistant', label: 'AI 助教' },
  { to: '/community', label: '学习社区' },
];

const SCROLL_THRESHOLD = 8;

// M04 审查修复：presigned 头像 URL 5 分钟过期后破图，onError 兜底回退占位头像。
const FALLBACK_AVATAR =
  'https://api.dicebear.com/7.x/initials/svg?seed=educloud&backgroundColor=1e1b4b&textColor=ffffff&fontWeight=500&fontSize=24';

export default function Navbar() {
  const navigate = useNavigate();
  const { token, logout, user } = useAuthStore();
  const unreadCount = useNotificationStore((state) =>
    state.notifications.reduce((count, notification) => count + Number(!notification.read), 0),
  );
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [scrolled, setScrolled] = useState(false);
  const rafId = useRef<number>(0);

  useEffect(() => {
    const onScroll = () => {
      if (rafId.current) return;
      rafId.current = requestAnimationFrame(() => {
        rafId.current = 0;
        setScrolled(window.scrollY > SCROLL_THRESHOLD);
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    return () => {
      window.removeEventListener('scroll', onScroll);
      if (rafId.current) {
        cancelAnimationFrame(rafId.current);
        rafId.current = 0;
      }
    };
  }, []);

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
    <header
      data-site-header
      className={cn(
        'sticky top-0 z-50 w-full',
        // Pill state: outer padding to float the capsule
        // Expanded state: no outer padding
        'transition-[padding] duration-[520ms] ease-[cubic-bezier(0.4,0,0.2,1)] motion-reduce:transition-none',
        scrolled ? 'px-0 py-0' : 'px-4 md:px-6 pt-3 pb-0',
      )}
    >
      <div
        data-navbar-surface
        className={cn(
          // Base layout
          'relative mx-auto w-full overflow-hidden',
          'transition-[max-width,border-radius,height,background-color,box-shadow,padding] duration-[520ms] ease-[cubic-bezier(0.4,0,0.2,1)] motion-reduce:transition-none',
          // Pill state (top)
          !scrolled && [
            'max-w-[80rem] rounded-[28px]',
            'bg-paper/55 dark:bg-ink-900/55 backdrop-blur-2xl',
            'border border-white/75 dark:border-white/10',
            'shadow-lg shadow-ink-900/[0.06] dark:shadow-black/20',
            'h-14',
          ],
          // Expanded state (scrolled)
          scrolled && [
            'max-w-[100vw] rounded-[0px]',
            'bg-paper/90 dark:bg-ink-900/90 backdrop-blur-xl',
            'border-b border-ink-200/70 dark:border-ink-700/60',
            'shadow-md shadow-ink-900/[0.05] dark:shadow-black/15',
            'h-16',
          ],
        )}
      >
        {/* Inner content — keep layout stable while height and horizontal padding change */}
        <div
          className={cn(
            'flex items-center gap-4 lg:gap-6 h-full w-full',
            'transition-[padding] duration-[520ms] ease-[cubic-bezier(0.4,0,0.2,1)] motion-reduce:transition-none',
            scrolled ? 'px-4 md:px-8' : 'px-5 md:px-7',
          )}
        >
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 shrink-0">
            <div
              className={cn(
                'bg-indigo-800 flex items-center justify-center text-paper shrink-0',
                'transition-all duration-[520ms] ease-[cubic-bezier(0.4,0,0.2,1)] motion-reduce:transition-none',
                scrolled ? 'w-9 h-9' : 'w-8 h-8 rounded-xl',
              )}
            >
              <GraduationCap size={scrolled ? 20 : 18} />
            </div>
            <div className="hidden sm:block">
              <div className="font-display text-lg font-bold text-ink-900 dark:text-paper leading-none">
                EduCloud
              </div>
              <div className="text-[10px] text-ink-400 dark:text-ink-500 tracking-widest uppercase mt-0.5">
                Education Cloud
              </div>
            </div>
          </Link>

          {/* Desktop nav */}
          <nav className="hidden lg:flex items-center gap-1 xl:gap-3">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  isActive
                    ? 'nav-link nav-link-active whitespace-nowrap px-3 py-1.5'
                    : 'nav-link whitespace-nowrap px-3 py-1.5'
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          {/* Search */}
          <form onSubmit={handleSearch} className="hidden xl:flex relative flex-1 max-w-[15rem] ml-auto">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400 dark:text-ink-500" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索课程..."
              className="w-full pl-9 pr-4 py-2 text-sm bg-white/80 dark:bg-ink-800/60 border border-ink-200 dark:border-ink-700 text-ink-800 dark:text-ink-100 placeholder:text-ink-400 dark:placeholder:text-ink-500 rounded-full focus:border-indigo-800 dark:focus:border-indigo-400 focus:ring-1 focus:ring-indigo-800/20 dark:focus:ring-indigo-400/20 focus:outline-none transition-all"
            />
          </form>

          {/* Right actions */}
          <div className="flex items-center gap-1 ml-auto md:ml-0">
            {token ? (
              <>
                <Link
                  to="/notifications"
                  aria-label="通知中心"
                  className="hidden sm:flex relative p-2 text-ink-500 dark:text-ink-400 hover:text-indigo-800 dark:hover:text-indigo-300 rounded-full hover:bg-ink-100/60 dark:hover:bg-ink-800/60 transition-colors"
                >
                  <Bell size={19} />
                  {unreadCount > 0 && (
                    <span className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-amber-600 px-1 text-center text-[10px] font-semibold leading-4 text-white">
                      {unreadCount > 9 ? '9+' : unreadCount}
                    </span>
                  )}
                </Link>
                <Link
                  to="/orders"
                  aria-label="购物车"
                  className="hidden sm:flex p-2 text-ink-500 dark:text-ink-400 hover:text-indigo-800 dark:hover:text-indigo-300 rounded-full hover:bg-ink-100/60 dark:hover:bg-ink-800/60 transition-colors"
                >
                  <ShoppingCart size={19} />
                </Link>
                <Link
                  to="/profile"
                  aria-label="个人中心"
                  className="flex items-center gap-2 ml-1 p-0.5 rounded-full hover:bg-ink-100/60 dark:hover:bg-ink-800/60 transition-colors"
                >
                  <img
                    src={user?.avatarUrl ?? user?.avatar ?? ''}
                    alt="用户头像"
                    onError={(e) => {
                      const img = e.currentTarget;
                      img.onerror = null;
                      img.src = FALLBACK_AVATAR;
                    }}
                    className="block h-8 w-8 rounded-full bg-indigo-100 border border-ink-200 dark:border-ink-700 object-cover"
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
              className="lg:hidden p-2 text-ink-600 dark:text-ink-300 hover:text-indigo-800 dark:hover:text-indigo-300 ml-1 rounded-full hover:bg-ink-100/60 dark:hover:bg-ink-800/60 transition-colors"
            >
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="lg:hidden mt-2 mx-4 rounded-2xl border border-ink-100 dark:border-ink-800 bg-white dark:bg-ink-900 shadow-xl shadow-ink-900/10 dark:shadow-black/30 overflow-hidden animate-fade-in">
          <div className="px-4 py-4 space-y-1">
            <form onSubmit={handleSearch} className="relative mb-3 md:hidden">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="搜索课程..."
                className="w-full pl-9 pr-4 py-2.5 text-sm bg-ink-50 dark:bg-ink-800 border border-ink-200 dark:border-ink-700 text-ink-800 dark:text-ink-100 rounded-xl focus:border-indigo-800 focus:outline-none"
              />
            </form>
            {[
              { to: '/', label: '首页', icon: GraduationCap, end: true },
              { to: '/courses', label: '课程中心', icon: BookOpen },
              { to: '/my-courses', label: '我的课程', icon: BookOpen },
              { to: '/live/1', label: '直播课堂', icon: Video },
              { to: '/assignments', label: '作业', icon: FileText },
              { to: '/exams', label: '考试中心', icon: ClipboardList },
              { to: '/ai-assistant', label: 'AI 助教', icon: Sparkles },
              { to: '/community', label: '学习社区', icon: MessageCircle },
            ].map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => setMenuOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 px-3 py-2.5 text-sm rounded-xl transition-colors',
                    isActive
                      ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-300 font-medium'
                      : 'text-ink-600 dark:text-ink-300 hover:bg-ink-50 dark:hover:bg-ink-800',
                  )
                }
              >
                <item.icon size={18} />
                {item.label}
              </NavLink>
            ))}
            <div className="pt-2 border-t border-ink-100 dark:border-ink-800 mt-2">
              {token ? (
                <>
                  <NavLink
                    to="/notifications"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2.5 text-sm text-ink-600 dark:text-ink-300 hover:bg-ink-50 dark:hover:bg-ink-800 rounded-xl transition-colors"
                  >
                    <Bell size={18} /> 通知中心
                    {unreadCount > 0 && <span className="ml-auto text-xs text-amber-700 dark:text-amber-400">{unreadCount}</span>}
                  </NavLink>
                  <NavLink
                    to="/profile"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2.5 text-sm text-ink-600 dark:text-ink-300 hover:bg-ink-50 dark:hover:bg-ink-800 rounded-xl transition-colors"
                  >
                    <User size={18} /> 个人中心
                  </NavLink>
                  <NavLink
                    to="/orders"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2.5 text-sm text-ink-600 dark:text-ink-300 hover:bg-ink-50 dark:hover:bg-ink-800 rounded-xl transition-colors"
                  >
                    <ShoppingCart size={18} /> 我的订单
                  </NavLink>
                  <button
                    onClick={handleLogout}
                    className="flex items-center gap-3 px-3 py-2.5 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 w-full rounded-xl transition-colors"
                  >
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
