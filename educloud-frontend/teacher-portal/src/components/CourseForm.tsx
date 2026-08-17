import { useState } from 'react';
import { Upload, Save, Eye, EyeOff } from 'lucide-react';
import type { Course, CourseCategory, CourseStatus } from '../types';
import { cn } from '../utils/cn';

interface CourseFormProps {
  initialCourse?: Course | null;
  onSubmit: (data: Partial<Course>) => void;
  loading?: boolean;
}

const categories: { value: CourseCategory; label: string }[] = [
  { value: 'backend', label: '后端开发' },
  { value: 'frontend', label: '前端开发' },
  { value: 'data', label: '数据分析' },
  { value: 'ai', label: '人工智能' },
  { value: 'devops', label: '运维部署' },
  { value: 'mobile', label: '移动开发' },
];

export default function CourseForm({ initialCourse, onSubmit, loading }: CourseFormProps) {
  const [title, setTitle] = useState(initialCourse?.title ?? '');
  const [description, setDescription] = useState(initialCourse?.description ?? '');
  const [category, setCategory] = useState<CourseCategory>(initialCourse?.category ?? 'backend');
  const [price, setPrice] = useState(initialCourse?.price?.toString() ?? '0');
  const [cover, setCover] = useState(initialCourse?.cover ?? '');
  const [status, setStatus] = useState<CourseStatus>(initialCourse?.status ?? 'DRAFT');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      title,
      description,
      category,
      price: Number(price) || 0,
      cover: cover || 'https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600&h=400&fit=crop',
      status,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-8">
      {/* Title */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">
          课程标题 <span className="text-amber-600">*</span>
        </label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="请输入课程标题，例如：Spring Boot 3 实战"
          className="input-field font-display text-lg"
          required
        />
      </div>

      {/* Description */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">课程简介</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="详细描述课程内容、学习目标与适合人群……"
          rows={5}
          className="input-field resize-none"
        />
      </div>

      {/* Category & Price */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
          <label className="block text-sm font-medium text-ink-700 mb-2">课程分类</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as CourseCategory)}
            className="input-field appearance-none cursor-pointer"
          >
            {categories.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-ink-700 mb-2">课程定价（元）</label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-400 text-sm">¥</span>
            <input
              type="number"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              min="0"
              className="input-field pl-8"
              placeholder="0 表示免费课程"
            />
          </div>
        </div>
      </div>

      {/* Cover Upload */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">课程封面</label>
        <div className="border-2 border-dashed border-ink-200 p-8 text-center hover:border-indigo-800 transition-colors">
          {cover ? (
            <div className="space-y-3">
              <img
                src={cover}
                alt="封面预览"
                className="w-full max-w-md mx-auto h-48 object-cover border border-ink-100"
              />
              <button
                type="button"
                onClick={() => setCover('')}
                className="btn-ghost text-red-600 hover:text-red-700"
              >
                移除封面
              </button>
            </div>
          ) : (
            <div className="space-y-2">
              <Upload className="w-10 h-10 mx-auto text-ink-300" strokeWidth={1.5} />
              <p className="text-sm text-ink-500">点击或拖拽图片到此处上传</p>
              <p className="text-xs text-ink-400">推荐尺寸 1200 × 800，支持 JPG / PNG</p>
              <input
                type="text"
                value={cover}
                onChange={(e) => setCover(e.target.value)}
                placeholder="或输入图片 URL"
                className="input-field mt-4 max-w-sm mx-auto"
              />
            </div>
          )}
        </div>
      </div>

      {/* Status Toggle */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-3">发布状态</label>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setStatus('DRAFT')}
            className={cn(
              'flex items-center gap-2 px-5 py-2.5 border text-sm font-medium transition-all',
              status === 'DRAFT'
                ? 'border-ink-800 bg-ink-800 text-white'
                : 'border-ink-200 text-ink-600 hover:border-ink-400'
            )}
          >
            <EyeOff className="w-4 h-4" />
            草稿
          </button>
          <button
            type="button"
            onClick={() => setStatus('PUBLISHED')}
            className={cn(
              'flex items-center gap-2 px-5 py-2.5 border text-sm font-medium transition-all',
              status === 'PUBLISHED'
                ? 'border-green-600 bg-green-600 text-white'
                : 'border-ink-200 text-ink-600 hover:border-ink-400'
            )}
          >
            <Eye className="w-4 h-4" />
            立即发布
          </button>
        </div>
      </div>

      {/* Submit */}
      <div className="flex items-center gap-4 pt-4 border-t border-ink-100">
        <button type="submit" disabled={loading} className="btn-primary">
          <Save className="w-4 h-4" />
          {loading ? '保存中…' : '保存课程'}
        </button>
        <button type="button" className="btn-outline">
          取消
        </button>
      </div>
    </form>
  );
}
