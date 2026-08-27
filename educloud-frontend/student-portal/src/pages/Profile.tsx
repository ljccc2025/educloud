import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  User, Mail, Calendar, BookOpen, Award, Clock,
  Settings, Edit3, Check, ChevronRight, LogOut,
} from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { uploadAvatar } from '@/services/file';
import { http, apiErrorText, type ApiEnvelope } from '@/services/http';

// M04 审查修复：presigned 头像 URL 5 分钟过期后破图，onError 兜底回退占位头像。
const FALLBACK_AVATAR =
  'https://api.dicebear.com/7.x/initials/svg?seed=educloud&backgroundColor=1e1b4b&textColor=ffffff&fontWeight=500&fontSize=24';

export default function Profile() {
  const navigate = useNavigate();
  const { user, refresh, logout } = useAuthStore();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', bio: '' });
  const [saved, setSaved] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [avatarUploaded, setAvatarUploaded] = useState(false);
  const [avatarRefreshPending, setAvatarRefreshPending] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (user) {
      setForm({ name: user.realName || user.username || '', email: user.email || '', bio: user.bio || '' });
    }
  }, [user]);

  if (!user) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        <div className="bg-white border border-ink-100 p-8 rounded-2xl">
          <p className="text-ink-500">正在加载个人资料…</p>
        </div>
      </div>
    );
  }

  const current = user;

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      setAvatarError('头像图片不能超过 10MB');
      return;
    }
    setUploading(true);
    setAvatarError(null);
    setAvatarUploaded(false);
    setAvatarRefreshPending(false);
    try {
      const fileId = await uploadAvatar(file);
      // PATCH 为全量更新：displayName 必填（后端 @NotBlank），带上当前档案字段。
      const patched = await http.patch<ApiEnvelope<{ avatarFileId: string | null }>>('/me/profile', {
        displayName: current.realName || current.username || '学员',
        bio: current.bio ?? '',
        locale: 'zh-CN',
        avatarFileId: fileId,
      });
      // 记录当前 avatarFileId：后续资料保存（全量 PATCH）需携带，否则后端会解绑清空头像。
      useAuthStore.setState((state) => ({
        user: state.user
          ? { ...state.user, avatarFileId: patched.data.data.avatarFileId ?? undefined }
          : state.user,
      }));
      // 上传与 PATCH 已成功：refresh 刷新全站用户状态
      setAvatarUploaded(true);
      try {
        await refresh();
      } catch {
        // 头像已保存，仅本地状态未刷新：提示稍后刷新可见
        setAvatarRefreshPending(true);
      }
    } catch (err) {
      setAvatarError(apiErrorText(err));
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    setSaveError(null);
    try {
      // 真实保存：PATCH /me/profile 全量更新（displayName 必填；avatarFileId 携带当前值防止头像被清空）。
      await http.patch('/me/profile', {
        displayName: form.name.trim() || current.username || '学员',
        bio: form.bio ?? '',
        locale: 'zh-CN',
        avatarFileId: current.avatarFileId ?? null,
      });
      await refresh();
      const fresh = useAuthStore.getState().user ?? current;
      setEditing(false);
      setSaved(true);
      setForm({
        name: fresh?.realName ?? '',
        email: fresh?.email ?? '',
        bio: fresh?.bio ?? '',
      });
      setTimeout(() => setSaved(false), 2000);
    } catch (err) {
      setSaveError(apiErrorText(err));
    }
  };

  const stats = [
    {
      icon: BookOpen,
      value: current.learnedCourses,
      label: '已学习课程',
      color: 'text-indigo-800',
      bg: 'bg-indigo-50',
    },
    {
      icon: Award,
      value: current.consecutiveDays,
      label: '连续学习(天)',
      color: 'text-green-600',
      bg: 'bg-green-50',
    },
    {
      icon: Clock,
      value: current.learnedHours,
      label: '学习时长(小时)',
      color: 'text-amber-600',
      bg: 'bg-amber-50',
    },
    {
      icon: Award,
      value: current.certificates,
      label: '获得证书',
      color: 'text-indigo-800',
      bg: 'bg-indigo-50',
    },
  ];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <span className="section-label mb-3">账户管理</span>
        <h1 className="display-heading text-4xl md:text-5xl mt-3">个人中心</h1>
      </div>

      {/* Profile Header */}
      <div className="bg-white border border-ink-100 p-8 mb-8 rounded-2xl shadow-sm">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-6">
          <div className="flex flex-col items-center sm:items-start gap-3 flex-shrink-0">
            <div className="relative w-24 h-24 rounded-2xl overflow-hidden shadow-inner">
              <img
                src={current.avatarUrl || current.avatar}
                alt="用户头像"
                onError={(e) => {
                  const img = e.currentTarget;
                  img.onerror = null;
                  img.src = FALLBACK_AVATAR;
                }}
                className="w-24 h-24 object-cover bg-indigo-50 border border-ink-100"
              />
              {uploading && (
                <span className="absolute inset-0 flex items-center justify-center bg-ink-900/60 text-white text-xs font-semibold backdrop-blur-sm">
                  上传中…
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="btn-outline text-xs px-3 py-1.5 rounded-lg"
            >
              更换头像
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleAvatarChange}
            />
            {avatarError && (
              <p className="text-xs text-red-600 max-w-[14rem] text-center sm:text-left">{avatarError}</p>
            )}
            {avatarUploaded && !avatarRefreshPending && (
              <p className="text-xs text-emerald-600 font-medium max-w-[14rem] text-center sm:text-left">头像已更换成功</p>
            )}
            {avatarUploaded && avatarRefreshPending && (
              <p className="text-xs text-amber-600 max-w-[14rem] text-center sm:text-left">头像已上传，稍后刷新可见</p>
            )}
          </div>
          <div className="flex-1">
            <h2 className="font-display text-2xl font-bold text-ink-900">{current.realName || current.username}</h2>
            {current.email ? (
              <p className="text-sm text-ink-500 mt-1 flex items-center gap-1.5">
                <Mail size={14} className="text-ink-400" />
                {current.email}
              </p>
            ) : current.bio ? (
              <p className="text-sm text-ink-500 mt-1 line-clamp-1">{current.bio}</p>
            ) : (
              <p className="text-xs text-ink-400 mt-1 font-medium bg-ink-50 px-2 py-0.5 rounded w-fit">
                EduCloud 终身学习者
              </p>
            )}
            <p className="text-sm text-ink-400 mt-2 flex items-center gap-1.5">
              <Calendar size={14} />
              加入时间：{current.joinDate || '2026-08-25'}
            </p>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              type="button"
              onClick={editing ? handleSave : () => setEditing(true)}
              className="btn-outline flex items-center gap-1.5"
            >
              {editing ? (
                <><Check size={16} /> 保存资料</>
              ) : (
                <><Edit3 size={16} /> 编辑资料</>
              )}
            </button>
            <button
              type="button"
              onClick={async () => {
                await logout();
                navigate('/');
              }}
              className="btn-outline text-red-600 hover:bg-red-50 hover:border-red-200 dark:text-red-400 flex items-center gap-1.5"
            >
              <LogOut size={16} /> 退出登录
            </button>
          </div>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {stats.map((stat) => (
          <div key={stat.label} className="stat-card text-center p-6 bg-white border border-ink-100 rounded-2xl">
            <div className={`w-12 h-12 ${stat.bg} rounded-xl flex items-center justify-center mx-auto mb-3`}>
              <stat.icon size={22} className={stat.color} strokeWidth={1.5} />
            </div>
            <p className="font-display text-3xl font-bold text-ink-900">{stat.value}</p>
            <p className="text-sm text-ink-500 mt-1">{stat.label}</p>
          </div>
        ))}
      </div>

      <div className="grid md:grid-cols-3 gap-8">
        {/* Profile Form */}
        <div className="md:col-span-2">
          <div className="bg-white border border-ink-100 p-6 rounded-2xl shadow-sm">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-6 flex items-center gap-2">
              <Settings size={18} className="text-ink-400" />
              基本信息
            </h3>
            <div className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">姓名</label>
                <div className="relative">
                  <User size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    disabled={!editing}
                    placeholder="请输入您的姓名或昵称"
                    className="input-field pl-10 disabled:bg-ink-50/50 disabled:text-ink-600"
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">邮箱地址</label>
                <div className="relative">
                  <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type="email"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    disabled={!editing}
                    placeholder="请输入邮箱地址"
                    className="input-field pl-10 disabled:bg-ink-50/50 disabled:text-ink-600"
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">个人简介</label>
                <textarea
                  value={form.bio}
                  onChange={(e) => setForm({ ...form, bio: e.target.value })}
                  disabled={!editing}
                  rows={4}
                  placeholder="简单介绍一下自己吧…"
                  className="input-field disabled:bg-ink-50/50 disabled:text-ink-600 resize-none"
                />
              </div>

              {saveError && (
                <p className="text-sm text-red-600">{saveError}</p>
              )}
              {saved && (
                <div className="flex items-center gap-2 text-green-600 text-sm font-medium">
                  <Check size={16} />
                  个人信息已成功保存
                </div>
              )}

              {editing && (
                <div className="flex justify-end gap-3 pt-4 border-t border-ink-100">
                  <button
                    type="button"
                    onClick={() => {
                      setEditing(false);
                      setSaveError(null);
                      setForm({
                        name: current.realName || current.username || '',
                        email: current.email || '',
                        bio: current.bio || '',
                      });
                    }}
                    className="btn-outline text-sm"
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    onClick={handleSave}
                    className="btn-primary text-sm"
                  >
                    保存修改
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Quick Links */}
        <div className="space-y-6">
          <div className="bg-white border border-ink-100 p-6 rounded-2xl shadow-sm">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-4">快捷入口</h3>
            <div className="space-y-2">
              <Link
                to="/my-courses"
                className="flex items-center justify-between p-3 rounded-lg hover:bg-ink-50 transition-colors text-ink-700 text-sm font-medium"
              >
                <span className="flex items-center gap-3">
                  <BookOpen size={16} className="text-indigo-900" />
                  我的课程
                </span>
                <ChevronRight size={16} className="text-ink-400" />
              </Link>
              <Link
                to="/orders"
                className="flex items-center justify-between p-3 rounded-lg hover:bg-ink-50 transition-colors text-ink-700 text-sm font-medium"
              >
                <span className="flex items-center gap-3">
                  <Award size={16} className="text-emerald-600" />
                  我的订单
                </span>
                <ChevronRight size={16} className="text-ink-400" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
