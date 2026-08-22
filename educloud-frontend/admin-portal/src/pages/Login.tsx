import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, Eye, EyeOff, GraduationCap, AlertCircle } from 'lucide-react';
import { useAuthStore } from '../stores/useAuthStore';

export default function Login() {
  const navigate = useNavigate();
  const { login, loading, error, token, clearError } = useAuthStore();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    if (token) navigate('/', { replace: true });
  }, [token, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const ok = await login(username, password);
    if (ok) navigate('/', { replace: true });
  };

  return (
    <div className="min-h-screen flex bg-paper">
      {/* Left panel */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-[#0c0c16] via-[#1a1030] to-[#0c0c16] relative overflow-hidden">
        {/* Purple glow decorations */}
        <div className="absolute -top-20 -left-20 w-80 h-80 bg-brand-500/20 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 right-0 w-96 h-96 bg-purple-500/10 rounded-full blur-3xl pointer-events-none" />
        <div
          className="absolute inset-0 opacity-30"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 30%, rgba(139, 92, 246, 0.3) 0%, transparent 50%), radial-gradient(circle at 80% 70%, rgba(167, 139, 250, 0.2) 0%, transparent 50%)',
          }}
        />
        <div className="relative z-10 flex flex-col justify-between p-12 xl:p-16 text-white w-full">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 bg-white/5 border border-white/10 rounded-xl flex items-center justify-center">
              <GraduationCap size={24} className="text-amber-400" />
            </div>
            <div>
              <div className="font-display text-xl font-bold">EduCloud</div>
              <div className="text-xs text-white/50 tracking-widest uppercase">
                Education Cloud
              </div>
            </div>
          </div>

          <div>
            <div className="flex items-center gap-3 mb-6">
              <Shield size={48} className="text-brand-400" />
            </div>
            <h1 className="font-display text-5xl xl:text-6xl font-bold leading-[1.1] mb-6">
              系统管理
            </h1>
            <p className="text-white/70 text-lg leading-relaxed max-w-md">
              EduCloud 教育云平台管理后台。统一管理用户、课程、订单与系统配置，为平台运营提供全方位支持。
            </p>
            <div className="mt-12 grid grid-cols-3 gap-6 max-w-md">
              <Stat numeral="12.8k" label="注册用户" />
              <Stat numeral="486" label="上线课程" />
              <Stat numeral="99.9%" label="服务可用" />
            </div>
          </div>

          <div className="text-white/50 text-xs tracking-wider">
            © 2024 EduCloud. 京ICP备2024000000号-1
          </div>
        </div>
      </div>

      {/* Right form */}
      <div className="flex-1 flex items-center justify-center p-6 md:p-12">
        <div className="w-full max-w-md animate-fade-up opacity-0">
          <div className="lg:hidden flex items-center gap-3 mb-10 justify-center">
            <div className="w-11 h-11 bg-indigo-800 rounded-xl flex items-center justify-center">
              <GraduationCap size={22} className="text-white" />
            </div>
            <div className="font-display text-xl font-bold text-ink-900">EduCloud 管理后台</div>
          </div>

          <div className="section-label mb-3">欢迎回来</div>
          <h2 className="display-heading text-3xl mb-2">管理员登录</h2>
          <p className="text-ink-500 mb-8">请输入您的管理员账号以继续</p>

          {error && (
            <div className="flex items-center gap-2 p-3 mb-6 bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20 text-sm rounded-xl">
              <AlertCircle size={16} />
              <span>{error}</span>
              <button onClick={clearError} className="ml-auto text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300">
                ×
              </button>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">用户名</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="input-field"
                placeholder="请输入用户名"
                required
                autoComplete="username"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-700 mb-2">密码</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input-field pr-11"
                  placeholder="请输入密码"
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-700"
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-sm">
              <label className="inline-flex items-center gap-2 cursor-pointer text-ink-600">
                <input type="checkbox" className="w-4 h-4 accent-brand-500" defaultChecked />
                记住我
              </label>
              <a href="#" className="link-underline">
                忘记密码？
              </a>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3.5 disabled:opacity-50"
            >
              {loading ? '登录中...' : '登 录'}
            </button>
          </form>

          <div className="mt-8 p-4 bg-ink-50 border border-ink-100 text-sm text-ink-500">
            <span className="font-medium text-ink-700">演示账号：</span> admin / admin123
          </div>
        </div>
      </div>
    </div>
  );
}

function Stat({ numeral, label }: { numeral: string; label: string }) {
  return (
    <div>
      <div className="font-display text-3xl font-bold text-brand-400">{numeral}</div>
      <div className="text-white/50 text-xs mt-1 tracking-wider uppercase">{label}</div>
    </div>
  );
}
