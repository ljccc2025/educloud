import { useState } from 'react';
import { useNavigate, Navigate, useSearchParams } from 'react-router-dom';
import { Mail, Lock, Eye, EyeOff, GraduationCap, Loader2 } from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { getSafeInternalRedirect } from '@/utils/checkoutSession';

export default function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, token } = useAuthStore();
  const [email, setEmail] = useState('limingxuan@educloud.com');
  const [password, setPassword] = useState('123456');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const redirectTo = getSafeInternalRedirect(searchParams.get('redirect'));

  if (token) {
    return <Navigate to={redirectTo} replace />;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const success = await login(email, password);
      if (success) {
        navigate(redirectTo, { replace: true });
      } else {
        setError('登录失败，请检查账号和密码');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex">
      {/* Left Side - Branding */}
      <div className="hidden lg:flex lg:w-1/2 bg-indigo-800 relative overflow-hidden">
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              'linear-gradient(rgba(255,255,255,0.3) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.3) 1px, transparent 1px)',
            backgroundSize: '50px 50px',
          }}
        />
        <div className="absolute top-20 right-16 section-number !text-white/5 !text-[14rem] select-none">
          01
        </div>
        <div className="relative z-10 flex flex-col justify-between p-12 xl:p-16 w-full">
          <div className="flex items-center gap-3">
            <GraduationCap size={36} className="text-amber-500" strokeWidth={1.5} />
            <span className="font-display text-3xl font-bold text-white">EduCloud</span>
          </div>

          <div className="max-w-lg">
            <blockquote className="font-display text-3xl xl:text-4xl font-bold text-white leading-snug italic">
              "教育不是注满一桶水，而是点燃一把火。"
            </blockquote>
            <p className="text-indigo-200 mt-6 text-lg leading-relaxed">
              在 EduCloud，我们相信学习的力量。汇聚顶尖讲师与优质课程，让每一位求知者都能找到属于自己的成长之路。
            </p>
            <div className="flex items-center gap-4 mt-10">
              <div className="flex -space-x-2">
                {['李', '王', '张', '陈'].map((char, i) => (
                  <div
                    key={i}
                    className="w-10 h-10 bg-white/10 border-2 border-indigo-800 flex items-center justify-center text-white text-sm font-medium"
                  >
                    {char}
                  </div>
                ))}
              </div>
              <div>
                <p className="text-white font-medium text-sm">50,000+ 学员</p>
                <p className="text-indigo-300 text-xs">正在这里学习</p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-6 text-indigo-300 text-xs">
            <span>© 2025 EduCloud</span>
            <span>用心做教育</span>
            <span>让知识触手可及</span>
          </div>
        </div>
      </div>

      {/* Right Side - Login Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 bg-paper">
        <div className="w-full max-w-md">
          <div className="lg:hidden flex items-center gap-2 mb-10">
            <GraduationCap size={28} className="text-indigo-800" strokeWidth={1.5} />
            <span className="font-display text-2xl font-bold text-indigo-800">EduCloud</span>
          </div>

          <span className="section-label mb-4">欢迎回来</span>
          <h1 className="display-heading text-4xl mt-4 mb-2">登录账户</h1>
          <p className="text-ink-500 mb-8">输入你的邮箱和密码，继续学习之旅</p>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">邮箱地址</label>
              <div className="relative">
                <Mail size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="your@email.com"
                  required
                  className="input-field pl-11"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">密码</label>
              <div className="relative">
                <Lock size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入密码"
                  required
                  minLength={6}
                  className="input-field pl-11 pr-12"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors"
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-sm">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" className="w-4 h-4 accent-indigo-800" />
                <span className="text-ink-600">记住我</span>
              </label>
              <button type="button" className="text-indigo-800 link-underline">
                忘记密码？
              </button>
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3.5 disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {loading ? (
                <><Loader2 size={16} className="animate-spin" /> 登录中...</>
              ) : (
                '登录'
              )}
            </button>
          </form>

          <div className="mt-8 text-center text-sm text-ink-500">
            还没有账户？
            <button type="button" className="text-indigo-800 font-medium link-underline ml-1">
              立即注册
            </button>
          </div>

          <p className="mt-8 text-xs text-ink-300 text-center">
            演示账号已预填，直接点击登录即可体验
          </p>
        </div>
      </div>
    </div>
  );
}
