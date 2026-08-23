import dayjs from 'dayjs';
import { http, TOKEN_KEY, apiErrorText, type ApiEnvelope } from './http';
import type {
  Course, Chapter, Review, LiveRoom, ChatMessage,
  Assignment, Exam, Order, StudentUser, HomeStats,
  CategoryShowcase, Category, CourseLevel, PaginatedResponse,
} from '../types';
import { createMockCheckoutApi } from './mockCheckoutApi';

// ---------- helpers ----------
const delay = <T>(data: T, ms = 300): Promise<T> =>
  new Promise((r) => setTimeout(() => r(data), ms));

const avatar = (seed: string) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(seed)}&backgroundColor=1e1b4b,d97706,4f46e5,b45309&textColor=ffffff&fontWeight=500&fontSize=24`;

const cover = (seed: number) =>
  `https://picsum.photos/seed/edu${seed}/600/360`;

// ---------- current user ----------
export const currentUser: StudentUser = {
  id: '1',
  username: 'student001',
  realName: '林晓',
  email: 'linxiao@educloud.cn',
  phone: '138****8888',
  avatar: avatar('林晓'),
  bio: '热爱学习，追求卓越。正在系统学习计算机科学与数据分析。',
  joinDate: '2024-03-15',
  learnedCourses: 8,
  learnedHours: 126,
  certificates: 3,
  consecutiveDays: 23,
};

// ---------- categories ----------
const categoryDefinitions: Omit<CategoryShowcase, 'courseCount'>[] = [
  { name: '计算机', icon: 'Cpu', studentCount: 38420, description: '编程、算法、人工智能与软件工程' },
  { name: '数学', icon: 'Sigma', studentCount: 24680, description: '从基础代数到高等数学的系统课程' },
  { name: '语言学习', icon: 'Languages', studentCount: 42150, description: '英语、日语、韩语等多语种学习' },
  { name: '经济管理', icon: 'TrendingUp', studentCount: 18930, description: '经济学、管理学与商业分析' },
  { name: '文学艺术', icon: 'BookOpen', studentCount: 15240, description: '中外文学、艺术鉴赏与创作' },
  { name: '设计', icon: 'Palette', studentCount: 21360, description: 'UI/UX、平面设计与创意表达' },
  { name: '心理学', icon: 'Brain', studentCount: 16780, description: '认知心理学、社会心理学导论' },
  { name: '法律', icon: 'Scale', studentCount: 8920, description: '法学基础与实务专题' },
  { name: '音乐', icon: 'Music', studentCount: 11450, description: '乐器演奏、乐理与音乐欣赏' },
  { name: '哲学', icon: 'Compass', studentCount: 6340, description: '东西方哲学经典与思辨' },
];

// ---------- courses ----------
const courseData: Array<{
  title: string; teacher: string; teacherTitle: string;
  category: Category; level: CourseLevel; price: number; originalPrice?: number;
}> = [
  { title: '高等数学精讲：从极限到微积分', teacher: '李明远', teacherTitle: '数学系教授 · 博导', category: '数学', level: 'BEGINNER', price: 199, originalPrice: 299 },
  { title: 'Python 数据分析实战', teacher: '王雪琴', teacherTitle: '数据科学高级工程师', category: '计算机', level: 'INTERMEDIATE', price: 299, originalPrice: 399 },
  { title: '英语口语突破训练营', teacher: '陈建国', teacherTitle: '剑桥英语认证教师', category: '语言学习', level: 'BEGINNER', price: 0 },
  { title: '机器学习入门与实践', teacher: '赵文博', teacherTitle: 'AI 研究员 · 前谷歌工程师', category: '计算机', level: 'ADVANCED', price: 499, originalPrice: 699 },
  { title: '中国现代文学经典解读', teacher: '周淑芬', teacherTitle: '文学院副教授', category: '文学艺术', level: 'BEGINNER', price: 99 },
  { title: '前端工程化与 React 进阶', teacher: '吴志强', teacherTitle: '前端架构师', category: '计算机', level: 'INTERMEDIATE', price: 399, originalPrice: 499 },
  { title: '微观经济学原理', teacher: '郑丽华', teacherTitle: '经济学院教授', category: '经济管理', level: 'BEGINNER', price: 199 },
  { title: '摄影构图与光影美学', teacher: '黄敏', teacherTitle: '国家一级摄影师', category: '设计', level: 'BEGINNER', price: 149, originalPrice: 199 },
  { title: 'Java 后端架构设计', teacher: '徐强', teacherTitle: '资深架构师 · 10年经验', category: '计算机', level: 'ADVANCED', price: 599, originalPrice: 799 },
  { title: '心理学导论：认识自我', teacher: '孙丽', teacherTitle: '心理学博士', category: '心理学', level: 'BEGINNER', price: 0 },
  { title: '线性代数及其应用', teacher: '李明远', teacherTitle: '数学系教授 · 博导', category: '数学', level: 'INTERMEDIATE', price: 249 },
  { title: 'UI/UX 设计思维方法论', teacher: '马超', teacherTitle: '首席设计师', category: '设计', level: 'INTERMEDIATE', price: 349, originalPrice: 449 },
  { title: '财务管理与报表分析', teacher: '何勇', teacherTitle: 'CPA · 财务总监', category: '经济管理', level: 'INTERMEDIATE', price: 299 },
  { title: '日语零基础至 N2', teacher: '高桥美咲', teacherTitle: 'JLPT 特级讲师', category: '语言学习', level: 'BEGINNER', price: 399, originalPrice: 599 },
  { title: '算法与数据结构精讲', teacher: '赵文博', teacherTitle: 'AI 研究员 · 前谷歌工程师', category: '计算机', level: 'INTERMEDIATE', price: 349 },
  { title: '商务谈判与沟通技巧', teacher: '罗琳', teacherTitle: 'MBA · 企业培训师', category: '经济管理', level: 'BEGINNER', price: 129 },
  { title: 'Docker 与 Kubernetes 实战', teacher: '吴志强', teacherTitle: '前端架构师', category: '计算机', level: 'ADVANCED', price: 449, originalPrice: 599 },
  { title: '西方哲学史讲演录', teacher: '郑浩', teacherTitle: '哲学系教授', category: '哲学', level: 'BEGINNER', price: 179 },
  { title: '产品经理从入门到精通', teacher: '梁宇', teacherTitle: '高级产品总监', category: '经济管理', level: 'BEGINNER', price: 279, originalPrice: 379 },
  { title: '吉他弹唱速成教程', teacher: '谢雯', teacherTitle: '独立音乐人', category: '音乐', level: 'BEGINNER', price: 199 },
  { title: '刑事诉讼法专题', teacher: '韩松', teacherTitle: '法学博士 · 执业律师', category: '法律', level: 'ADVANCED', price: 329 },
  { title: '深度学习与计算机视觉', teacher: '赵文博', teacherTitle: 'AI 研究员 · 前谷歌工程师', category: '计算机', level: 'ADVANCED', price: 699, originalPrice: 899 },
  { title: '写作训练营：非虚构写作', teacher: '唐悦', teacherTitle: '畅销书作家', category: '文学艺术', level: 'INTERMEDIATE', price: 199 },
  { title: '围棋入门到业余初段', teacher: '冯刚', teacherTitle: '业余 6 段', category: '文学艺术', level: 'BEGINNER', price: 0 },
];

function makeChapters(prefix: string): Chapter[] {
  const titles = [
    '课程导论与学习指南', '基础概念精讲', '核心原理剖析',
    '实战案例（上）', '实战案例（下）', '进阶技巧与最佳实践',
    '常见问题与避坑指南', '综合项目演练', '课程总结与展望',
  ];
  return titles.map((t, i) => ({
    id: i + 1,
    title: `${prefix} · ${t}`,
    duration: `${12 + i * 3}:${String((i * 7) % 60).padStart(2, '0')}`,
    free: i < 2,
    completed: i < 3,
    coursewares: [{
      id: i + 1,
      title: t,
      type: i % 4 === 3 ? 'quiz' : 'video',
      duration: 12 + i * 3,
      completed: i < 3,
    }],
  }));
}

const reviewTexts = [
  '老师讲解非常清晰，深入浅出，重点突出。课程内容编排合理，循序渐进，让我从零开始掌握了核心知识。强烈推荐！',
  '课程质量很高，实战项目很有价值。老师对知识点的把握很精准，答疑也很及时。物超所值。',
  '作为一个零基础的学员，这门课让我受益匪浅。视频清晰，课件详实，每个章节都有配套练习。',
  '内容扎实，老师经验丰富。有些章节难度较大，但反复观看后都能理解。希望能多出进阶课程。',
  '非常棒的课程！理论与实践结合得很好，案例都是真实场景，学完就能应用到工作中。',
  '老师讲课风格幽默风趣，不会觉得枯燥。知识点覆盖全面，课后作业设计得很用心。',
];

const reviewers = ['张伟', '王芳', '李娜', '刘洋', '陈静', '杨帆', '赵磊', '黄敏'];

function makeReviews(seed: number): Review[] {
  const count = 3 + (seed % 4);
  return Array.from({ length: count }, (_, i) => ({
    id: seed * 10 + i,
    userName: reviewers[(seed + i) % reviewers.length],
    avatar: avatar(reviewers[(seed + i) % reviewers.length]),
    rating: 4 + ((seed + i) % 2),
    content: reviewTexts[(seed + i) % reviewTexts.length],
    date: dayjs().subtract((seed + i) * 5, 'day').format('YYYY-MM-DD'),
  }));
}

function generateCourses(): Course[] {
  return courseData.map((c, i) => {
    const enrolled = i < 6;
    const progress = enrolled ? [100, 75, 45, 30, 60, 20][i] : 0;
    return {
      id: i + 1,
      title: c.title,
      cover: cover(i + 1),
      teacherName: c.teacher,
      teacherAvatar: avatar(c.teacher),
      teacherTitle: c.teacherTitle,
      category: c.category,
      level: c.level,
      price: c.price,
      originalPrice: c.originalPrice,
      description: `本课程由${c.teacherTitle}${c.teacher}老师主讲，系统讲解${c.title}的核心知识与实战技能。课程注重理论与实践结合，通过大量真实案例帮助学员深入理解并灵活运用。无论你是零基础入门还是有经验希望进阶，都能从中获得实质性提升。`,
      whatYouLearn: [
        `掌握${c.category}领域的核心概念与理论框架`,
        '能够独立完成从需求分析到方案落地的全流程',
        '熟练运用行业主流工具与方法论',
        '具备解决实际工作中复杂问题的能力',
        '获得可展示的项目作品集',
        '建立持续学习和进阶的知识体系',
      ],
      requirements: [
        '具备基本的学习意愿和时间投入',
        '建议每周投入 5-8 小时学习',
        '部分进阶章节需要相关基础知识',
      ],
      chapters: makeChapters(`第${i + 1}章`),
      reviews: makeReviews(i + 1),
      studentCount: 1200 + Math.floor(Math.random() * 8000) + i * 137,
      rating: Number((4.2 + Math.random() * 0.7).toFixed(1)),
      reviewCount: 80 + Math.floor(Math.random() * 600) + i * 13,
      totalDuration: `${8 + i * 2}小时${20 + (i * 7) % 40}分`,
      lastUpdated: dayjs().subtract(i * 12, 'day').format('YYYY-MM-DD'),
      enrolled,
      progress,
    };
  });
}

const courses: Course[] = generateCourses();

export const categories: CategoryShowcase[] = categoryDefinitions.map((category) => ({
  ...category,
  courseCount: courses.filter((course) => course.category === category.name).length,
}));

// ---------- live rooms ----------
export const liveRooms: LiveRoom[] = [
  {
    id: 1, courseId: 1, courseTitle: '高等数学精讲：从极限到微积分',
    teacherName: '李明远', teacherAvatar: avatar('李明远'),
    title: '第三章直播答疑：导数的应用', cover: cover(1),
    status: 'LIVE', startTime: dayjs().format('YYYY-MM-DD HH:mm'),
    viewerCount: 328, duration: '进行中 45:23',
  },
  {
    id: 2, courseId: 2, courseTitle: 'Python 数据分析实战',
    teacherName: '王雪琴', teacherAvatar: avatar('王雪琴'),
    title: 'Pandas 高级操作实战直播课', cover: cover(2),
    status: 'SCHEDULED', startTime: dayjs().add(1, 'day').hour(20).minute(0).format('YYYY-MM-DD HH:mm'),
    viewerCount: 156, duration: '预计 90 分钟',
  },
  {
    id: 3, courseId: 4, courseTitle: '机器学习入门与实践',
    teacherName: '赵文博', teacherAvatar: avatar('赵文博'),
    title: '神经网络反向传播推导（直播）', cover: cover(4),
    status: 'SCHEDULED', startTime: dayjs().add(2, 'day').hour(19).minute(30).format('YYYY-MM-DD HH:mm'),
    viewerCount: 243, duration: '预计 120 分钟',
  },
  {
    id: 4, courseId: 6, courseTitle: '前端工程化与 React 进阶',
    teacherName: '吴志强', teacherAvatar: avatar('吴志强'),
    title: 'React Server Components 深度解析', cover: cover(6),
    status: 'ENDED', startTime: dayjs().subtract(1, 'day').format('YYYY-MM-DD HH:mm'),
    viewerCount: 892, duration: '回放 1:45:30',
  },
];

// ---------- chat messages ----------
export const initialMessages: ChatMessage[] = [
  { id: 1, userName: '李明远', avatar: avatar('李明远'), content: '大家好，今天我们来讲解导数的应用，有问题随时在聊天区提问。', time: '19:30', isTeacher: true },
  { id: 2, userName: '张伟', avatar: avatar('张伟'), content: '老师好！请问洛必达法则的使用条件是什么？', time: '19:32' },
  { id: 3, userName: '李明远', avatar: avatar('李明远'), content: '好问题！洛必达法则需要满足 0/0 或 ∞/∞ 型，且分子分母都可导。', time: '19:33', isTeacher: true },
  { id: 4, userName: '王芳', avatar: avatar('王芳'), content: '明白了，谢谢老师！', time: '19:34' },
  { id: 5, userName: '刘洋', avatar: avatar('刘洋'), content: '老师能再讲一下泰勒展开吗？', time: '19:36' },
  { id: 6, userName: '陈静', avatar: avatar('陈静'), content: '课件可以下载吗？', time: '19:38' },
  { id: 7, userName: '李明远', avatar: avatar('李明远'), content: '课件在课程资料区可以下载，泰勒展开我们下节课详细讲。', time: '19:39', isTeacher: true },
  { id: 8, userName: '杨帆', avatar: avatar('杨帆'), content: '这节课收获很大！', time: '19:42' },
];

// ---------- assignments ----------
export const assignments: Assignment[] = [
  {
    id: 1, courseId: 1, courseTitle: '高等数学精讲：从极限到微积分',
    title: '第三章习题：导数与微分', description: '完成教材第 45-48 页的全部习题，要求写出完整解题过程。',
    dueDate: dayjs().add(3, 'day').format('YYYY-MM-DD'), status: 'PENDING',
    totalScore: 100,
  },
  {
    id: 2, courseId: 2, courseTitle: 'Python 数据分析实战',
    title: 'Pandas 数据清洗作业', description: '使用 Pandas 对给定数据集进行清洗和初步分析，提交 Jupyter Notebook。',
    dueDate: dayjs().add(5, 'day').format('YYYY-MM-DD'), status: 'PENDING',
    totalScore: 100,
  },
  {
    id: 3, courseId: 6, courseTitle: '前端工程化与 React 进阶',
    title: '实现一个自定义 Hook', description: '编写 useDebounce 和 useLocalStorage 两个自定义 Hook，附带单元测试。',
    dueDate: dayjs().subtract(1, 'day').format('YYYY-MM-DD'), status: 'SUBMITTED',
    totalScore: 100, submitDate: dayjs().subtract(2, 'day').format('YYYY-MM-DD'),
  },
  {
    id: 4, courseId: 4, courseTitle: '机器学习入门与实践',
    title: '线性回归模型实现', description: '使用 NumPy 从零实现线性回归，在波士顿房价数据集上验证。',
    dueDate: dayjs().subtract(7, 'day').format('YYYY-MM-DD'), status: 'GRADED',
    score: 92, totalScore: 100, submitDate: dayjs().subtract(8, 'day').format('YYYY-MM-DD'),
    feedback: '实现完整，代码规范。梯度下降部分可以进一步优化学习率策略。继续保持！',
  },
  {
    id: 5, courseId: 7, courseTitle: '微观经济学原理',
    title: '供需曲线分析报告', description: '选取一个真实商品，分析其供需关系并撰写 2000 字报告。',
    dueDate: dayjs().subtract(3, 'day').format('YYYY-MM-DD'), status: 'OVERDUE',
    totalScore: 100,
  },
  {
    id: 6, courseId: 9, courseTitle: 'Java 后端架构设计',
    title: '微服务架构设计方案', description: '为一个电商系统设计微服务架构，画出架构图并说明技术选型。',
    dueDate: dayjs().add(10, 'day').format('YYYY-MM-DD'), status: 'PENDING',
    totalScore: 100,
  },
];

// ---------- exams ----------
export const exams: Exam[] = [
  {
    id: 1, courseId: 1, courseTitle: '高等数学精讲：从极限到微积分',
    title: '期中测试：极限与连续', description: '涵盖第一至第二章内容，包括选择题、填空题和解答题。',
    duration: 90, totalQuestions: 25, totalScore: 100, passScore: 60,
    status: 'NOT_STARTED',
  },
  {
    id: 2, courseId: 2, courseTitle: 'Python 数据分析实战',
    title: 'NumPy 基础测验', description: '测试 NumPy 数组操作、索引和广播机制的掌握程度。',
    duration: 30, totalQuestions: 15, totalScore: 50, passScore: 30,
    status: 'GRADED', score: 45,
    startTime: dayjs().subtract(5, 'day').format('YYYY-MM-DD HH:mm'),
    endTime: dayjs().subtract(5, 'day').format('YYYY-MM-DD HH:mm'),
  },
  {
    id: 3, courseId: 6, courseTitle: '前端工程化与 React 进阶',
    title: 'React Hooks 专项考试', description: '深入考察 Hooks 原理、使用规则和性能优化。',
    duration: 60, totalQuestions: 20, totalScore: 100, passScore: 70,
    status: 'IN_PROGRESS',
    startTime: dayjs().format('YYYY-MM-DD HH:mm'),
  },
  {
    id: 4, courseId: 4, courseTitle: '机器学习入门与实践',
    title: '监督学习算法测验', description: '线性回归、逻辑回归、决策树和 SVM 的原理与应用。',
    duration: 45, totalQuestions: 18, totalScore: 100, passScore: 60,
    status: 'SUBMITTED',
    startTime: dayjs().subtract(2, 'day').format('YYYY-MM-DD HH:mm'),
    endTime: dayjs().subtract(2, 'day').format('YYYY-MM-DD HH:mm'),
  },
  {
    id: 5, courseId: 10, courseTitle: '心理学导论：认识自我',
    title: '认知心理学章节测试', description: '感知、注意、记忆和思维相关知识测验。',
    duration: 30, totalQuestions: 20, totalScore: 100, passScore: 60,
    status: 'NOT_STARTED',
  },
];

// ---------- orders ----------
function generateOrders(): Order[] {
  const list: Order[] = [];
  const methods: Order['paymentMethod'][] = ['ALIPAY', 'WECHAT'];
  const statuses: Order['status'][] = [
    'PAID',
    'PAID',
    'PAID',
    'PENDING_PAYMENT',
    'REFUNDED',
    'CANCELLED',
  ];
  const purchased = courses.filter((c) => c.price > 0).slice(0, 8);
  purchased.forEach((c, i) => {
    const createdAt = dayjs().subtract(i * 12 + 3, 'day');
    const status = statuses[i % statuses.length];
    list.push({
      id: String(i + 1),
      orderNo: `EC${createdAt.format('YYYYMMDD')}${String(20000 + i).padStart(5, '0')}`,
      courseId: c.id,
      courseTitle: c.title,
      courseCover: c.cover,
      originalAmount: c.originalPrice ?? c.price,
      payableAmount: c.price,
      currency: 'CNY',
      paymentMethod: methods[i % methods.length],
      status,
      createdAt: createdAt.format('YYYY-MM-DD HH:mm:ss'),
      expiresAt: createdAt.add(30, 'minute').format('YYYY-MM-DD HH:mm:ss'),
      paidAt: status === 'PAID'
        ? createdAt.add(2, 'minute').format('YYYY-MM-DD HH:mm:ss')
        : undefined,
    });
  });
  return list;
}

export const orders: Order[] = generateOrders();

// ---------- home stats ----------
export const homeStats: HomeStats = {
  totalCourses: 486,
  totalStudents: 12846,
  totalTeachers: 86,
  totalHours: 3200,
};

// ---------- API ----------
export const courseApi = {
  getAll: (): Promise<Course[]> => delay(courses),

  getById: (id: number): Promise<Course | undefined> =>
    delay(courses.find((c) => c.id === id)),

  getFeatured: (): Promise<Course[]> => delay(courses.slice(0, 8)),

  getPopular: (): Promise<Course[]> =>
    delay([...courses].sort((a, b) => b.studentCount - a.studentCount).slice(0, 6)),

  getNewReleases: (): Promise<Course[]> =>
    delay([...courses].sort((a, b) => b.id - a.id).slice(0, 4)),

  getFree: (): Promise<Course[]> => delay(courses.filter((c) => c.price === 0)),

  getEnrolled: (): Promise<Course[]> => delay(courses.filter((c) => c.enrolled)),

  search: (params: {
    keyword?: string;
    category?: string;
    level?: string;
    price?: string;
    sort?: string;
    page?: number;
    pageSize?: number;
  }): Promise<PaginatedResponse<Course>> => {
    let filtered = [...courses];
    if (params.keyword) {
      const kw = params.keyword.toLowerCase();
      filtered = filtered.filter(
        (c) =>
          c.title.toLowerCase().includes(kw) ||
          c.teacherName.toLowerCase().includes(kw) ||
          c.description.toLowerCase().includes(kw),
      );
    }
    if (params.category && params.category !== 'ALL') {
      filtered = filtered.filter((c) => c.category === params.category);
    }
    if (params.level && params.level !== 'ALL') {
      filtered = filtered.filter((c) => c.level === params.level);
    }
    if (params.price === 'FREE') filtered = filtered.filter((c) => c.price === 0);
    if (params.price === 'PAID') filtered = filtered.filter((c) => c.price > 0);

    if (params.sort === 'popular') filtered.sort((a, b) => b.studentCount - a.studentCount);
    if (params.sort === 'rating') filtered.sort((a, b) => b.rating - a.rating);
    if (params.sort === 'price_asc') filtered.sort((a, b) => a.price - b.price);
    if (params.sort === 'price_desc') filtered.sort((a, b) => b.price - a.price);
    if (params.sort === 'newest') filtered.sort((a, b) => b.id - a.id);

    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 12;
    const start = (page - 1) * pageSize;
    return delay({
      list: filtered.slice(start, start + pageSize),
      total: filtered.length,
      page,
      pageSize,
    });
  },

  enroll: (id: number): Promise<{ success: boolean }> => {
    const c = courses.find((x) => x.id === id);
    if (c) { c.enrolled = true; c.progress = 0; }
    return delay({ success: true });
  },

  updateProgress: (courseId: number, chapterId: number): Promise<{ success: boolean }> => {
    const c = courses.find((x) => x.id === courseId);
    if (c) {
      const ch = c.chapters.find((x) => x.id === chapterId);
      if (ch) ch.completed = true;
      const done = c.chapters.filter((x) => x.completed).length;
      c.progress = Math.round((done / c.chapters.length) * 100);
    }
    return delay({ success: true });
  },
};

export const liveApi = {
  getRooms: (): Promise<LiveRoom[]> => delay(liveRooms),
  getById: (id: number): Promise<LiveRoom | undefined> =>
    delay(liveRooms.find((r) => r.id === id)),
  getMessages: (): Promise<ChatMessage[]> => delay(initialMessages),
};

export const assignmentApi = {
  getAll: (): Promise<Assignment[]> => delay(assignments),
};

export const examApi = {
  getAll: (): Promise<Exam[]> => delay(exams),
};

export const { orderApi, paymentApi } = createMockCheckoutApi({
  seedOrders: orders,
  courses: {
    getCourse: (courseId) => courses.find((course) => course.id === courseId),
    grantCourseAccess: (courseId) => {
      const course = courses.find((item) => item.id === courseId);
      if (course) {
        course.enrolled = true;
        course.progress = 0;
      }
    },
  },
});

export const userApi = {
  getProfile: (): Promise<StudentUser> => delay(currentUser),
  updateProfile: (data: Partial<StudentUser>): Promise<StudentUser> => {
    Object.assign(currentUser, data);
    return delay(currentUser);
  },
};

// ---------- 真实认证（M03 联调）：经 Gateway 与 educloud-user 服务对接 ----------
export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  userType: string;
  roles: string[];
  permissions: string[];
  avatarUrl?: string;
  /** 当前头像 fileId（M04：全量 PATCH 需携带，/me 返回保证刷新不丢失）。 */
  avatarFileId?: string;
}

function mapAuthUser(a: AuthUser): StudentUser {
  return {
    id: a.id,
    username: a.username,
    realName: a.displayName || a.username,
    email: '',
    phone: '',
    avatar: a.avatarUrl ?? avatar(a.username),
    avatarUrl: a.avatarUrl,
    avatarFileId: a.avatarFileId,
    bio: '',
    joinDate: new Date().toISOString().slice(0, 10),
    learnedCourses: 0,
    learnedHours: 0,
    certificates: 0,
    consecutiveDays: 1,
  };
}

export const authApi = {
  login: async (loginName: string, password: string): Promise<{ token: string; user: StudentUser }> => {
    const resp = await http.post<ApiEnvelope<{ accessToken: string; expiresIn: number; user: AuthUser }>>(
      '/auth/login',
      { loginName, password, portal: 'STUDENT' },
    );
    const token = resp.data.data.accessToken;
    localStorage.setItem(TOKEN_KEY, token);
    const fromLogin = mapAuthUser(resp.data.data.user);
    try {
      // 登录响应不含 avatarUrl（M04：仅 /me 经 File 批量授权解析），补一次 me() 拿真实头像。
      return { token, user: await authApi.me() };
    } catch {
      return { token, user: fromLogin };
    }
  },
  register: async (payload: {
    username: string;
    password: string;
    email: string;
    phone: string;
    displayName?: string;
  }): Promise<void> => {
    try {
      await http.post<ApiEnvelope<null>>('/auth/register', {
        username: payload.username,
        password: payload.password,
        email: payload.email,
        phone: payload.phone,
        displayName: payload.displayName,
      });
    } catch (e) {
      throw new Error(apiErrorText(e));
    }
  },
  me: async (): Promise<StudentUser> => {
    const resp = await http.get<ApiEnvelope<AuthUser>>('/me');
    return mapAuthUser(resp.data.data);
  },
  logout: async (): Promise<void> => {
    try {
      await http.post('/auth/logout');
    } catch {
      // 服务端已尽力撤销；本地始终清理。
    }
    localStorage.removeItem(TOKEN_KEY);
  },
};

export const homeApi = {
  getStats: (): Promise<HomeStats> => delay(homeStats),
  getCategories: (): Promise<CategoryShowcase[]> => delay(categories),
};
