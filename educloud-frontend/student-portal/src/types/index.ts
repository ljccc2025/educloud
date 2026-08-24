// 课程分类（真实 API：GET /api/v1/categories，M05 任务 7）
export interface Category {
  id: string;
  name: string;
  slug: string;
  sortOrder: number;
  children: Category[];
}

// 课程难度
export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

// 课件类型与实体（真实 API：GET /api/v1/courses/{courseId}/chapters）
export type CoursewareType = 'VIDEO' | 'DOCUMENT' | 'EXTERNAL';

export interface Courseware {
  id: string;
  chapterId: string;
  courseId: string;
  title: string;
  coursewareType: CoursewareType;
  fileId: string | null;
  externalUrl: string | null;
  durationSeconds: number;
  sizeBytes: number;
  freePreview: boolean;
  sortOrder: number;
  completed?: boolean;
  positionSeconds?: number;
}

// 课程章节（真实 API：GET /api/v1/courses/{courseId}/chapters）
export interface Chapter {
  id: string;
  courseId: string;
  title: string;
  description: string | null;
  sortOrder: number;
  coursewares: Courseware[];
}

// 课程评价（真实 API：CourseReviewResponse，M05 任务 14；Snowflake id/studentId 为 string）
export interface Review {
  id: string;
  studentId: string;
  rating: number;
  content: string;
  status?: string;
  createdAt: string;
  updatedAt: string;
}

// 课程列表项（真实 API：CourseSummaryResponse，M05 任务 11；id/price 均为 string，禁止 Number() 处理 id）
export interface Course {
  id: string;
  title: string;
  coverUrl: string | null;
  teacherName: string;
  categoryName: string;
  level: CourseLevel;
  price: string;
  ratingAvg: number;
  ratingCount: number;
  enrollmentCount: number;
  enrolled: boolean;
}

// 课程详情教师成员（真实 API：CourseDetailResponse.Teacher）
export interface CourseTeacher {
  teacherId: string;
  teacherRole: string;
}

// 课程详情（真实 API：CourseDetailResponse，M05 任务 11/14）
export interface CourseDetail {
  id: string;
  title: string;
  subtitle: string | null;
  description: string;
  coverUrl: string | null;
  level: CourseLevel;
  price: string;
  currency: string;
  categoryId: string;
  categoryName: string;
  teachers: CourseTeacher[];
  ratingAvg: number;
  ratingCount: number;
  enrollmentCount: number;
  enrolled: boolean;
  lifecycleStatus: string;
  reviews: Review[];
}

// 我的课程（真实 API：GET /api/v1/me/enrollments，M05 任务 13；进度归 M06 Content 服务）
export interface MyCourse {
  courseId: string;
  title: string;
  coverUrl: string | null;
  status: string;
  enrolledAt: string;
}

// 我的课程状态（预留；M05 真实接口仅下发 ACTIVE）
export type CourseStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

// 直播状态
export type LiveStatus = 'LIVE' | 'SCHEDULED' | 'ENDED';

// 直播
export interface LiveRoom {
  id: number;
  courseId: number;
  courseTitle: string;
  teacherName: string;
  teacherAvatar: string;
  title: string;
  cover: string;
  status: LiveStatus;
  startTime: string;
  viewerCount: number;
  duration: string;
}

// 聊天消息
export interface ChatMessage {
  id: number;
  userName: string;
  avatar: string;
  content: string;
  time: string;
  isTeacher?: boolean;
}

// 购物车条目（courseId 为 Snowflake string，禁止 Number()）
export interface CartItem {
  courseId: string;
  title: string;
  price: number;
  cover: string;
  teacherName: string;
}

// 作业状态
export type AssignmentStatus = 'PENDING' | 'SUBMITTED' | 'GRADED' | 'OVERDUE';

// 作业
export interface Assignment {
  id: number;
  courseId: number;
  courseTitle: string;
  title: string;
  description: string;
  dueDate: string;
  status: AssignmentStatus;
  score?: number;
  totalScore: number;
  submitDate?: string;
  feedback?: string;
}

// 考试状态
export type ExamStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED';

// 考试
export interface Exam {
  id: number;
  courseId: number;
  courseTitle: string;
  title: string;
  description: string;
  duration: number; // minutes
  totalQuestions: number;
  totalScore: number;
  status: ExamStatus;
  startTime?: string;
  endTime?: string;
  score?: number;
  passScore: number;
}

// 订单状态：与后端业务订单状态保持一致，支付处理状态不混入订单状态。
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CANCELLED'
  | 'CLOSED'
  | 'REFUNDING'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

// 支付方式
export type PaymentMethod = 'ALIPAY' | 'WECHAT';

// 单次支付尝试状态
export type PaymentAttemptStatus =
  | 'ACTIVE'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export type MockPaymentOutcome = 'SUCCESS' | 'FAILED' | 'CANCELLED';

// 订单（courseId 为 Snowflake string，禁止 Number()）
export interface Order {
  id: string;
  orderNo: string;
  courseId: string;
  courseTitle: string;
  courseCover: string;
  originalAmount: number;
  payableAmount: number;
  currency: 'CNY';
  paymentMethod?: PaymentMethod;
  status: OrderStatus;
  createdAt: string;
  expiresAt: string;
  paidAt?: string;
}

export interface PaymentStatusSnapshot {
  paymentId: string;
  attemptId: string;
  orderId: string;
  channel: PaymentMethod;
  status: PaymentAttemptStatus;
  failureCode?: string;
  providerCreatedAt: string;
  updatedAt: string;
}

export interface PaymentRequest {
  orderId: string;
  channel: PaymentMethod;
}

// 用户
export interface StudentUser {
  id: string;
  username: string;
  realName: string;
  email: string;
  phone: string;
  avatar: string;
  avatarUrl?: string;
  /** 头像 fileId（M04：PATCH /me/profile 全量更新需携带，否则后端会解绑清空头像）。 */
  avatarFileId?: string;
  bio: string;
  joinDate: string;
  learnedCourses: number;
  learnedHours: number;
  certificates: number;
  consecutiveDays: number;
}

// 首页统计
export interface HomeStats {
  totalCourses: number;
  totalStudents: number;
  totalTeachers: number;
  totalHours: number;
}

// 分类展示（首页 mock 仍保留，仅展示用途）
export interface CategoryShowcase {
  name: string;
  icon: string;
  courseCount: number;
  studentCount: number;
  description: string;
}

// 分页响应（真实 API：PageResponse，M05 任务 11/13）
export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}
