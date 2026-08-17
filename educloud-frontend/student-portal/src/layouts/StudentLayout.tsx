import { useState, useRef, useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { Search, ChevronDown, User, BookOpen, LogOut, GraduationCap, Menu, X } from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { cn } from '@/utils/cn';

const navLinks = [
  { to: '/', label: '首页' },
  { to: '/courses', label: '课程' },
  { to: '/my-courses', label: '我的课程' },
  { to: '/live/1', label: '直播' },
  { to: '/profile', label: '个人中心' },
];

export default function StudentLayout() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top Navbar */}
      <header className="sticky top-0 z-50 bg-paper/95 backdrop-blur-sm border-b border-ink-100">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            {/* Logo */}
            <NavLink to="/" className="flex items-center gap-2 flex-shrink-0">
              <GraduationCap size={28} className="text-indigo-800" strokeWidth={1.5} />
              <span className="font-display text-2xl font-bold text-indigo-800 tracking-tight">
                EduCloud
              </span>
            </NavLink>

            {/* Desktop Nav */}
            <nav className="hidden md:flex items-center gap-1">
              {navLinks.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  end={link.to === '/'}
                  className={({ isActive }) =>
                    cn(
                      'px-4 py-2 text-sm font-medium transition-colors duration-200 relative',
                      isActive
                        ? 'text-indigo-800'
                        : 'text-ink-500 hover:text-indigo-800'
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      {link.label}
                      {isActive && (
                        <span className="absolute bottom-0 left-1/2 -translate-x-1/2 w-6 h-0.5 bg-amber-600" />
                      )}
                    </>
                  )}
                </NavLink>
              ))}
            </nav>

            {/* Search + Avatar */}
            <div className="flex items-center gap-3">
              <div className="hidden lg:flex items-center relative">
                <Search size={16} className="absolute left-3 text-ink-300" />
                <input
                  type="text"
                  placeholder="搜索课程..."
                  className="w-48 pl-9 pr-4 py-2 text-sm bg-white border border-ink-200 text-ink-800 placeholder:text-ink-400 focus:outline-none focus:border-indigo-800 focus:w-64 transition-all duration-300"
                />
              </div>

              {/* Avatar Dropdown */}
              <div className="relative" ref={dropdownRef}>
                <button
                  type="button"
                  onClick={() => setDropdownOpen(!dropdownOpen)}
                  className="flex items-center gap-2 p-1 hover:bg-ink-50 transition-colors"
                >
                  <div className="w-9 h-9 bg-indigo-800 flex items-center justify-center text-paper font-medium text-sm">
                    {user?.realName?.charAt(0) ?? 'U'}
                  </div>
                  <ChevronDown
                    size={14}
                    className={cn(
                      'text-ink-400 transition-transform hidden sm:block',
                      dropdownOpen && 'rotate-180'
                    )}
                  />
                </button>

                {dropdownOpen && (
                  <div className="absolute right-0 top-full mt-2 w-56 bg-white border border-ink-100 shadow-xl shadow-ink-900/10 py-1 animate-fade-in">
                    <div className="px-4 py-3 border-b border-ink-100">
                      <p className="font-medium text-ink-900 text-sm">{user?.realName ?? '用户'}</p>
                      <p className="text-xs text-ink-400 mt-0.5">{user?.email ?? ''}</p>
                    </div>
                    <NavLink
                      to="/profile"
                      onClick={() => setDropdownOpen(false)}
                      className="flex items-center gap-3 px-4 py-2.5 text-sm text-ink-600 hover:bg-indigo-50 hover:text-indigo-800 transition-colors"
                    >
                      <User size={16} />
                      个人中心
                    </NavLink>
                    <NavLink
                      to="/my-courses"
                      onClick={() => setDropdownOpen(false)}
                      className="flex items-center gap-3 px-4 py-2.5 text-sm text-ink-600 hover:bg-indigo-50 hover:text-indigo-800 transition-colors"
                    >
                      <BookOpen size={16} />
                      我的课程
                    </NavLink>
                    <div className="border-t border-ink-100 my-1" />
                    <button
                      type="button"
                      onClick={handleLogout}
                      className="flex items-center gap-3 w-full px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
                    >
                      <LogOut size={16} />
                      退出登录
                    </button>
                  </div>
                )}
              </div>

              {/* Mobile menu toggle */}
              <button
                type="button"
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="md:hidden p-2 text-ink-600"
              >
                {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
              </button>
            </div>
          </div>
        </div>

        {/* Mobile menu */}
        {mobileMenuOpen && (
          <div className="md:hidden border-t border-ink-100 bg-white animate-fade-in">
            <nav className="px-4 py-3 space-y-1">
              {navLinks.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  end={link.to === '/'}
                  onClick={() => setMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'block px-3 py-2 text-sm font-medium rounded-none transition-colors',
                      isActive
                        ? 'text-indigo-800 bg-indigo-50 border-l-2 border-amber-600'
                        : 'text-ink-600 hover:bg-ink-50'
                    )
                  }
                >
                  {link.label}
                </NavLink>
              ))}
            </nav>
          </div>
        )}
      </header>

      {/* Main Content */}
      <main className="flex-1">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="bg-ink-900 text-ink-300 mt-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
            <div className="md:col-span-2">
              <div className="flex items-center gap-2 mb-4">
                <GraduationCap size={24} className="text-amber-500" strokeWidth={1.5} />
                <span className="font-display text-xl font-bold text-white">EduCloud</span>
              </div>
              <p className="text-sm text-ink-400 max-w-md leading-relaxed">
                EduCloud 致力于为每一位学习者提供高质量的在线教育资源，连接优秀的讲师与求知的学生，让学习无处不在。
              </p>
            </div>
            <div>
              <h4 className="text-white font-medium text-sm mb-3">学习</h4>
              <ul className="space-y-2 text-sm">
                <li><NavLink to="/courses" className="hover:text-amber-500 transition-colors">全部课程</NavLink></li>
                <li><NavLink to="/my-courses" className="hover:text-amber-500 transition-colors">我的课程</NavLink></li>
                <li><NavLink to="/assignments" className="hover:text-amber-500 transition-colors">作业中心</NavLink></li>
                <li><NavLink to="/exams" className="hover:text-amber-500 transition-colors">考试中心</NavLink></li>
              </ul>
            </div>
            <div>
              <h4 className="text-white font-medium text-sm mb-3">账户</h4>
              <ul className="space-y-2 text-sm">
                <li><NavLink to="/profile" className="hover:text-amber-500 transition-colors">个人中心</NavLink></li>
                <li><NavLink to="/orders" className="hover:text-amber-500 transition-colors">我的订单</NavLink></li>
                <li><button type="button" onClick={handleLogout} className="hover:text-amber-500 transition-colors">退出登录</button></li>
              </ul>
            </div>
          </div>
          <div className="border-t border-ink-800 mt-10 pt-6 flex flex-col sm:flex-row justify-between items-center gap-4">
            <p className="text-xs text-ink-500">© 2025 EduCloud. 保留所有权利。</p>
            <p className="text-xs text-ink-500">用心做教育，让知识触手可及</p>
          </div>
        </div>
      </footer>
    </div>
  );
}
