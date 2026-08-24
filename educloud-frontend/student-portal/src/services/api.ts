import dayjs from 'dayjs';
import { http, TOKEN_KEY, apiErrorText, type ApiEnvelope } from './http';
import type {
  LiveRoom, ChatMessage,
  Assignment, Exam, Order, StudentUser, HomeStats,
  CategoryShowcase,
} from '../types';
import { courseApi } from './courseApi';
import { createMockCheckoutApi } from './mockCheckoutApi';

// ---------- helpers ----------
const delay = <T>(data: T, ms = 300): Promise<T> =>
  new Promise((r) => setTimeout(() => r(data), ms));

const avatar = (seed: string) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(seed)}&backgroundColor=1e1b4b,d97706,4f46e5,b45309&textColor=ffffff&fontWeight=500&fontSize=24`;

export const cover = (seed: number) =>
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

// ---------- categories（首页展示 mock，保留非课程 mock） ----------
const categoryDefinitions: CategoryShowcase[] = [
  { name: '计算机', icon: 'Cpu', courseCount: 7, studentCount: 38420, description: '编程、算法、人工智能与软件工程' },
  { name: '数学', icon: 'Sigma', courseCount: 2, studentCount: 24680, description: '从基础代数到高等数学的系统课程' },
  { name: '语言学习', icon: 'Languages', courseCount: 2, studentCount: 42150, description: '英语、日语、韩语等多语种学习' },
  { name: '经济管理', icon: 'TrendingUp', courseCount: 3, studentCount: 18930, description: '经济学、管理学与商业分析' },
  { name: '文学艺术', icon: 'BookOpen', courseCount: 2, studentCount: 15240, description: '中外文学、艺术鉴赏与创作' },
  { name: '设计', icon: 'Palette', courseCount: 2, studentCount: 21360, description: 'UI/UX、平面设计与创意表达' },
  { name: '心理学', icon: 'Brain', courseCount: 1, studentCount: 16780, description: '认知心理学、社会心理学导论' },
  { name: '法律', icon: 'Scale', courseCount: 1, studentCount: 8920, description: '法学基础与实务专题' },
  { name: '音乐', icon: 'Music', courseCount: 1, studentCount: 11450, description: '乐器演奏、乐理与音乐欣赏' },
  { name: '哲学', icon: 'Compass', courseCount: 1, studentCount: 6340, description: '东西方哲学经典与思辨' },
];

export const categories: CategoryShowcase[] = categoryDefinitions;

// ---------- 订单/结算 mock 种子（课程已切真实 API；此种子仅供订单页与结算 mock 演示） ----------
const mockCourseSeeds: Array<{ id: string; title: string; price: number; cover: string }> = [
  { id: '1', title: '高等数学精讲：从极限到微积分', price: 199, cover: cover(1) },
  { id: '2', title: 'Python 数据分析实战', price: 299, cover: cover(2) },
  { id: '3', title: '机器学习入门与实践', price: 499, cover: cover(4) },
  { id: '4', title: '前端工程化与 React 进阶', price: 399, cover: cover(6) },
  { id: '5', title: 'Java 后端架构设计', price: 599, cover: cover(9) },
  { id: '6', title: '日语零基础至 N2', price: 399, cover: cover(14) },
  { id: '7', title: '算法与数据结构精讲', price: 349, cover: cover(15) },
  { id: '8', title: 'Docker 与 Kubernetes 实战', price: 449, cover: cover(17) },
];

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
  const purchased = mockCourseSeeds.filter((c) => c.price > 0).slice(0, 8);
  purchased.forEach((c, i) => {
    const createdAt = dayjs().subtract(i * 12 + 3, 'day');
    const status = statuses[i % statuses.length];
    list.push({
      id: String(i + 1),
      orderNo: `EC${createdAt.format('YYYYMMDD')}${String(20000 + i).padStart(5, '0')}`,
      userId: currentUser.id,
      courseId: c.id,
      courseTitle: c.title,
      courseCover: c.cover,
      originalAmount: c.price,
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
export { courseApi };

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
    getCourse: async (courseId: string) => {
      const seeded = mockCourseSeeds.find((c) => c.id === courseId);
      if (seeded) return seeded;
      try {
        const course = await courseApi.getById(courseId);
        return {
          id: course.id,
          title: course.title,
          price: Number(course.price),
          cover: course.coverUrl ?? cover(0),
        };
      } catch {
        return undefined;
      }
    },
    grantCourseAccess: () => {},
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
  /** 个人简介（/me 返回，保证刷新后表单回显不丢失）。 */
  bio?: string;
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
    bio: a.bio ?? '',
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