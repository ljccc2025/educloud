import { create } from 'zustand';
import type { CommunityPost, CommunityReply } from './types';

const avatar = (name: string) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}&backgroundColor=1e1b4b&textColor=ffffff&fontWeight=500&fontSize=24`;

const initialPosts: CommunityPost[] = [
  {
    id: 104,
    title: '如何建立稳定的 React 性能分析流程？',
    content: '最近在学习 React Profiler，想把组件重渲染、网络请求和交互延迟放进一套固定排查流程。大家通常会先看哪些指标？',
    author: '陈雨桐',
    avatar: avatar('陈雨桐'),
    courseName: '前端工程化与 React 进阶',
    tags: ['React', '性能优化'],
    createdAt: '2026-08-18 10:25',
    likes: 18,
    liked: false,
    bookmarked: false,
    replies: [
      { id: 1001, author: '刘浩然', avatar: avatar('刘浩然'), content: '我一般先用 Profiler 找提交时间异常的组件，再结合 Network 面板排除请求瀑布。', createdAt: '2026-08-18 10:42' },
    ],
  },
  {
    id: 103,
    title: '分享一份高等数学错题整理模板',
    content: '模板分为知识点、错误原因、正确思路和一周后复做四栏。坚持两周后，极限与导数综合题的错误率明显下降。',
    author: '周子涵',
    avatar: avatar('周子涵'),
    courseName: '高等数学精讲：从极限到微积分',
    tags: ['高等数学', '学习方法'],
    createdAt: '2026-08-17 21:10',
    likes: 36,
    liked: true,
    bookmarked: true,
    replies: [
      { id: 1002, author: '林晓', avatar: avatar('林晓'), content: '四栏结构很清晰，尤其是一周后复做这一步很有用。', createdAt: '2026-08-17 21:36' },
      { id: 1003, author: '张伟', avatar: avatar('张伟'), content: '可以再加一栏记录同类题的识别关键词。', createdAt: '2026-08-17 22:05' },
    ],
  },
  {
    id: 102,
    title: 'Pandas 数据清洗作业中的缺失值处理',
    content: '同一列同时存在空字符串、None 和异常占位符时，是先统一替换为 NaN，再按业务规则填充更合理吗？',
    author: '王可欣',
    avatar: avatar('王可欣'),
    courseName: 'Python 数据分析实战',
    tags: ['Python', 'Pandas'],
    createdAt: '2026-08-17 16:45',
    likes: 12,
    liked: false,
    bookmarked: false,
    replies: [],
  },
  {
    id: 101,
    title: '微服务项目应该怎样划分服务边界？',
    content: '课程案例按业务能力拆分服务，但实际项目中经常出现共享数据。想请教大家如何判断应该调用接口，还是通过事件同步数据。',
    author: '李晨阳',
    avatar: avatar('李晨阳'),
    courseName: 'Java 后端架构设计',
    tags: ['微服务', '架构设计'],
    createdAt: '2026-08-16 19:20',
    likes: 27,
    liked: false,
    bookmarked: true,
    replies: [
      { id: 1004, author: '赵文博', avatar: avatar('赵文博'), content: '先根据业务一致性要求判断，强一致查询走接口，跨域状态传播更适合事件。', createdAt: '2026-08-16 20:01' },
    ],
  },
];

interface NewPostInput {
  title: string;
  content: string;
  courseName: string;
  tags: string[];
}

interface CommunityState {
  posts: CommunityPost[];
  addPost: (input: NewPostInput) => boolean;
  toggleLike: (postId: number) => void;
  toggleBookmark: (postId: number) => void;
  addReply: (postId: number, content: string) => boolean;
}

export const useCommunityStore = create<CommunityState>((set, get) => ({
  posts: initialPosts,
  addPost: (input) => {
    const title = input.title.trim();
    const content = input.content.trim();
    if (!title || !content) return false;

    const nextId = Math.max(0, ...get().posts.map((post) => post.id)) + 1;
    const post: CommunityPost = {
      id: nextId,
      title,
      content,
      author: '林晓',
      avatar: avatar('林晓'),
      courseName: input.courseName || '学习交流',
      tags: input.tags.map((tag) => tag.trim()).filter(Boolean).slice(0, 3),
      createdAt: '刚刚',
      likes: 0,
      liked: false,
      bookmarked: false,
      replies: [],
    };
    set((state) => ({ posts: [post, ...state.posts] }));
    return true;
  },
  toggleLike: (postId) => set((state) => ({
    posts: state.posts.map((post) => post.id === postId
      ? { ...post, liked: !post.liked, likes: Math.max(0, post.likes + (post.liked ? -1 : 1)) }
      : post),
  })),
  toggleBookmark: (postId) => set((state) => ({
    posts: state.posts.map((post) => post.id === postId ? { ...post, bookmarked: !post.bookmarked } : post),
  })),
  addReply: (postId, content) => {
    const trimmedContent = content.trim();
    if (!trimmedContent) return false;

    const allReplies = get().posts.flatMap((post) => post.replies);
    const reply: CommunityReply = {
      id: Math.max(1000, ...allReplies.map((item) => item.id)) + 1,
      author: '林晓',
      avatar: avatar('林晓'),
      content: trimmedContent,
      createdAt: '刚刚',
    };
    set((state) => ({
      posts: state.posts.map((post) => post.id === postId ? { ...post, replies: [...post.replies, reply] } : post),
    }));
    return true;
  },
}));
