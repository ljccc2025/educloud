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
      <div className="hidden lg:flex lg:w-1/2 bg-indigo-800 relative overflow-hidden">
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 30%, #fcd34d 0%, transparent 50%), radial-gradient(circle at 80% 70%, #818cf8 0%, transparent 50%)',
          }}
        />
        <div className="relative z-10 flex flex-col justify-between p-12 xl:p-16 text-paper w-full">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 bg-paper/10 border border-paper/20 flex items-center justify-center">
              <GraduationCap size={24} className="text-amber-400" />
            </div>
            <div>
              <div className="font-display text-xl font-700">EduCloud</div>
              <div className="text-xs text-paper/60 tracking-widest uppercase">
                Education Cloud
              </div>
            </div>
          </div>

          <div>
            <div className="flex items-center gap-3 mb-6">
              <Shield size={48} className="text-amber-400" />
            </div>
            <h1 className="font-display text-5xl xl:text-6xl font-700 leading-[1.1] mb-6">
              系统管理
            </h1>
            <p className="text-paper/70 text-lg leading-relaxed max-w-md">
              EduCloud 教育云平台管理后台。统一管理用户、课程、订单与系统配置，为平台运营提供全方位支持。
            </p>
            <div className="mt-12 grid grid-cols-3 gap-6 max-w-md">
              <Stat numeral="12.8k" label="注册用户" />
              <Stat numeral="486" label="上线课程" />
              <Stat numeral="99.9%" label="服务可用" />
            </div>
          </div>

          <div className="text-paper/40 text-xs tracking-wider">
            © 2024 EduCloud. 京ICP备2024000000号-1
          </div>
        </div>
      </div>

      {/* Right form */}
      <div className="flex-1 flex items-center justify-center p-6 md:p-12">
        <div className="w-full max-w-md animate-fade-up opacity-0">
          <div className="lg:hidden flex items-center gap-3 mb-10 justify-center">
            <div className="w-11 h-11 bg-indigo-800 flex items-center justify-center">
              <GraduationCap size={22} className="text-paper" />
            </div>
            <div className="font-display text-xl font-700 text-ink-900">EduCloud 管理后台</div>
          </div>

          <div className="section-label mb-3">欢迎回来</div>
          <h2 className="display-heading text-3xl mb-2">管理员登录</h2>
          <p className="text-ink-500 mb-8">请输入您的管理员账号以继续</p>

          {error && (
            <div className="flex items-center gap-2 p-3 mb-6 bg-red-50 border border-red-200 text-red-700 text-sm">
              <AlertCircle size={16} />
              <span>{error}</span>
              <button onClick={clearError} className="ml-auto text-red-400 hover:text-red-600">
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
                <input type="checkbox" className="w-4 h-4 accent-indigo-800" defaultChecked />
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
      <div className="font-display text-3xl font-700 text-amber-400">{numeral}</div>
      <div className="text-paper/60 text-xs mt-1 tracking-wider uppercase">{label}</div>
    </div>
  );
}
