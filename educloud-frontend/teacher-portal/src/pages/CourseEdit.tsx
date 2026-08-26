import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  CopyPlus,
  Image as ImageIcon,
  Info,
  ListTree,
  Loader2,
  RefreshCw,
  Save,
  Send,
  Upload,
  Trash2,
} from 'lucide-react';
import { teacherCourseApi } from '../services/teacherCourseApi';
import { apiErrorText } from '../services/http';
import type { Category, CourseDraft, CourseDraftInput } from '../types';
import CourseForm from '../components/CourseForm';
import { cn } from '../utils/cn';

type Tab = 'basic' | 'cover' | 'chapters';

const tabs: { key: Tab; label: string; icon: typeof Info }[] = [
  { key: 'basic', label: '基本信息', icon: Info },
  { key: 'cover', label: '封面设置', icon: ImageIcon },
  { key: 'chapters', label: '章节管理', icon: ListTree },
];

const versionBanner: Record<
  string,
  { text: string; cls: string }
> = {
  DRAFT: { text: '可编辑草稿', cls: 'badge-amber' },
  PENDING_REVIEW: { text: '已提交审核（只读）', cls: 'badge-indigo' },
  REJECTED: { text: '审核未通过，可复制为新草稿', cls: 'badge-red' },
  WITHDRAWN: { text: '已撤回（只读），可复制为新草稿', cls: 'badge-red' },
  PUBLISHED: { text: '已发布版本（可复制为新草稿继续编辑）', cls: 'badge-green' },
};

export default function CourseEdit() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isNew = id === 'new';

  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesError, setCategoriesError] = useState<string | null>(null);
  const [draft, setDraft] = useState<CourseDraft | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [copying, setCopying] = useState(false);
  const [coverUploading, setCoverUploading] = useState(false);
  const [coverPreview, setCoverPreview] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('basic');
  const [retryTick, setRetryTick] = useState(0);
  const coverInputRef = useRef<HTMLInputElement>(null);
  const loadingCourseIdRef = useRef<string | null>(null);

  const loadDraft = useCallback(async () => {
    if (isNew || !id) return;
    if (loadingCourseIdRef.current === id) return;
    loadingCourseIdRef.current = id;
    setLoading(true);
    setError(null);
    try {
      const cats = await teacherCourseApi.getCategories();
      setCategories(cats);

      let draftData: CourseDraft | null = null;
      try {
        draftData = await teacherCourseApi.getDraft(id);
      } catch (err) {
        // 如果当前课程没有活动草稿（如已发布/已驳回/已撤回），自动从已有版本无缝复制或创建新草稿
        try {
          draftData = await teacherCourseApi.createDraft(id);
          setNotice(`已为您基于当前版本创建草稿 v${draftData.versionNo}，可直接编辑与修改`);
        } catch (copyErr) {
          throw err;
        }
      }

      setDraft(draftData);
      if (draftData?.coverUrl) {
        setCoverPreview(draftData.coverUrl);
      }
    } catch (e) {
      setError(apiErrorText(e));
    } finally {
      loadingCourseIdRef.current = null;
      setLoading(false);
    }
  }, [id, isNew]);

  const loadCategories = useCallback(async () => {
    setCategoriesError(null);
    try {
      const cats = await teacherCourseApi.getCategories();
      setCategories(cats);
    } catch (e) {
      setCategoriesError(apiErrorText(e));
    }
  }, []);

  useEffect(() => {
    if (isNew) {
      void loadCategories();
    } else {
      void loadDraft();
    }
  }, [isNew, loadDraft, loadCategories, retryTick]);

  const editable = draft?.versionStatus === 'DRAFT';

  const handleSave = async (data: CourseDraftInput) => {
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      if (isNew) {
        const created = await teacherCourseApi.createCourse(data);
        navigate('/courses/edit/' + created.courseId);
        return;
      }
      if (!draft) return;
      const updated = await teacherCourseApi.updateDraft(draft.versionId, data);
      setDraft(updated);
      setNotice('草稿已保存');
    } catch (e) {
      setError(apiErrorText(e));
    } finally {
      setSaving(false);
    }
  };

  const handleSubmitReview = async () => {
    if (!draft) return;
    if (!window.confirm('确定提交审核吗？提交后草稿将不可再编辑，等待管理员审核。')) return;
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      await teacherCourseApi.submitReview(draft.versionId);
      const updated = await teacherCourseApi.getDraft(draft.courseId);
      setDraft(updated);
      setNotice('已提交审核，等待管理员审核');
    } catch (e) {
      setError(apiErrorText(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopyDraft = async () => {
    if (!draft) return;
    if (!window.confirm('从当前版本复制一个新草稿继续编辑？')) return;
    setCopying(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await teacherCourseApi.createDraft(draft.courseId);
      setDraft(updated);
      setNotice('已创建新草稿 v' + updated.versionNo + '，可编辑');
    } catch (e) {
      setError(apiErrorText(e));
    } finally {
      setCopying(false);
    }
  };

  const saveCoverFileId = async (coverFileId: string | null) => {
    if (!draft) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const input: CourseDraftInput = {
        title: draft.title,
        subtitle: draft.subtitle,
        description: draft.description,
        coverFileId,
        level: draft.level,
        price: draft.price ?? '0',
        currency: draft.currency ?? 'CNY',
        categoryId: draft.categoryId ?? '',
      };
      const updated = await teacherCourseApi.updateDraft(draft.versionId, input);
      setDraft(updated);
      setNotice(coverFileId ? '封面已上传并保存到草稿' : '封面已移除');
    } catch (e) {
      setError(apiErrorText(e));
    } finally {
      setSaving(false);
    }
  };

  const handleCoverFile = async (file: File | undefined) => {
    if (!file || !draft) return;
    if (!file.type.startsWith('image/')) {
      setError('仅支持图片文件（JPG/PNG/WebP 等）');
      return;
    }
    setCoverUploading(true);
    setError(null);
    const previousPreview = coverPreview;
    const objectUrl = URL.createObjectURL(file);
    try {
      const fileId = await teacherCourseApi.uploadCover(file);
      setCoverPreview(objectUrl);
      await saveCoverFileId(fileId);
    } catch (e) {
      // 保存失败回滚预览（任务 22 规格审查②）：释放本次对象 URL、恢复旧预览。
      URL.revokeObjectURL(objectUrl);
      setCoverPreview(previousPreview);
      setError(apiErrorText(e));
    } finally {
      setCoverUploading(false);
    }
  };

  if (!isNew && loading && !draft) {
    return (
      <div className="flex items-center justify-center py-24 text-ink-400">
        <Loader2 className="w-5 h-5 animate-spin mr-2" />
        加载课程中…
      </div>
    );
  }

  if (!isNew && error && !draft) {
    return (
      <div className="card-editorial p-12 text-center space-y-4 max-w-xl mx-auto my-12">
        <AlertCircle className="w-12 h-12 mx-auto text-amber-500" />
        <h3 className="text-lg font-bold text-ink-800">暂无待编辑草稿</h3>
        <p className="text-sm text-ink-500">
          {error.includes('不存在') || error.includes('not found') || error.includes('no draft')
            ? '该课程当前处于已发布/非草稿状态，点击下方按钮即可基于最新版本创建新版本草稿继续编辑。'
            : error}
        </p>
        <div className="flex items-center justify-center gap-3 pt-2">
          <button type="button" onClick={() => setRetryTick((tick) => tick + 1)} className="btn-outline">
            <RefreshCw className="w-4 h-4" />
            重新加载
          </button>
          {id && (
            <button
              type="button"
              onClick={async () => {
                setCopying(true);
                setError(null);
                try {
                  const updated = await teacherCourseApi.createDraft(id);
                  setDraft(updated);
                  setNotice('已创建新草稿 v' + updated.versionNo + '，可编辑');
                } catch (e) {
                  setError(apiErrorText(e));
                } finally {
                  setCopying(false);
                }
              }}
              disabled={copying}
              className="btn-primary"
            >
              <CopyPlus className="w-4 h-4" />
              {copying ? '创建中…' : '从最近版本创建新草稿'}
            </button>
          )}
        </div>
      </div>
    );
  }

  const banner = draft ? versionBanner[draft.versionStatus] : undefined;

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/courses')} className="btn-ghost">
          <ArrowLeft className="w-4 h-4" />
          返回
        </button>
        <div className="flex-1">
          <p className="section-label mb-1">课程编辑</p>
          <h1 className="display-heading text-2xl md:text-3xl">
            {isNew ? '新建课程' : draft?.title}
          </h1>
        </div>
        {activeTab === 'basic' && editable && (
          <button form="course-form" type="submit" disabled={saving} className="btn-primary">
            <Save className="w-4 h-4" />
            {saving ? '保存中…' : '保存草稿'}
          </button>
        )}
        {activeTab === 'basic' && editable && (
          <button
            type="button"
            onClick={() => void handleSubmitReview()}
            disabled={submitting || saving}
            className="btn-primary !bg-green-700 !border-green-700 hover:!bg-green-800"
          >
            <Send className="w-4 h-4" />
            {submitting ? '提交中…' : '提交审核'}
          </button>
        )}
        {(draft?.versionStatus === 'REJECTED' || draft?.versionStatus === 'WITHDRAWN') && activeTab === 'basic' && (
          <button
            type="button"
            onClick={() => void handleCopyDraft()}
            disabled={copying}
            className="btn-outline"
          >
            <CopyPlus className="w-4 h-4" />
            {copying ? '创建中…' : '复制为新草稿'}
          </button>
        )}
      </div>

      {/* Status banner */}
      {banner && draft && (
        <div className="flex items-center gap-2 text-sm text-ink-600">
          <span className={cn('px-3 py-1 rounded-full', banner.cls)}>{banner.text}</span>
          <span className="text-ink-400">
            v{draft.versionNo} · {draft.versionStatus === 'DRAFT' ? '可编辑' : '只读'}
          </span>
        </div>
      )}

      {notice && (
        <div className="flex items-center gap-2 rounded-xl border border-green-200 bg-green-50/80 px-4 py-3 text-sm text-green-700">
          <CheckCircle2 className="w-4 h-4" />
          {notice}
        </div>
      )}

      {error && !isNew && draft && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Tabs */}
      <div className="border-b border-ink-200">
        <nav className="flex gap-1">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={cn(
                'flex items-center gap-2 px-5 py-3 text-sm font-medium border-b-2 transition-all -mb-px rounded-lg',
                activeTab === tab.key
                  ? 'border-amber-600 text-indigo-800'
                  : 'border-transparent text-ink-400 hover:text-ink-700'
              )}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab content */}
      <div className="max-w-3xl">
        {activeTab === 'basic' && (
          isNew && categoriesError ? (
            <div className="card-editorial p-8 text-center space-y-4">
              <AlertCircle className="w-10 h-10 mx-auto text-red-300" />
              <p className="text-ink-600">课程分类加载失败</p>
              <p className="text-sm text-ink-400">{categoriesError}</p>
              <button type="button" onClick={() => void loadCategories()} className="btn-outline">
                <RefreshCw className="w-4 h-4" />
                重新加载分类
              </button>
            </div>
          ) : isNew && categories.length === 0 ? (
            <div className="card-editorial p-12 text-center text-ink-400 text-sm">正在加载课程分类…</div>
          ) : draft && !editable ? (
            <div className="card-editorial p-8 space-y-4">
              <div className="space-y-1">
                <p className="section-label mb-2">课程信息</p>
                <h2 className="font-display text-2xl font-semibold text-ink-900">{draft.title}</h2>
                {draft.subtitle && <p className="text-ink-500">{draft.subtitle}</p>}
                {draft.description && <p className="text-ink-600 text-sm whitespace-pre-line">{draft.description}</p>}
              </div>
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div><dt className="text-ink-400">难度</dt><dd className="text-ink-800">{draft.level}</dd></div>
                <div><dt className="text-ink-400">价格</dt><dd className="text-ink-800">{draft.price === '0' || draft.price == null ? '免费' : '¥' + draft.price}</dd></div>
                <div><dt className="text-ink-400">分类 ID</dt><dd className="text-ink-800">{draft.categoryId ?? '—'}</dd></div>
                <div><dt className="text-ink-400">版本</dt><dd className="text-ink-800">v{draft.versionNo}</dd></div>
              </dl>
              {(draft.versionStatus === 'REJECTED' || draft.versionStatus === 'WITHDRAWN') && (
                <p className="text-sm text-red-600">
                  {draft.versionStatus === 'REJECTED' ? '审核未通过' : '已撤回'}，
                  点击右上角「复制为新草稿」修改后重新提交。
                </p>
              )}
            </div>
          ) : (
            <CourseForm
              key={draft?.versionId ?? 'new'}
              initialDraft={draft}
              categories={categories}
              onSubmit={handleSave}
              loading={saving}
              errorMessage={error && isNew ? error : null}
            />
          )
        )}

        {activeTab === 'cover' && (
          <div className="card-editorial p-8">
            {!draft ? (
              <div className="text-center py-12 text-ink-400 text-sm">请先保存课程基本信息后再设置封面</div>
            ) : !editable ? (
              <div className="text-center py-12">
                <ImageIcon className="w-12 h-12 mx-auto text-ink-200 mb-4" />
                <p className="text-ink-500">当前版本不可编辑（{draft.versionStatus}），无法修改封面</p>
              </div>
            ) : (
              <div className="space-y-4">
                <p className="text-sm text-ink-500">封面上传后立即保存到当前草稿（全量 PUT）。</p>
                {coverPreview ? (
                  <img
                    src={coverPreview}
                    alt="封面预览"
                    className="w-full max-w-lg aspect-video object-cover border border-ink-100 rounded-2xl"
                  />
                ) : draft.coverFileId ? (
                  <div className="w-full max-w-lg aspect-video border border-ink-100 rounded-2xl bg-ink-50 flex items-center justify-center text-ink-400 text-sm">
                    已设置封面（fileId: {draft.coverFileId}）
                  </div>
                ) : (
                  <div className="w-full max-w-lg aspect-video border border-ink-100 rounded-2xl bg-ink-50 flex items-center justify-center text-ink-400 text-sm">
                    尚未设置封面
                  </div>
                )}
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => coverInputRef.current?.click()}
                    disabled={coverUploading || saving}
                    className="btn-primary"
                  >
                    {coverUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                    {coverUploading ? '上传中…' : '上传封面'}
                  </button>
                  {draft.coverFileId && (
                    <button
                      type="button"
                      onClick={() => void saveCoverFileId(null)}
                      disabled={saving}
                      className="btn-outline text-red-600"
                    >
                      <Trash2 className="w-4 h-4" />
                      移除封面
                    </button>
                  )}
                </div>
                <input
                  ref={coverInputRef}
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={(e) => void handleCoverFile(e.target.files?.[0])}
                />
              </div>
            )}
          </div>
        )}

        {activeTab === 'chapters' && (
          <div className="card-editorial p-12 text-center">
            <ListTree className="w-12 h-12 mx-auto text-ink-200 mb-4" />
            <p className="text-ink-500 mb-2">章节与课件内容管理</p>
            <p className="text-sm text-ink-400">将在 M06 内容服务接入后开放（GET /teacher/courses/{id}/content-draft）</p>
          </div>
        )}
      </div>
    </div>
  );
}
