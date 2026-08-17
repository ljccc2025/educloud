// 用户角色
export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

// 用户状态
export type UserStatus = 'ACTIVE' | 'DISABLED';

// 用户
export interface User {
  id: number;
  username: string;
  email: string;
  avatar: string;
  role: UserRole;
  status: UserStatus;
  phone?: string;
  registerDate: string;
  lastLogin?: string;
}

// 课程状态
export type CourseStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

// 课程
export interface Course {
  id: number;
  title: string;
  cover: string;
  teacherName: string;
  category: string;
  price: number;
  description: string;
  submittedDate: string;
  status: CourseStatus;
  rejectReason?: string;
}

// 课件类型
export type ContentType = 'VIDEO' | 'PDF' | 'PPT';

// 内容审核项
export interface ContentItem {
  id: number;
  title: string;
  type: ContentType;
  courseName: string;
  uploader: string;
  uploadDate: string;
  status: CourseStatus;
  fileSize: string;
}

// 订单状态
export type OrderStatus = 'PAID' | 'PENDING' | 'REFUNDED' | 'CANCELLED';

// 支付方式
export type PaymentMethod = 'ALIPAY' | 'WECHAT';

// 订单
export interface Order {
  id: string;
  orderNo: string;
  userId: number;
  userName: string;
  userEmail: string;
  courseId: number;
  courseName: string;
  amount: number;
  paymentMethod: PaymentMethod;
  status: OrderStatus;
  createdAt: string;
}

// 系统配置
export interface SystemConfig {
  siteName: string;
  siteDescription: string;
  logoUrl: string;
  icp: string;
  // 邮件配置
  smtpHost: string;
  smtpPort: number;
  smtpUser: string;
  smtpPassword: string;
  senderName: string;
  senderEmail: string;
  // 存储配置 (MinIO)
  minioEndpoint: string;
  minioPort: number;
  minioAccessKey: string;
  minioSecretKey: string;
  minioBucket: string;
  minioUseSSL: boolean;
  // JWT / 安全
  jwtSecret: string;
  jwtExpiration: number;
  passwordMinLength: number;
  requireEmailVerify: boolean;
  loginAttemptLimit: number;
}

// 操作日志级别
export type LogLevel = 'INFO' | 'WARN' | 'ERROR';

// 操作日志
export interface AuditLog {
  id: number;
  timestamp: string;
  operator: string;
  action: string;
  target: string;
  ip: string;
  level: LogLevel;
  detail?: string;
}

// 财务统计
export interface FinanceStats {
  totalRevenue: number;
  monthlyRevenue: number;
  refundAmount: number;
  pendingSettlement: number;
  transactionCount: number;
  avgOrderValue: number;
}

// 月度收入
export interface MonthlyRevenue {
  month: string;
  revenue: number;
  refund: number;
}

// 仪表盘统计
export interface DashboardStats {
  totalUsers: number;
  totalCourses: number;
  totalRevenue: number;
  onlineUsers: number;
  userGrowth: number;
  courseGrowth: number;
  revenueGrowth: number;
  onlineGrowth: number;
}

// 用户增长数据点
export interface UserGrowthPoint {
  date: string;
  users: number;
  newUsers: number;
}

// 分类统计
export interface CategoryStat {
  name: string;
  count: number;
}

// 订单状态统计
export interface OrderStatusStat {
  name: string;
  value: number;
}

// 活动记录
export interface ActivityItem {
  id: number;
  user: string;
  action: string;
  target: string;
  time: string;
  type: 'user' | 'course' | 'order' | 'system';
}

// 分页响应
export interface PaginatedResponse<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}
