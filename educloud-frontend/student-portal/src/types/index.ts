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
  courseTitle?: string;
  title?: string;
  coverFileId?: string | null;
  coverUrl?: string | null;
  cover?: string;
  price: number;
  selected?: boolean;
  valid?: boolean;
  invalidReason?: string | null;
  createdAt?: string;
  teacherName?: string;
}

export interface CartResponse {
  items: CartItem[];
  selectedCount: number;
  totalAmount: number;
  selectedAmount: number;
}

// 作业状态
export type AssignmentStatus = 'PENDING' | 'SUBMITTED' | 'GRADED' | 'OVERDUE';

// 作业提交内容
export interface AssignmentSubmission {
  content: string;
  files?: Array<{ name: string; size: number; url?: string }>;
  submittedAt: string;
  note?: string;
}

// 作业
export interface Assignment {
  id: number | string;
  courseId: number | string;
  courseTitle: string;
  title: string;
  description: string;
  dueDate: string;
  status: AssignmentStatus;
  score?: number;
  totalScore: number;
  submitDate?: string;
  feedback?: string;
  submission?: AssignmentSubmission;
}

// 考试状态
export type ExamStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED' | 'ENDED';

// 考试题目
export interface ExamQuestion {
  id: number;
  question: string;
  options: string[];
  questionType?: 'SINGLE' | 'MULTIPLE' | 'JUDGE';
  answer?: number[];
  /** 后端契约原始题目字段（ExamQuestionResponse.stem），仅供 getExams 映射为 question 时读取 */
  stem?: string;
  /** 兼容本地 mock 回退判分（真实 API 不下发标准答案） */
  correctAnswer?: number;
}

// 考试
export interface Exam {
  id: number | string;
  courseId: number | string;
  courseTitle: string;
  title: string;
  description: string;
  duration: number; // minutes
  /** 后端契约原始时长字段（ExamResponse.durationMinutes），仅供 getExams 映射为 duration 时读取 */
  durationMinutes?: number;
  totalQuestions: number;
  totalScore: number;
  status: ExamStatus;
  startTime?: string;
  endTime?: string;
  score?: number;
  passScore: number;
  submittedAt?: string;
  questions?: ExamQuestion[];
  attemptId?: number | string;
  attemptStatus?: string;
}

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
export type PaymentMethod = 'MOCK' | 'ALIPAY' | 'WECHAT';

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
  userId?: string;
  studentId?: string;
  courseId?: string;
  courseTitle?: string;
  courseCover?: string;
  originalAmount: number;
  payableAmount: number;
  currency: string;
  paymentMethod?: PaymentMethod;
  status: OrderStatus;
  createdAt?: string;
  expiresAt?: string;
  paidAt?: string;
  cancelledAt?: string;
  items?: OrderItem[];
  countdownSeconds?: number;
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
  userType?: string;
  roles?: string[];
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
  pageSize?: number;
  size?: number;
  total: number;
  totalPages?: number;
}

// ---- 角色化动态流（真实 API：GET /api/v1/analytics/student/activities，阶段 4） ----
// 契约见后端 ActivityItem；时间一律使用 timestamp（ISO-8601）计算相对时间，
// 禁止使用后端中文相对时间（timeAgo），否则 dayjs 解析为 Invalid Date。
export interface ActivityItem {
  id: string;
  /** 动态类型（ENROLLED / ASSIGNMENT_SUBMITTED / ASSIGNMENT_GRADED / COURSE_COMPLETED / CERTIFICATE_ISSUED / PROGRESS_MILESTONE / COURSE_REVIEWED ...） */
  actionType: string;
  /** 动作中文文案（后端按模板组合目标标题与扩展字段） */
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  targetTitle?: string | null;
  /** 扩展字段（分数/进度/星级/评语） */
  extra?: Record<string, unknown> | null;
  /** 事件发生时间（ISO-8601） */
  timestamp: string;
}

// ---- 完课证书（真实 API：GET /api/v1/content/certificates，阶段 3） ----
export interface Certificate {
  certNo: string;
  courseId: string | number;
  courseTitle: string;
  issuedAt: string;
}

// ---- M13 推荐模块 ----
export interface RecommendationItem {
  courseId: string;
  title: string;
  categoryId: string;
  categoryName?: string;
  coverUrl: string;
  price: string; // 十进制金额字符串（元）
  reason: string;
  strategy: 'POPULAR' | 'NEW' | 'SIMILAR';
}

export interface RecommendationResponse {
  configVersion: number;
  items: RecommendationItem[];
}
