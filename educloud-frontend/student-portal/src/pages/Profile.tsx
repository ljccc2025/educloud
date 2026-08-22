import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  User, Mail, Calendar, BookOpen, Award, Clock,
  Settings, Edit3, Check, ChevronRight,
} from 'lucide-react';
import { useAuthStore } from '@/stores/useAuthStore';
import { currentUser } from '@/services/api';
import { uploadAvatar } from '@/services/file';
import { http, apiErrorText } from '@/services/http';

export default function Profile() {
  const { user, refresh } = useAuthStore();
  const displayUser = user ?? currentUser;
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({
    name: displayUser.realName,
    email: displayUser.email,
    bio: displayUser.bio,
  });
  const [saved, setSaved] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

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
    try {
      const fileId = await uploadAvatar(file);
      // PATCH 为全量更新：displayName 必填（后端 @NotBlank），带上当前档案字段。
      await http.patch('/me/profile', {
        displayName: displayUser.realName || displayUser.username || '学员',
        bio: displayUser.bio ?? '',
        locale: 'zh-CN',
        avatarFileId: fileId,
      });
      await refresh();
    } catch (err) {
      setAvatarError(apiErrorText(err));
    } finally {
      setUploading(false);
    }
  };

  const handleSave = () => {
    setEditing(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const stats = [
    {
      icon: BookOpen,
      value: displayUser.learnedCourses,
      label: '已学习课程',
      color: 'text-indigo-800',
      bg: 'bg-indigo-50',
    },
    {
      icon: Award,
      value: displayUser.consecutiveDays,
      label: '连续学习(天)',
      color: 'text-green-600',
      bg: 'bg-green-50',
    },
    {
      icon: Clock,
      value: displayUser.learnedHours,
      label: '学习时长(小时)',
      color: 'text-amber-600',
      bg: 'bg-amber-50',
    },
    {
      icon: Award,
      value: displayUser.certificates,
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
      <div className="bg-white border border-ink-100 p-8 mb-8">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-6">
          <div className="flex flex-col items-center sm:items-start gap-3 flex-shrink-0">
            <div className="relative w-24 h-24">
              <img
                src={displayUser.avatarUrl || displayUser.avatar}
                alt="用户头像"
                className="w-24 h-24 object-cover bg-indigo-50 border border-ink-100"
              />
              {uploading && (
                <span className="absolute inset-0 flex items-center justify-center bg-ink-900/40 text-white text-xs font-medium">
                  上传中…
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="btn-outline text-sm px-3 py-1.5"
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
              <p className="text-sm text-red-600 max-w-[12rem]">{avatarError}</p>
            )}
          </div>
          <div className="flex-1">
            <h2 className="font-display text-2xl font-bold text-ink-900">{displayUser.realName}</h2>
            <p className="text-ink-500 mt-1">{displayUser.email}</p>
            <p className="text-sm text-ink-400 mt-2 flex items-center gap-1.5">
              <Calendar size={14} />
              加入时间：{displayUser.joinDate}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setEditing(!editing)}
            className="btn-outline"
          >
            {editing ? (
              <><Check size={16} onClick={handleSave} /> 保存</>
            ) : (
              <><Edit3 size={16} /> 编辑资料</>
            )}
          </button>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {stats.map((stat) => (
          <div key={stat.label} className="stat-card text-center">
            <div className={`w-12 h-12 ${stat.bg} flex items-center justify-center mx-auto mb-3`}>
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
          <div className="bg-white border border-ink-100 p-6">
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
                    disabled={!editing}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="input-field pl-10 disabled:bg-ink-50 disabled:text-ink-500"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">邮箱</label>
                <div className="relative">
                  <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-300" />
                  <input
                    type="email"
                    value={form.email}
                    disabled={!editing}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    className="input-field pl-10 disabled:bg-ink-50 disabled:text-ink-500"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-700 mb-2">个人简介</label>
                <textarea
                  value={form.bio}
                  disabled={!editing}
                  onChange={(e) => setForm({ ...form, bio: e.target.value })}
                  rows={4}
                  className="input-field resize-none disabled:bg-ink-50 disabled:text-ink-500"
                />
              </div>
              {editing && (
                <div className="flex gap-3">
                  <button type="button" onClick={handleSave} className="btn-primary">
                    <Check size={16} />
                    保存修改
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditing(false);
                      setForm({
                        name: displayUser.realName,
                        email: displayUser.email,
                        bio: displayUser.bio,
                      });
                    }}
                    className="btn-outline"
                  >
                    取消
                  </button>
                </div>
              )}
              {saved && (
                <p className="text-sm text-green-600 flex items-center gap-1.5">
                  <Check size={14} /> 资料保存成功
                </p>
              )}
            </div>
          </div>
        </div>

        {/* Quick Links */}
        <div className="space-y-4">
          <div className="bg-white border border-ink-100 p-6">
            <h3 className="font-display text-lg font-bold text-ink-900 mb-4">快捷入口</h3>
            <div className="space-y-1">
              <Link
                to="/my-courses"
                className="flex items-center justify-between px-3 py-2.5 text-sm text-ink-600 hover:bg-indigo-50 hover:text-indigo-800 transition-colors"
              >
                <span className="flex items-center gap-2">
                  <BookOpen size={16} /> 我的课程
                </span>
                <ChevronRight size={14} className="text-ink-300" />
              </Link>
              <Link
                to="/orders"
                className="flex items-center justify-between px-3 py-2.5 text-sm text-ink-600 hover:bg-indigo-50 hover:text-indigo-800 transition-colors"
              >
                <span className="flex items-center gap-2">
                  <Settings size={16} /> 我的订单
                </span>
                <ChevronRight size={14} className="text-ink-300" />
              </Link>
              <Link
                to="/assignments"
                className="flex items-center justify-between px-3 py-2.5 text-sm text-ink-600 hover:bg-indigo-50 hover:text-indigo-800 transition-colors"
              >
                <span className="flex items-center gap-2">
                  <Edit3 size={16} /> 我的作业
                </span>
                <ChevronRight size={14} className="text-ink-300" />
              </Link>
              <Link
                to="/exams"
                className="flex items-center justify-between px-3 py-2.5 text-sm text-ink-600 hover:bg-indigo-50 hover:text-indigo-800 transition-colors"
              >
                <span className="flex items-center gap-2">
                  <Award size={16} /> 我的考试
                </span>
                <ChevronRight size={14} className="text-ink-300" />
              </Link>
            </div>
          </div>

          <div className="bg-gradient-to-br from-indigo-800 to-indigo-900 p-6 text-white">
            <h3 className="font-display text-lg font-bold mb-2">学习成就</h3>
            <p className="text-indigo-200 text-sm mb-4">
              你已获得 {displayUser.certificates} 张课程证书，继续加油！
            </p>
            <div className="flex gap-2">
              {Array.from({ length: Math.min(displayUser.certificates, 3) }).map((_, i) => (
                <div key={i} className="w-10 h-10 bg-amber-500 flex items-center justify-center">
                  <Award size={20} className="text-white" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
