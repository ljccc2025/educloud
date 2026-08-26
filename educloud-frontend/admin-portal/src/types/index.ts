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
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CANCELLED'
  | 'CLOSED'
  | 'REFUNDING'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED'
  | 'PENDING';

// 支付方式
export type PaymentMethod = 'ALIPAY' | 'WECHAT';

// 订单项
export interface OrderItem {
  id: string;
  orderId: string;
  courseId: string;
  courseTitleSnapshot: string;
  coverFileIdSnapshot: string | null;
  coverUrlSnapshot?: string | null;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  fulfillmentStatus: string;
}

// 订单
export interface Order {
  id: string;
  orderNo: string;
  studentId?: string;
  userId?: string | number;
  userName?: string;
  userEmail?: string;
  courseId?: string | number;
  courseName?: string;
  courseTitle?: string;
  amount: number;
  originalAmount?: number;
  payableAmount: number;
  currency?: string;
  paymentMethod?: PaymentMethod;
  status: OrderStatus;
  createdAt: string;
  expiresAt?: string;
  paidAt?: string;
  cancelledAt?: string;
  items?: OrderItem[];
  countdownSeconds?: number;
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
  onlineUsers?: number;
  activeLives?: number;
  userGrowth?: number;
  userGrowthRate?: number;
  courseGrowth?: number;
  courseGrowthRate?: number;
  revenueGrowth?: number;
  revenueGrowthRate?: number;
  onlineGrowth?: number;
}

// 用户增长数据点
export interface UserGrowthPoint {
  date: string;
  users: number;
  courses?: number;
  newUsers?: number;
}

// 分类统计
export interface CategoryStat {
  name: string;
  count?: number;
  value?: number;
  percentage?: number;
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

// ---------- 课程审核 / 管理真实 API 类型（M05 任务 23） ----------
// 契约见后端 CourseAuditResponse/AdminCourseResponse：Snowflake ID 一律 string，
// 前端禁止 Number() 处理 id；price 为十进制金额字符串（"0" 表示免费）。

export type CourseVersionStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'PUBLISHED'
  | 'SUPERSEDED';

export type CourseLifecycleStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'OFFLINE'
  | 'ARCHIVED';

export type AuditSubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

/** 课程审核快照（GET /course-audits 列表 / {id} 详情 / approve / reject 共用）。 */
export interface CourseAuditItem {
  auditId: string;
  courseId: string;
  versionId: string;
  versionNo: number | null;
  title: string | null;
  subtitle: string | null;
  description: string | null;
  coverFileId: string | null;
  level: string | null;
  price: string | null;
  currency: string | null;
  categoryId: string | null;
  versionStatus: string;
  lifecycleStatus: string;
  submissionStatus: AuditSubmissionStatus;
  submittedBy: string;
  submittedAt: string;
  withdrawnAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reason: string | null;
}

/** 管理端课程管理列表项（GET /admin/courses，全生命周期分页）。 */
export interface AdminCourse {
  courseId: string;
  versionId: string | null;
  versionNo: number | null;
  title: string | null;
  coverUrl: string | null;
  level: string | null;
  price: string | null;
  currency: string | null;
  categoryId: string | null;
  versionStatus: string;
  lifecycleStatus: CourseLifecycleStatus;
  enrollmentCount: number;
}

/** 真实 API 分页响应（items 形状，对齐后端 PageResponse；与上方 mock 形状并存）。 */
export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}
