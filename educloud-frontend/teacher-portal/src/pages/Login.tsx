import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BookOpen, Mail, Lock, ArrowRight, Eye, EyeOff } from 'lucide-react';
import { useAuthStore } from '../stores/useAuthStore';

export default function Login() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const loading = useAuthStore((s) => s.loading);

  const [email, setEmail] = useState('zhangming@educloud.cn');
  const [password, setPassword] = useState('password');
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      await login(email, password);
      navigate('/');
    } catch {
      setError('登录失败，请检查邮箱与密码');
    }
  };

  return (
    <div className="min-h-screen flex">
      {/* Left — editorial indigo panel */}
      <div className="hidden lg:flex lg:w-1/2 bg-indigo-800 relative overflow-hidden">
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-20 left-20 w-72 h-72 border border-amber-400/30 rounded-full" />
          <div className="absolute bottom-20 right-20 w-96 h-96 border border-amber-400/20 rounded-full" />
          <div className="absolute top-1/2 left-1/3 w-48 h-48 border border-white/10 rounded-full" />
        </div>
        <div className="relative z-10 flex flex-col justify-between p-12 xl:p-16 text-paper w-full">
          {/* Logo */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-amber-500 flex items-center justify-center">
              <BookOpen className="w-5 h-5 text-indigo-900" strokeWidth={2.5} />
            </div>
            <div>
              <h1 className="font-display text-xl font-bold leading-none">EduCloud</h1>
              <p className="text-[10px] text-indigo-300 uppercase tracking-widest mt-1">教师工作台</p>
            </div>
          </div>

          {/* Center quote */}
          <div className="space-y-6">
            <p className="font-display text-6xl xl:text-7xl font-black text-amber-400 leading-none">
              传道
            </p>
            <p className="font-display text-6xl xl:text-7xl font-black text-paper leading-none">
              授业
            </p>
            <div className="w-16 h-px bg-amber-400" />
            <p className="text-indigo-200 text-lg leading-relaxed max-w-md">
              师者，所以传道授业解惑也。<br />
              EduCloud 教师端，为每一位教育者提供专业的在线教学工具。
            </p>
          </div>

          {/* Footer */}
          <p className="text-indigo-400 text-xs">
            © 2026 EduCloud · 让优质教育触手可及
          </p>
        </div>
      </div>

      {/* Right — login form */}
      <div className="flex-1 flex items-center justify-center p-8 bg-paper">
        <div className="w-full max-w-md space-y-8 animate-fade-up">
          {/* Mobile logo */}
          <div className="lg:hidden flex items-center gap-3 justify-center">
            <div className="w-10 h-10 bg-indigo-800 flex items-center justify-center">
              <BookOpen className="w-5 h-5 text-amber-400" />
            </div>
            <h1 className="font-display text-2xl font-bold text-ink-900">EduCloud 教师端</h1>
          </div>

          <div>
            <p className="section-label mb-3">欢迎登录</p>
            <h2 className="display-heading text-3xl mb-2">教师登录</h2>
            <p className="text-ink-500 text-sm">请使用您的 EduCloud 教师账号登录工作台</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm">
                {error}
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">邮箱地址</label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-300" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="input-field pl-11"
                  placeholder="teacher@educloud.cn"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">登录密码</label>
              <div className="relative">
                <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-300" />
                <input
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input-field pl-11 pr-11"
                  placeholder="请输入密码"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPwd(!showPwd)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-ink-300 hover:text-ink-600"
                >
                  {showPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-sm">
              <label className="flex items-center gap-2 text-ink-600 cursor-pointer">
                <input type="checkbox" className="rounded border-ink-300 text-indigo-800 focus:ring-indigo-800" />
                记住我
              </label>
              <a href="#" className="link-underline">忘记密码？</a>
            </div>

            <button type="submit" disabled={loading} className="btn-primary w-full">
              {loading ? '登录中…' : '登录工作台'}
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          <p className="text-center text-xs text-ink-400">
            演示账号已预填，直接点击登录即可体验
          </p>
        </div>
      </div>
    </div>
  );
}
