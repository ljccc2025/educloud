import { useState } from 'react';
import { useNavigate, Navigate, useSearchParams } from 'react-router-dom';
import { Mail, Lock, Eye, EyeOff, GraduationCap, Loader2, User, Phone } from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { authApi } from '@/services/api';
import { getSafeInternalRedirect } from '@/utils/checkoutSession';

export default function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, token } = useAuthStore();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [username, setUsername] = useState('');
  const [phone, setPhone] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const redirectTo = getSafeInternalRedirect(searchParams.get('redirect'));

  if (token) {
    return <Navigate to={redirectTo} replace />;
  }

  const switchMode = (next: 'login' | 'register') => {
    setMode(next);
    setError('');
    setNotice('');
    // 切换 Tab 时清空注册专属字段，避免残留上一次的敏感输入。
    setUsername('');
    setPhone('');
    setDisplayName('');
    setConfirmPassword('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setNotice('');
    setLoading(true);
    try {
      if (mode === 'register') {
        if (password !== confirmPassword) {
          setError('两次输入的密码不一致');
          return;
        }
        // HTML pattern 在 React 的 v 模式下对连字符解析有兼容问题，改用 JS 校验（与后端规则一致）。
        if (!/^[A-Za-z0-9_.-]+$/.test(username)) {
          setError('用户名只能包含字母、数字、下划线、点和连字符');
          return;
        }
        if (!/^[0-9+ -]{5,32}$/.test(phone)) {
          setError('手机号格式不正确');
          return;
        }
        await authApi.register({
          username,
          password,
          email,
          phone,
          displayName: displayName || username,
        });
        setMode('login');
        setEmail(username);
        setUsername('');
        setPhone('');
        setDisplayName('');
        setPassword('');
        setConfirmPassword('');
        setNotice('注册成功，请使用新账号登录');
        return;
      }
      const success = await login(email, password);
      if (success) {
        navigate(redirectTo, { replace: true });
      } else {
        setError(useAuthStore.getState().error ?? '登录失败，请重试');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败，请重试');
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

      {/* Right Side - Auth Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 bg-paper">
        <div className="w-full max-w-md">
          <div className="lg:hidden flex items-center gap-2 mb-10">
            <GraduationCap size={28} className="text-indigo-800" strokeWidth={1.5} />
            <span className="font-display text-2xl font-bold text-indigo-800">EduCloud</span>
          </div>

          <span className="section-label mb-4">
            {mode === 'login' ? '欢迎回来' : '加入我们'}
          </span>
          <div className="flex gap-4 mb-6 border-b border-ink-100">
            <button
              type="button"
              onClick={() => switchMode('login')}
              className={`pb-2 text-sm font-medium transition-colors ${mode === 'login' ? 'text-indigo-800 border-b-2 border-indigo-800' : 'text-ink-400 hover:text-ink-600'}`}
            >
              登录
            </button>
            <button
              type="button"
              onClick={() => switchMode('register')}
              className={`pb-2 text-sm font-medium transition-colors ${mode === 'register' ? 'text-indigo-800 border-b-2 border-indigo-800' : 'text-ink-400 hover:text-ink-600'}`}
            >
              注册
            </button>
          </div>
          <h1 className="display-heading text-4xl mt-4 mb-2">
            {mode === 'login' ? '登录账户' : '注册新账号'}
          </h1>
          <p className="text-ink-500 mb-8">
            {mode === 'login'
              ? '输入你的邮箱（或用户名）和密码，继续学习之旅'
              : '填写以下信息，开启学习之旅'}
          </p>

          <form onSubmit={handleSubmit} className="space-y-5">
            {mode === 'register' && (
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">用户名</label>
                <div className="relative">
                  <User size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="设置用户名（字母、数字、下划线、点）"
                    required
                    minLength={3}
                    maxLength={32}
                    className="input-field pl-11"
                  />
                </div>
              </div>
            )}

            {mode === 'register' && (
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">手机号</label>
                <div className="relative">
                  <Phone size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="手机号（用于找回账号）"
                    required
                    className="input-field pl-11"
                  />
                </div>
              </div>
            )}

            {mode === 'register' && (
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">
                  昵称 <span className="text-ink-400 font-normal">（可选）</span>
                </label>
                <div className="relative">
                  <User size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type="text"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="昵称（可选）"
                    maxLength={64}
                    className="input-field pl-11"
                  />
                </div>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">
                {mode === 'login' ? '邮箱或用户名' : '邮箱'}
              </label>
              <div className="relative">
                <Mail size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                <input
                  type={mode === 'register' ? 'email' : 'text'}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={mode === 'login' ? 'your@email.com 或用户名' : 'your@email.com'}
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
                  placeholder={mode === 'register' ? '至少 8 位密码' : '请输入密码'}
                  required
                  minLength={mode === 'register' ? 8 : 6}
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

            {mode === 'register' && (
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">确认密码</label>
                <div className="relative">
                  <Lock size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="请再次输入密码"
                    required
                    minLength={8}
                    className="input-field pl-11"
                  />
                </div>
              </div>
            )}

            {mode === 'login' && (
              <div className="flex items-center justify-between text-sm">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" className="w-4 h-4 accent-indigo-800" />
                  <span className="text-ink-600">记住我</span>
                </label>
                <button type="button" className="text-indigo-800 link-underline">
                  忘记密码？
                </button>
              </div>
            )}

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">
                {error}
              </div>
            )}

            {notice && (
              <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3">
                {notice}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3.5 disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {loading ? (
                <><Loader2 size={16} className="animate-spin" /> 提交中...</>
              ) : (
                mode === 'login' ? '登录' : '注册'
              )}
            </button>
          </form>

          <div className="mt-8 text-center text-sm text-ink-500">
            {mode === 'login' ? (
              <>
                还没有账户？
                <button
                  type="button"
                  onClick={() => switchMode('register')}
                  className="text-indigo-800 font-medium link-underline ml-1"
                >
                  立即注册
                </button>
              </>
            ) : (
              <>
                已有账户？
                <button
                  type="button"
                  onClick={() => switchMode('login')}
                  className="text-indigo-800 font-medium link-underline ml-1"
                >
                  去登录
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
