import dayjs from 'dayjs';
import { http, TOKEN_KEY, type ApiEnvelope } from './http';
import { searchAdminApi } from './searchAdminApi';
export { searchAdminApi };
import type {
  User,
  Course,
  ContentItem,
  Order,
  SystemConfig,
  AuditLog,
  FinanceStats,
  DashboardStats,
  UserGrowthPoint,
  CategoryStat,
  OrderStatusStat,
  ActivityItem,
  PaginatedResponse,
  CourseStatus,
  MonthlyRevenue,
} from '../types';

// ---------- 工具函数 ----------
const delay = <T>(data: T, ms = 300): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(data), ms));

const avatar = (seed: string) =>
  `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(seed)}&backgroundColor=1e1b4b,d97706,4f46e5,b45309&textColor=ffffff`;

const cover = (seed: number) =>
  `https://picsum.photos/seed/course${seed}/400/240`;

// ---------- 管理员 ----------
export interface AdminUser {
  id: string;
  username: string;
  realName: string;
  email: string;
  avatar: string;
  avatarUrl?: string;
  role: string;
  lastLogin: string;
}

export const adminUser: AdminUser = {
  id: '1',
  username: 'admin',
  realName: '超级管理员',
  email: 'admin@educloud.cn',
  avatar: avatar('超级管理员'),
  role: 'SUPER_ADMIN',
  lastLogin: dayjs().subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss'),
};

// ---------- 用户数据 ----------
const chineseNames = [
  '张伟', '王芳', '李娜', '刘洋', '陈静', '杨帆', '赵磊', '黄敏',
  '周杰', '吴婷', '徐强', '孙丽', '马超', '朱琳', '胡军', '郭涛',
  '林雪', '何勇', '高翔', '罗琳', '郑浩', '梁宇', '谢雯', '韩松',
  '唐悦', '冯刚', '于洋', '董洁', '萧然', '程曦', '曹莹', '袁博',
  '邓辉', '许晴', '傅聪', '沈瑶', '曾毅', '彭飞', '吕娜', '苏畅',
  '蒋雯', '蔡明', '贾玲', '丁宁', '魏巍', '薛峰', '叶舟', '阎肃',
  '余华', '潘虹', '杜鹃', '戴琦', '夏雨', '钟楚曦', '汪涵', '田雨',
  '任达华', '姜文', '范冰', '方文山', '石兆琪', '谭松韵', '廖凡', '邹市明',
  '熊乃瑾', '金士杰', '陆毅', '郝蕾', '孔琳', '白宇', '崔健', '康辉',
  '毛不易', '邱泽', '秦昊', '江一燕', '史航', '顾长卫', '侯勇', '邵兵',
];

const roles: User['role'][] = ['STUDENT', 'TEACHER', 'ADMIN', 'STUDENT', 'STUDENT', 'TEACHER', 'STUDENT', 'STUDENT'];

function generateUsers(): User[] {
  return chineseNames.map((name, i) => {
    const role = roles[i % roles.length];
    const pinyin = `user${1000 + i}`;
    return {
      id: i + 1,
      username: pinyin,
      nickname: name,
      email: `${pinyin}@educloud.cn`,
      avatar: '',
      role,
      status: i % 13 === 0 ? 'DISABLED' : 'ACTIVE',
      phone: `138${String(10000000 + i).padStart(8, '0')}`,
      registerDate: dayjs().subtract(400 - i * 4, 'day').format('YYYY-MM-DD'),
      lastLogin: dayjs().subtract(i * 3, 'hour').format('YYYY-MM-DD HH:mm'),
    };
  });
}

const users: User[] = generateUsers();

// ---------- 课程数据 ----------
const courseTitles = [
  '高等数学精讲：从极限到微积分',
  'Python 数据分析实战',
  '英语口语突破训练营',
  '机器学习入门与实践',
  '中国现代文学经典解读',
  '前端工程化与 React 进阶',
  '微观经济学原理',
  '摄影构图与光影美学',
  'Java 后端架构设计',
  '心理学导论：认识自我',
  '线性代数及其应用',
  'UI/UX 设计思维方法论',
  '财务管理与报表分析',
  '日语零基础至 N2',
  '算法与数据结构精讲',
  '商务谈判与沟通技巧',
  'Docker 与 Kubernetes 实战',
  '西方哲学史讲演录',
  '产品经理从入门到精通',
  '吉他弹唱速成教程',
  '刑事诉讼法专题',
  '深度学习与计算机视觉',
  '写作训练营：非虚构写作',
  '围棋入门到业余初段',
];

const categories = ['计算机', '数学', '语言学习', '经济管理', '文学艺术', '设计', '心理学', '法律', '音乐', '哲学'];
const teachers = ['李明远', '王雪琴', '陈建国', '刘慧敏', '赵文博', '周淑芬', '吴志强', '郑丽华'];

function generateCourses(): Course[] {
  return courseTitles.map((title, i) => {
    const cat = categories[i % categories.length];
    const teacher = teachers[i % teachers.length];
    const statusPool: CourseStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'APPROVED', 'APPROVED'];
    const status = i < 8 ? 'PENDING' : statusPool[i % statusPool.length];
    return {
      id: i + 1,
      title,
      cover: cover(i + 1),
      teacherName: teacher,
      category: cat,
      price: [0, 99, 199, 299, 399, 499, 599][i % 7],
      description: `本课程由${teacher}老师主讲，系统讲解${title}相关知识，适合希望深入学习${cat}领域的学员。课程包含理论讲解、案例分析与实战练习。`,
      submittedDate: dayjs().subtract(i * 2, 'day').format('YYYY-MM-DD'),
      status,
      rejectReason: status === 'REJECTED' ? '课程封面图分辨率不足，且简介缺少教学大纲，请补充后重新提交。' : undefined,
    };
  });
}

const courses: Course[] = generateCourses();

// ---------- 内容审核数据 ----------
const contentTitles = [
  '第一章 函数与极限 - 讲义',
  '变量与数据类型 - 视频',
  '发音规则与语调 - PDF',
  '线性回归模型推导 - PPT',
  '鲁迅《狂人日记》赏析 - 视频',
  'Hooks 原理剖析 - 视频',
  '供需曲线与弹性 - PDF',
  '自然光与人造光 - PPT',
  'Spring Boot 集成 MyBatis - 视频',
  '认知偏差与决策 - PDF',
  '矩阵运算专题 - PPT',
  '用户访谈方法论 - 视频',
  '资产负债表解读 - PDF',
  '五十音图精讲 - 视频',
  '动态规划入门 - PPT',
  '谈判准备阶段 - PDF',
  'Pod 生命周期 - 视频',
  '苏格拉底对话 - PPT',
  '需求文档撰写 - PDF',
  'C 大调和弦转换 - 视频',
];

const contentTypes: ContentItem['type'][] = ['VIDEO', 'PDF', 'PPT'];
const contentStatuses: ContentItem['status'][] = ['PENDING', 'PENDING', 'APPROVED', 'REJECTED'];

function generateContent(): ContentItem[] {
  return contentTitles.map((title, i) => ({
    id: i + 1,
    title,
    type: contentTypes[i % contentTypes.length],
    courseName: courseTitles[i % courseTitles.length],
    uploader: teachers[i % teachers.length],
    uploadDate: dayjs().subtract(i, 'day').format('YYYY-MM-DD HH:mm'),
    status: contentStatuses[i % contentStatuses.length],
    fileSize: ['128 MB', '3.2 MB', '8.6 MB', '256 MB', '1.8 MB'][i % 5],
  }));
}

const contentItems: ContentItem[] = generateContent();

// ---------- 订单数据 ----------
function generateOrders(): Order[] {
  const list: Order[] = [];
  const methods: Order['paymentMethod'][] = ['ALIPAY', 'WECHAT'];
  const statuses: Order['status'][] = ['PAID', 'PAID', 'PAID', 'PENDING', 'REFUNDED', 'CANCELLED'];
  for (let i = 0; i < 60; i++) {
    const user = users[i % users.length];
    const course = courses[i % courses.length];
    const amount = course.price || 99;
    const createdAt = dayjs().subtract(i * 5, 'hour');
    list.push({
      id: String(i + 1),
      orderNo: `EC${createdAt.format('YYYYMMDD')}${String(10000 + i).padStart(5, '0')}`,
      userId: user.id,
      userName: user.username,
      userEmail: user.email,
      courseId: course.id,
      courseName: course.title,
      courseTitle: course.title,
      amount,
      payableAmount: amount,
      paymentMethod: methods[i % methods.length],
      status: statuses[i % statuses.length],
      createdAt: createdAt.format('YYYY-MM-DD HH:mm:ss'),
    });
  }
  return list;
}

const orders: Order[] = generateOrders();

// ---------- 系统配置 ----------
export const defaultConfig: SystemConfig = {
  siteName: 'EduCloud 教育云平台',
  siteDescription: '专注于高品质在线教育的学习平台',
  logoUrl: '',
  icp: '京ICP备2024000000号-1',
  smtpHost: 'smtp.exmail.qq.com',
  smtpPort: 465,
  smtpUser: 'noreply@educloud.cn',
  smtpPassword: '********',
  senderName: 'EduCloud 团队',
  senderEmail: 'noreply@educloud.cn',
  minioEndpoint: 'minio.educloud.cn',
  minioPort: 9000,
  minioAccessKey: 'educloud-admin',
  minioSecretKey: '********',
  minioBucket: 'educloud-resources',
  minioUseSSL: true,
  jwtSecret: 'educloud-jwt-secret-key-2024',
  jwtExpiration: 86400,
  passwordMinLength: 8,
  requireEmailVerify: true,
  loginAttemptLimit: 5,
};

// ---------- 操作日志 ----------
const logActions = [
  { action: '用户登录', target: '系统', level: 'INFO' as const },
  { action: '审核课程通过', target: '《高等数学精讲》', level: 'INFO' as const },
  { action: '驳回课程', target: '《摄影构图》', level: 'WARN' as const },
  { action: '禁用用户', target: 'user1013', level: 'WARN' as const },
  { action: '修改系统配置', target: '邮件 SMTP 配置', level: 'INFO' as const },
  { action: '处理退款申请', target: '订单 EC2024080100023', level: 'WARN' as const },
  { action: '登录失败', target: 'admin (密码错误)', level: 'ERROR' as const },
  { action: '导出订单数据', target: '订单列表', level: 'INFO' as const },
  { action: '删除违规课件', target: '《未命名视频》', level: 'WARN' as const },
  { action: '新增管理员', target: 'teacher002', level: 'INFO' as const },
  { action: '数据库备份完成', target: 'educloud_db', level: 'INFO' as const },
  { action: '存储节点连接超时', target: 'MinIO Node-2', level: 'ERROR' as const },
  { action: '审核课件通过', target: '《第一章讲义》', level: 'INFO' as const },
  { action: '重置用户密码', target: 'user1005', level: 'WARN' as const },
  { action: '更新轮播图配置', target: '首页 Banner', level: 'INFO' as const },
  { action: '检测到异常登录', target: 'IP 203.0.113.45', level: 'ERROR' as const },
];

function generateLogs(): AuditLog[] {
  return logActions.map((item, i) => ({
    id: i + 1,
    timestamp: dayjs().subtract(i * 47, 'minute').format('YYYY-MM-DD HH:mm:ss'),
    operator: i % 4 === 0 ? '超级管理员' : ['李明远', '王雪琴', '陈建国'][i % 3],
    action: item.action,
    target: item.target,
    ip: `192.168.${(i * 7) % 255}.${(i * 13) % 255}`,
    level: item.level,
    detail: `${item.action}：${item.target}`,
  }));
}

const logs: AuditLog[] = generateLogs();

// ---------- 仪表盘数据 ----------
function generateUserGrowth(): UserGrowthPoint[] {
  const points: UserGrowthPoint[] = [];
  let cumulative = 12480;
  for (let i = 6; i >= 0; i--) {
    const newUsers = 80 + Math.floor(Math.random() * 120) + i * 8;
    cumulative += newUsers;
    points.push({
      date: dayjs().subtract(i, 'day').format('MM-DD'),
      users: cumulative,
      newUsers,
    });
  }
  return points;
}

function generateCategoryStats(): CategoryStat[] {
  return categories.slice(0, 6).map((name, i) => ({
    name,
    count: [128, 96, 84, 72, 64, 52][i],
  }));
}

function generateOrderStatusStats(): OrderStatusStat[] {
  return [
    { name: '已支付', value: 1842 },
    { name: '待支付', value: 126 },
    { name: '已退款', value: 48 },
    { name: '已取消', value: 73 },
  ];
}

function generateActivities(): ActivityItem[] {
  return [
    { id: 1, user: '李明远', action: '提交了课程审核', target: '《深度学习与计算机视觉》', time: '5 分钟前', type: 'course' },
    { id: 2, user: '王芳', action: '完成了订单支付', target: '¥299.00', time: '12 分钟前', type: 'order' },
    { id: 3, user: '系统', action: '自动备份了数据库', target: 'educloud_db', time: '1 小时前', type: 'system' },
    { id: 4, user: '陈静', action: '注册成为新用户', target: 'STUDENT', time: '2 小时前', type: 'user' },
    { id: 5, user: '赵磊', action: '上传了新课件', target: '《Hooks 原理剖析》', time: '3 小时前', type: 'course' },
    { id: 6, user: '刘洋', action: '申请了退款', target: '订单 EC2024080100023', time: '4 小时前', type: 'order' },
    { id: 7, user: '超级管理员', action: '审核通过了课程', target: '《线性代数及其应用》', time: '5 小时前', type: 'course' },
    { id: 8, user: '周杰', action: '更新了个人资料', target: '教师认证', time: '6 小时前', type: 'user' },
  ];
}

const dashboardStats: DashboardStats = {
  totalUsers: 12846,
  totalCourses: 486,
  totalRevenue: 2846930,
  onlineUsers: 328,
  userGrowth: 12.4,
  courseGrowth: 8.6,
  revenueGrowth: 23.8,
  onlineGrowth: -4.2,
};

const financeStats: FinanceStats = {
  totalRevenue: 2846930,
  monthlyRevenue: 328650,
  refundAmount: 18420,
  pendingSettlement: 56200,
  transactionCount: 2089,
  avgOrderValue: 152.6,
};

function generateMonthlyRevenue() {
  const months: MonthlyRevenue[] = [];
  for (let i = 11; i >= 0; i--) {
    const d = dayjs().subtract(i, 'month');
    months.push({
      month: d.format('MM月'),
      revenue: 180000 + Math.floor(Math.random() * 180000) + i * 8000,
      refund: 8000 + Math.floor(Math.random() * 15000),
    });
  }
  return months;
}

// ---------- API 函数 ----------

// 认证（M03 联调：经 Gateway 与 educloud-user 服务对接）
export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  userType: string;
  roles: string[];
  permissions: string[];
  avatarUrl?: string;
}

function mapAuthAdmin(a: AuthUser): AdminUser {
  return {
    id: a.id,
    username: a.username,
    realName: a.displayName || a.username,
    email: '',
    avatar: a.avatarUrl ?? avatar(a.username),
    avatarUrl: a.avatarUrl,
    role: a.roles[0] ?? 'ADMIN',
    lastLogin: dayjs().format('YYYY-MM-DD HH:mm:ss'),
  };
}

export const authApi = {
  login: async (loginName: string, password: string): Promise<{ token: string; admin: AdminUser }> => {
    const resp = await http.post<ApiEnvelope<{ accessToken: string; expiresIn: number; user: AuthUser }>>(
      '/auth/login',
      { loginName, password, portal: 'ADMIN' },
    );
    const token = resp.data.data.accessToken;
    localStorage.setItem(TOKEN_KEY, token);
    const fromLogin = mapAuthAdmin(resp.data.data.user);
    try {
      // 登录响应不含 avatarUrl（M04：仅 /me 经 File 批量授权解析），补一次 me() 拿真实头像。
      return { token, admin: await authApi.me() };
    } catch {
      return { token, admin: fromLogin };
    }
  },
  me: async (): Promise<AdminUser> => {
    const resp = await http.get<ApiEnvelope<AuthUser>>('/me');
    return mapAuthAdmin(resp.data.data);
  },
  logout: async (): Promise<void> => {
    try {
      await http.post('/auth/logout');
    } catch {
      // 本地始终清理。
    }
    localStorage.removeItem(TOKEN_KEY);
  },
};

// 用户
export const userApi = {
  getUsers: (params?: {
    page?: number;
    pageSize?: number;
    keyword?: string;
    role?: string;
    status?: string;
  }): Promise<PaginatedResponse<User>> => {
    const page = params?.page ?? 1;
    const pageSize = params?.pageSize ?? 10;
    let filtered = [...users];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      filtered = filtered.filter(
        (u) =>
          u.username.toLowerCase().includes(kw) ||
          u.email.toLowerCase().includes(kw) ||
          (u.phone && u.phone.includes(kw)),
      );
    }
    if (params?.role && params.role !== 'ALL') {
      filtered = filtered.filter((u) => u.role === params.role);
    }
    if (params?.status && params.status !== 'ALL') {
      filtered = filtered.filter((u) => u.status === params.status);
    }
    const start = (page - 1) * pageSize;
    return delay({
      list: filtered.slice(start, start + pageSize),
      total: filtered.length,
      page,
      pageSize,
    });
  },
  updateStatus: (id: number, status: User['status']): Promise<User> => {
    const u = users.find((x) => x.id === id);
    if (u) u.status = status;
    return delay(u!);
  },
  deleteUser: (id: number): Promise<{ success: boolean }> => {
    const idx = users.findIndex((x) => x.id === id);
    if (idx >= 0) users.splice(idx, 1);
    return delay({ success: true });
  },
};

// 课程审核
// M05 任务 23：CourseAudit 页已改用 services/courseAdminApi.ts（真实 API，无 mock 回退），
// 本 mock courseApi 不再被页面使用；保留仅因 generateOrders 依赖上方 courses/courseTitles
// mock 数据。新代码勿再引用本对象。
export const courseApi = {
  getCourses: (status?: CourseStatus | 'ALL'): Promise<Course[]> => {
    if (!status || status === 'ALL') return delay(courses);
    return delay(courses.filter((c) => c.status === status));
  },
  audit: (id: number, approved: boolean, reason?: string): Promise<Course> => {
    const c = courses.find((x) => x.id === id);
    if (c) {
      c.status = approved ? 'APPROVED' : 'REJECTED';
      c.rejectReason = approved ? undefined : reason;
    }
    return delay(c!);
  },
};

// 内容审核
export const contentApi = {
  getList: (status?: ContentItem['status'] | 'ALL'): Promise<ContentItem[]> => {
    if (!status || status === 'ALL') return delay(contentItems);
    return delay(contentItems.filter((c) => c.status === status));
  },
  audit: (id: number, approved: boolean): Promise<ContentItem> => {
    const c = contentItems.find((x) => x.id === id);
    if (c) c.status = approved ? 'APPROVED' : 'REJECTED';
    return delay(c!);
  },
};

function normalizeAdminOrder(order: any): Order {
  const items = order?.items ?? [];
  const firstItem = items.length > 0 ? items[0] : null;
  const payableAmount = Number(order.payableAmount ?? order.amount ?? 0);
  return {
    id: String(order.id),
    orderNo: order.orderNo,
    studentId: order.studentId ? String(order.studentId) : undefined,
    userId: order.studentId ? String(order.studentId) : order.userId,
    userName: order.userName || (order.studentId ? `学员 ${order.studentId}` : '用户'),
    userEmail: order.userEmail || '',
    courseId: firstItem?.courseId ? String(firstItem.courseId) : (order.courseId ? String(order.courseId) : ''),
    courseName: firstItem?.courseTitleSnapshot ?? order.courseName ?? order.courseTitle ?? '课程',
    courseTitle: firstItem?.courseTitleSnapshot ?? order.courseName ?? order.courseTitle ?? '课程',
    amount: payableAmount,
    originalAmount: Number(order.originalAmount ?? 0),
    payableAmount,
    currency: order.currency ?? 'CNY',
    paymentMethod: order.paymentMethod ?? 'ALIPAY',
    status: order.status,
    createdAt: order.createdAt,
    expiresAt: order.expiresAt,
    paidAt: order.paidAt,
    cancelledAt: order.cancelledAt,
    items: items.map((i: any) => ({
      id: String(i.id),
      orderId: String(i.orderId),
      courseId: String(i.courseId),
      courseTitleSnapshot: i.courseTitleSnapshot,
      coverFileIdSnapshot: i.coverFileIdSnapshot,
      coverUrlSnapshot: i.coverUrlSnapshot,
      unitPrice: Number(i.unitPrice ?? 0),
      quantity: Number(i.quantity ?? 1),
      lineAmount: Number(i.lineAmount ?? 0),
      fulfillmentStatus: i.fulfillmentStatus,
    })),
    countdownSeconds: order.countdownSeconds != null ? Number(order.countdownSeconds) : undefined,
  };
}

// 订单
export const orderApi = {
  getOrders: async (params?: {
    orderNo?: string;
    status?: string;
    page?: number;
    size?: number;
    pageSize?: number;
    startDate?: string;
    endDate?: string;
  }): Promise<PaginatedResponse<Order>> => {
    const page = params?.page ?? 1;
    const pageSize = params?.size ?? params?.pageSize ?? 15;
    const queryParams: Record<string, any> = {
      page,
      size: pageSize,
    };
    if (params?.orderNo) queryParams.orderNo = params.orderNo;
    if (params?.status && params.status !== 'ALL') queryParams.status = params.status;

    try {
      const resp = await http.get<ApiEnvelope<any>>('/admin/orders', { params: queryParams });
      const pageData = resp.data.data;
      const items = (pageData?.items ?? []).map(normalizeAdminOrder);
      return {
        list: items,
        total: pageData?.total ?? items.length,
        page: pageData?.page ?? page,
        pageSize: pageData?.size ?? pageSize,
      };
    } catch {
      let filtered = [...orders];
      if (params?.status && params.status !== 'ALL') {
        filtered = filtered.filter((o) => o.status === params.status);
      }
      if (params?.orderNo) {
        filtered = filtered.filter((o) => o.orderNo.includes(params.orderNo!));
      }
      const start = (page - 1) * pageSize;
      return {
        list: filtered.slice(start, start + pageSize),
        total: filtered.length,
        page,
        pageSize,
      };
    }
  },

  getOrderDetail: async (id: string): Promise<Order> => {
    const resp = await http.get<ApiEnvelope<any>>(`/admin/orders/${id}`);
    return normalizeAdminOrder(resp.data.data);
  },
};

// 财务
export const financeApi = {
  getStats: (): Promise<FinanceStats> => delay(financeStats),
  getMonthlyRevenue: (): Promise<MonthlyRevenue[]> => delay(generateMonthlyRevenue()),
  getTransactions: (): Promise<Order[]> => delay(orders.slice(0, 20)),
};

// 仪表盘
export const dashboardApi = {
  getStats: (): Promise<DashboardStats> => delay(dashboardStats),
  getUserGrowth: (): Promise<UserGrowthPoint[]> => delay(generateUserGrowth()),
  getCategoryStats: (): Promise<CategoryStat[]> => delay(generateCategoryStats()),
  getOrderStatusStats: (): Promise<OrderStatusStat[]> => delay(generateOrderStatusStats()),
  getActivities: (): Promise<ActivityItem[]> => delay(generateActivities()),
};

// 系统配置
export const systemApi = {
  getConfig: (): Promise<SystemConfig> => delay({ ...defaultConfig }),
  saveConfig: (config: SystemConfig): Promise<SystemConfig> => {
    Object.assign(defaultConfig, config);
    return delay({ ...defaultConfig });
  },
  getSystemStats: () =>
    delay({
      cpuUsage: 42,
      memoryUsage: 68,
      diskUsage: 54,
      uptime: '12 天 6 小时',
      nodeCount: 4,
      serviceStatus: 'healthy' as const,
    }),
};

// 日志
export const logApi = {
  getLogs: (params?: {
    page?: number;
    pageSize?: number;
    level?: string;
    startDate?: string;
    endDate?: string;
  }): Promise<PaginatedResponse<AuditLog>> => {
    const page = params?.page ?? 1;
    const pageSize = params?.pageSize ?? 15;
    let filtered = [...logs];
    if (params?.level && params.level !== 'ALL') {
      filtered = filtered.filter((l) => l.level === params.level);
    }
    if (params?.startDate) {
      filtered = filtered.filter((l) => l.timestamp >= params.startDate!);
    }
    if (params?.endDate) {
      filtered = filtered.filter((l) => l.timestamp <= params.endDate! + ' 23:59:59');
    }
    const start = (page - 1) * pageSize;
    return delay({
      list: filtered.slice(start, start + pageSize),
      total: filtered.length,
      page,
      pageSize,
    });
  },
};
