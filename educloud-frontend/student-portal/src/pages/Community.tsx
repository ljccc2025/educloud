import { useMemo, useState } from 'react';
import {
  Bookmark,
  Flame,
  Heart,
  MessageCircle,
  Plus,
  Search,
  Send,
  Sparkles,
  Users,
} from 'lucide-react';
import { useCommunityStore } from '../features/engagement/useCommunityStore';
import SelectedTagFilter from '../features/engagement/components/SelectedTagFilter';
import { cn } from '../utils/cn';

type CommunityFilter = 'LATEST' | 'HOT' | 'BOOKMARKED';

const filters: Array<{ value: CommunityFilter; label: string; icon: typeof Flame }> = [
  { value: 'LATEST', label: '最新讨论', icon: MessageCircle },
  { value: 'HOT', label: '热门讨论', icon: Flame },
  { value: 'BOOKMARKED', label: '我的收藏', icon: Bookmark },
];

const popularTags = ['高等数学', 'Python', 'React', '微服务', '学习方法', '考试复习'];

export default function Community() {
  const posts = useCommunityStore((state) => state.posts);
  const addPost = useCommunityStore((state) => state.addPost);
  const toggleLike = useCommunityStore((state) => state.toggleLike);
  const toggleBookmark = useCommunityStore((state) => state.toggleBookmark);
  const addReply = useCommunityStore((state) => state.addReply);
  const [filter, setFilter] = useState<CommunityFilter>('LATEST');
  const [keyword, setKeyword] = useState('');
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [courseName, setCourseName] = useState('高等数学精讲：从极限到微积分');
  const [tags, setTags] = useState('学习方法');
  const [formError, setFormError] = useState<string | null>(null);
  const [expandedPostId, setExpandedPostId] = useState<number | null>(null);
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});

  const visiblePosts = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    let result = filter === 'BOOKMARKED' ? posts.filter((post) => post.bookmarked) : [...posts];
    result.sort((first, second) => filter === 'HOT'
      ? second.likes + second.replies.length - first.likes - first.replies.length
      : second.id - first.id);

    if (selectedTag) {
      result = result.filter((post) => post.tags.includes(selectedTag));
    }

    if (!normalizedKeyword) return result;
    return result.filter((post) => [post.title, post.content, post.courseName, ...post.tags]
      .some((value) => value.toLowerCase().includes(normalizedKeyword)));
  }, [filter, keyword, posts, selectedTag]);

  const publishPost = () => {
    const created = addPost({
      title,
      content,
      courseName,
      tags: tags.split(/[，,]/),
    });
    if (!created) {
      setFormError('请完整填写讨论标题和内容');
      return;
    }
    setTitle('');
    setContent('');
    setTags('学习方法');
    setFormError(null);
    setComposerOpen(false);
    setFilter('LATEST');
    setKeyword('');
    setSelectedTag(null);
  };

  const publishReply = (postId: number) => {
    if (!addReply(postId, replyDrafts[postId] ?? '')) return;
    setReplyDrafts((current) => ({ ...current, [postId]: '' }));
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-10 md:px-8 md:py-14 animate-fade-up">
      <div className="flex flex-col gap-5 border-b border-ink-100 pb-8 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="section-label mb-3">同伴学习空间</p>
          <h1 className="display-heading text-4xl md:text-5xl">学习社区</h1>
          <p className="mt-3 text-sm text-ink-500">分享学习经验，讨论课程问题，与同学共同进步</p>
        </div>
        <button type="button" onClick={() => setComposerOpen((open) => !open)} className="btn-primary self-start md:self-auto">
          <Plus size={17} /> 发布讨论
        </button>
      </div>

      {composerOpen && (
        <section className="mt-6 rounded-2xl border border-indigo-100 bg-white p-5 shadow-xl shadow-indigo-900/5 md:p-7">
          <div className="mb-5 flex items-center justify-between gap-4">
            <div>
              <h2 className="font-display text-xl font-semibold text-ink-900">发布新讨论</h2>
              <p className="mt-1 text-xs text-ink-400">描述清楚背景和问题，更容易获得有效回复</p>
            </div>
            <button type="button" onClick={() => setComposerOpen(false)} className="text-sm text-ink-400 hover:text-indigo-800">取消</button>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="md:col-span-2">
              <label htmlFor="community-title" className="mb-2 block text-sm font-medium text-ink-700">讨论标题</label>
              <input id="community-title" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={80} className="input-field rounded-xl" placeholder="用一句话概括你的问题或经验" />
            </div>
            <div className="md:col-span-2">
              <label htmlFor="community-content" className="mb-2 block text-sm font-medium text-ink-700">讨论内容</label>
              <textarea id="community-content" value={content} onChange={(event) => setContent(event.target.value)} maxLength={1200} rows={4} className="input-field resize-y rounded-xl" placeholder="补充课程背景、尝试过的方法和具体疑问" />
            </div>
            <div>
              <label htmlFor="community-course" className="mb-2 block text-sm font-medium text-ink-700">关联课程</label>
              <select id="community-course" value={courseName} onChange={(event) => setCourseName(event.target.value)} className="input-field cursor-pointer rounded-xl">
                <option>高等数学精讲：从极限到微积分</option>
                <option>Python 数据分析实战</option>
                <option>前端工程化与 React 进阶</option>
                <option>Java 后端架构设计</option>
              </select>
            </div>
            <div>
              <label htmlFor="community-tags" className="mb-2 block text-sm font-medium text-ink-700">标签</label>
              <input id="community-tags" value={tags} onChange={(event) => setTags(event.target.value)} className="input-field rounded-xl" placeholder="使用逗号分隔，最多三个" />
            </div>
          </div>
          {formError && <p className="mt-3 text-sm text-red-600">{formError}</p>}
          <div className="mt-5 flex justify-end">
            <button type="button" onClick={publishPost} className="btn-primary"><Send size={16} /> 确认发布</button>
          </div>
        </section>
      )}

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <main className="min-w-0">
          <div className="card-editorial mb-4 flex flex-col gap-3 p-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex overflow-x-auto">
              {filters.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  onClick={() => setFilter(item.value)}
                  className={cn(
                    'flex shrink-0 items-center gap-2 px-4 py-2.5 text-sm rounded-xl transition-colors',
                    filter === item.value ? 'bg-indigo-800 text-white' : 'text-ink-500 hover:bg-ink-50 hover:text-indigo-800',
                  )}
                >
                  <item.icon size={15} /> {item.label}
                </button>
              ))}
              {selectedTag ? (
                <SelectedTagFilter tag={selectedTag} onClear={() => setSelectedTag(null)} />
              ) : null}
            </div>
            <div className="relative min-w-0 sm:w-60">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={15} />
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} className="input-field rounded-xl py-2 pl-9" placeholder="搜索讨论..." />
            </div>
          </div>

          <div className="space-y-4" aria-live="polite">
            {visiblePosts.length === 0 ? (
              <div className="card-editorial flex min-h-64 flex-col items-center justify-center px-6 text-center">
                <MessageCircle className="mb-4 text-ink-300" size={36} />
                <h2 className="font-display text-xl font-semibold text-ink-900">没有找到相关讨论</h2>
                <p className="mt-2 text-sm text-ink-400">尝试更换关键词或发布新的讨论</p>
              </div>
            ) : visiblePosts.map((post) => {
              const expanded = expandedPostId === post.id;
              return (
                <article key={post.id} className="card-editorial p-5 md:p-6">
                  <div className="flex items-start gap-3">
                    <img src={post.avatar} alt="" className="h-10 w-10 shrink-0 rounded-full border border-ink-100 bg-indigo-50 object-cover" loading="lazy" />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-400">
                        <span className="font-medium text-ink-700">{post.author}</span>
                        <span>·</span>
                        <span>{post.createdAt}</span>
                        <span className="hidden sm:inline">·</span>
                        <span className="basis-full text-indigo-700 sm:basis-auto">{post.courseName}</span>
                      </div>
                      <h2 className="mt-2 font-display text-xl font-semibold leading-snug text-ink-900">{post.title}</h2>
                      <p className="mt-2 text-sm leading-6 text-ink-600">{post.content}</p>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {post.tags.map((tag) => <span key={tag} className="rounded-full bg-ink-50 px-3 py-1 text-xs text-ink-500">#{tag}</span>)}
                      </div>
                      <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-ink-100 pt-4">
                        <button
                          type="button"
                          aria-label="点赞"
                          aria-pressed={post.liked}
                          onClick={() => toggleLike(post.id)}
                          className={cn('btn-ghost px-2', post.liked && 'text-red-600')}
                        >
                          <Heart size={16} fill={post.liked ? 'currentColor' : 'none'} /> {post.likes}
                        </button>
                        <button
                          type="button"
                          aria-label="展开回复"
                          aria-expanded={expanded}
                          onClick={() => setExpandedPostId(expanded ? null : post.id)}
                          className="btn-ghost px-2"
                        >
                          <MessageCircle size={16} /> {post.replies.length}
                        </button>
                        <button
                          type="button"
                          aria-label="收藏"
                          aria-pressed={post.bookmarked}
                          onClick={() => toggleBookmark(post.id)}
                          className={cn('btn-ghost ml-auto px-2', post.bookmarked && 'text-amber-700')}
                        >
                          <Bookmark size={16} fill={post.bookmarked ? 'currentColor' : 'none'} />
                        </button>
                      </div>

                      {expanded && (
                        <div className="mt-4 space-y-3 border-t border-ink-100 pt-4">
                          {post.replies.map((reply) => (
                            <div key={reply.id} className="flex gap-3 rounded-2xl bg-ink-50 p-3">
                              <img src={reply.avatar} alt="" className="h-8 w-8 shrink-0 rounded-full bg-white object-cover" loading="lazy" />
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-2 text-xs text-ink-400"><span className="font-medium text-ink-700">{reply.author}</span><span>{reply.createdAt}</span></div>
                                <p className="mt-1 text-sm leading-6 text-ink-600">{reply.content}</p>
                              </div>
                            </div>
                          ))}
                          <div className="flex items-end gap-2">
                            <textarea
                              value={replyDrafts[post.id] ?? ''}
                              onChange={(event) => setReplyDrafts((current) => ({ ...current, [post.id]: event.target.value }))}
                              rows={2}
                              maxLength={500}
                              placeholder="写下你的回复..."
                              className="input-field min-h-[3rem] resize-none rounded-xl"
                            />
                            <button type="button" onClick={() => publishReply(post.id)} className="btn-primary h-[3rem] shrink-0 px-4" aria-label="发表回复">
                              <Send size={16} /> <span className="hidden sm:inline">发表回复</span>
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </main>

        <aside className="space-y-4">
          <div className="card-editorial p-5">
            <div className="flex items-center gap-2"><Sparkles className="text-amber-600" size={17} /><h2 className="font-display text-lg font-semibold text-ink-900">热门标签</h2></div>
            <div className="mt-4 flex flex-wrap gap-2">
              {popularTags.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  onClick={() => setSelectedTag(tag)}
                  aria-pressed={selectedTag === tag}
                  className={cn(
                    'rounded-full border px-3 py-1.5 text-xs transition-colors',
                    selectedTag === tag
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-800'
                      : 'border-ink-100 text-ink-500 hover:border-indigo-200 hover:bg-indigo-50/60 hover:text-indigo-800',
                  )}
                >
                  #{tag}
                </button>
              ))}
            </div>
          </div>
          <div className="card-editorial p-5">
            <div className="flex items-center gap-2"><Users className="text-indigo-700" size={17} /><h2 className="font-display text-lg font-semibold text-ink-900">社区约定</h2></div>
            <ul className="mt-3 space-y-2 text-xs leading-5 text-ink-500">
              <li>明确描述课程背景和实际问题</li>
              <li>尊重不同学习进度与解题思路</li>
              <li>避免发布答案交易和无关广告</li>
            </ul>
          </div>
        </aside>
      </div>
    </div>
  );
}
