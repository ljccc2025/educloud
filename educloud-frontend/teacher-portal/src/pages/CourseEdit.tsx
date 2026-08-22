import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Save, Info, ListTree, Image as ImageIcon } from 'lucide-react';
import { useCourseStore } from '../stores/useCourseStore';
import CourseForm from '../components/CourseForm';
import ContentEditor from '../components/ContentEditor';
import type { CoursewareType } from '../types';
import { cn } from '../utils/cn';

type Tab = 'basic' | 'chapters' | 'cover';

const tabs: { key: Tab; label: string; icon: typeof Info }[] = [
  { key: 'basic', label: '基本信息', icon: Info },
  { key: 'chapters', label: '章节管理', icon: ListTree },
  { key: 'cover', label: '封面设置', icon: ImageIcon },
];

export default function CourseEdit() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { courses, currentCourse, loading, fetchCourses, fetchCourse, createCourse, updateCourse, addChapter, removeChapter, reorderChapters, addCourseware, removeCourseware } = useCourseStore();
  const [activeTab, setActiveTab] = useState<Tab>('basic');
  const [saving, setSaving] = useState(false);

  const isNew = id === 'new';
  const course = isNew ? null : (currentCourse ?? courses.find((c) => c.id === id) ?? null);

  useEffect(() => {
    if (!isNew && id) {
      fetchCourse(id);
    } else {
      fetchCourses();
    }
  }, [id, isNew, fetchCourse, fetchCourses]);

  const handleSubmit = async (data: Parameters<typeof createCourse>[0]) => {
    setSaving(true);
    try {
      if (isNew) {
        const created = await createCourse(data);
        navigate(`/courses/edit/${created.id}`);
      } else if (course) {
        await updateCourse(course.id, data);
      }
    } finally {
      setSaving(false);
    }
  };

  if (!isNew && loading && !course) {
    return (
      <div className="flex items-center justify-center py-24 text-ink-400">
        加载课程中…
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade-up">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/courses')}
          className="btn-ghost"
        >
          <ArrowLeft className="w-4 h-4" />
          返回
        </button>
        <div className="flex-1">
          <p className="section-label mb-1">课程编辑</p>
          <h1 className="display-heading text-2xl md:text-3xl">
            {isNew ? '新建课程' : course?.title}
          </h1>
        </div>
        {activeTab === 'basic' && (
          <button
            form="course-form"
            type="submit"
            disabled={saving}
            className="btn-primary"
          >
            <Save className="w-4 h-4" />
            {saving ? '保存中…' : '保存'}
          </button>
        )}
      </div>

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
          <CourseForm
            key={course?.id ?? 'new'}
            initialCourse={course}
            onSubmit={handleSubmit}
            loading={saving}
          />
        )}

        {activeTab === 'chapters' && (
          <div>
            {course ? (
              <ContentEditor
                course={course}
                onAddChapter={(title) => addChapter(course.id, title)}
                onRemoveChapter={(chId) => removeChapter(course.id, chId)}
                onReorderChapters={(chapters) => reorderChapters(course.id, chapters)}
                onAddCourseware={(chId, type, title) =>
                  addCourseware(course.id, chId, {
                    title,
                    type: type as CoursewareType,
                    url: '#',
                    duration: type === 'VIDEO' ? 30 : undefined,
                    size: type !== 'VIDEO' ? 1.5 : undefined,
                  })
                }
                onRemoveCourseware={(chId, cwId) => removeCourseware(course.id, chId, cwId)}
              />
            ) : (
              <div className="card-editorial p-12 text-center">
                <ListTree className="w-12 h-12 mx-auto text-ink-200 mb-4" />
                <p className="text-ink-500 mb-2">请先保存课程基本信息</p>
                <p className="text-sm text-ink-400">创建课程后即可添加章节与课件内容</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'cover' && (
          <div className="card-editorial p-8">
            {course?.cover ? (
              <div className="space-y-4">
                <img
                  src={course.cover}
                  alt="课程封面"
                  className="w-full max-w-lg aspect-video object-cover border border-ink-100 rounded-2xl"
                />
                <p className="text-sm text-ink-500">当前封面预览。如需更换，请在「基本信息」标签页中修改封面 URL。</p>
                <button
                  onClick={() => updateCourse(course.id, { cover: '' })}
                  className="btn-outline"
                >
                  移除封面
                </button>
              </div>
            ) : (
              <div className="text-center py-12">
                <ImageIcon className="w-12 h-12 mx-auto text-ink-200 mb-4" />
                <p className="text-ink-500">尚未设置封面</p>
                <p className="text-sm text-ink-400 mt-1">请在「基本信息」标签页上传封面图片</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
