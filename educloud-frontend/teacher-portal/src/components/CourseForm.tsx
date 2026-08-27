import { useRef, useState } from 'react';
import { Upload, Save, Loader2, Trash2 } from 'lucide-react';
import type { Category, CourseDraft, CourseDraftInput, CourseLevel } from '../types';
import CustomSelect, { type SelectOption } from './CustomSelect';
import { cn } from '../utils/cn';
import { apiErrorText } from '../services/http';
import { teacherCourseApi } from '../services/teacherCourseApi';

interface CourseFormProps {
  initialDraft?: CourseDraft | null;
  categories: Category[];
  onSubmit: (data: CourseDraftInput) => void | Promise<void>;
  onCancel?: () => void;
  loading?: boolean;
  variant?: 'page' | 'modal';
  errorMessage?: string | null;
  /** 提交按钮文案（默认“保存草稿”）。 */
  submitLabel?: string;
}

const levels: { value: CourseLevel; label: string }[] = [
  { value: 'BEGINNER', label: '入门' },
  { value: 'INTERMEDIATE', label: '进阶' },
  { value: 'ADVANCED', label: '高级' },
];

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((c) => [c, ...flattenCategories(c.children)]);
}

/**
 * 课程基本信息表单（M05 任务 22 真实联调）：字段对齐 CourseCreateRequest/
 * CourseDraftUpdateRequest —— title/categoryId/level/price/currency 必填，
 * subtitle/description/coverFileId 可空；封面上传复用 file.ts 三段式返回 fileId。
 * 发布状态不在此选择（真实流程：DRAFT → 提交审核，见 CourseEdit）。
 */
export default function CourseForm({
  initialDraft,
  categories,
  onSubmit,
  onCancel,
  loading,
  variant = 'page',
  errorMessage,
  submitLabel = '保存草稿',
}: CourseFormProps) {
  const isModal = variant === 'modal';
  const flatCategories = flattenCategories(categories);
  const [title, setTitle] = useState(initialDraft?.title ?? '');
  const [subtitle, setSubtitle] = useState(initialDraft?.subtitle ?? '');
  const [description, setDescription] = useState(initialDraft?.description ?? '');
  const [categoryId, setCategoryId] = useState(initialDraft?.categoryId ?? '');
  const [level, setLevel] = useState<CourseLevel>(initialDraft?.level ?? 'BEGINNER');
  const [price, setPrice] = useState(initialDraft?.price ?? '0');
  const [coverFileId, setCoverFileId] = useState<string | null>(initialDraft?.coverFileId ?? null);
  // 编辑回显（任务 22 规格审查②）：draft 响应含 coverUrl（后端 USER grant）；新上传则用本地对象 URL。
  const [coverPreview, setCoverPreview] = useState<string | null>(initialDraft?.coverUrl ?? null);
  const [coverUploading, setCoverUploading] = useState(false);
  const [coverError, setCoverError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleCoverFile = async (file: File | undefined) => {
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setCoverError('仅支持图片文件（JPG/PNG/WebP 等）');
      return;
    }
    setCoverUploading(true);
    setCoverError(null);
    try {
      const fileId = await teacherCourseApi.uploadCover(file);
      setCoverFileId(fileId);
      setCoverPreview(URL.createObjectURL(file));
    } catch (e) {
      setCoverError(apiErrorText(e));
    } finally {
      setCoverUploading(false);
    }
  };

  const removeCover = () => {
    setCoverFileId(null);
    setCoverPreview(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setFormError('请输入课程标题');
      return;
    }
    if (!categoryId) {
      setFormError('请选择课程分类');
      return;
    }
    setFormError(null);
    void onSubmit({
      title: title.trim(),
      subtitle: subtitle.trim() ? subtitle.trim() : null,
      description: description.trim() ? description.trim() : null,
      coverFileId,
      level,
      price: price || '0',
      currency: 'CNY',
      categoryId,
    });
  };

  const shownError = formError ?? errorMessage;

  return (
    <form
      id="course-form"
      onSubmit={handleSubmit}
      className={cn(isModal ? 'space-y-5' : 'space-y-8')}
    >
      {/* Title */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">
          课程标题 <span className="text-amber-600">*</span>
        </label>
        <input
          autoFocus={isModal}
          data-autofocus={isModal ? 'true' : undefined}
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="请输入课程标题，例如：Spring Boot 3 实战"
          className={cn('input-field', isModal ? 'text-base' : 'font-display text-lg')}
          required
        />
      </div>

      {/* Subtitle */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">课程副标题</label>
        <input
          type="text"
          value={subtitle}
          onChange={(e) => setSubtitle(e.target.value)}
          placeholder="一句话概括课程亮点（可选）"
          className="input-field"
        />
      </div>

      {/* Description */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">课程简介</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="详细描述课程内容、学习目标与适合人群……"
          rows={isModal ? 3 : 5}
          className="input-field resize-none"
        />
      </div>

      {/* Category / Level / Price */}
      <div className={cn('grid grid-cols-1 md:grid-cols-3', isModal ? 'gap-4' : 'gap-6')}>
        <div>
          <label className="block text-sm font-medium text-ink-700 mb-2">
            课程分类 <span className="text-amber-600">*</span>
          </label>
          <CustomSelect
            options={flatCategories.map((c) => ({ value: c.id, label: c.name }))}
            value={categoryId}
            onChange={setCategoryId}
            placeholder="请选择分类"
            minWidth="w-full"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink-700 mb-2">课程难度</label>
          <CustomSelect
            options={levels.map((l) => ({ value: l.value, label: l.label }))}
            value={level}
            onChange={(val) => setLevel(val as CourseLevel)}
            placeholder="请选择难度"
            minWidth="w-full"
          />
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
              step="0.01"
              className="input-field pl-8"
              placeholder="0 表示免费课程"
            />
          </div>
        </div>
      </div>

      {/* Cover Upload */}
      <div>
        <label className="block text-sm font-medium text-ink-700 mb-2">课程封面</label>
        <div
          className={cn(
            'border-2 border-dashed border-ink-200 text-center hover:border-indigo-800 transition-colors rounded-2xl',
            isModal ? 'p-5' : 'p-8'
          )}
        >
          {coverPreview || coverFileId ? (
            <div className="space-y-3">
              {coverPreview ? (
                <img
                  src={coverPreview}
                  alt="封面预览"
                  className={cn(
                    'w-full max-w-md mx-auto object-cover border border-ink-100 rounded-xl',
                    isModal ? 'h-32' : 'h-48'
                  )}
                />
              ) : (
                <p className="text-sm text-ink-500 py-4">已上传封面（fileId: {coverFileId}）</p>
              )}
              <div className="flex items-center justify-center gap-3">
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={coverUploading}
                  className="btn-outline"
                >
                  {coverUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                  {coverUploading ? '上传中…' : '更换封面'}
                </button>
                <button
                  type="button"
                  onClick={removeCover}
                  className="btn-ghost text-red-600 hover:text-red-700"
                >
                  <Trash2 className="w-4 h-4" />
                  移除封面
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              <Upload className="w-10 h-10 mx-auto text-ink-300" strokeWidth={1.5} />
              <p className="text-sm text-ink-500">点击或拖拽图片到此处上传</p>
              <p className="text-xs text-ink-400">推荐尺寸 1200 × 800，支持 JPG / PNG</p>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={coverUploading}
                className="btn-outline mt-2"
              >
                {coverUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                {coverUploading ? '上传中…' : '选择图片上传'}
              </button>
            </div>
          )}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => void handleCoverFile(e.target.files?.[0])}
          />
          {coverError && <p role="alert" className="text-sm text-red-600 mt-3">{coverError}</p>}
        </div>
      </div>

      {shownError && (
        <div
          role="alert"
          className="rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-700"
        >
          {shownError}
        </div>
      )}

      {/* Submit */}
      <div className="flex items-center gap-4 pt-4 border-t border-ink-100">
        <button
          type="submit"
          disabled={loading}
          className={cn('btn-primary', isModal && 'flex-1')}
        >
          <Save className="w-4 h-4" />
          {loading ? '保存中…' : submitLabel}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            className={cn('btn-outline', isModal && 'flex-1')}
          >
            取消
          </button>
        )}
      </div>
    </form>
  );
}
