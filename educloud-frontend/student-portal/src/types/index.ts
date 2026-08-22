// 课程分类
export type Category =
  | '计算机'
  | '数学'
  | '语言学习'
  | '经济管理'
  | '文学艺术'
  | '设计'
  | '心理学'
  | '法律'
  | '音乐'
  | '哲学';

// 课程难度
export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

// 课件
export interface Courseware {
  id: number;
  title: string;
  type: 'video' | 'quiz' | 'file';
  duration: number;
  completed: boolean;
}

// 章节
export interface Chapter {
  id: number;
  title: string;
  duration: string;
  free: boolean;
  completed: boolean;
  coursewares: Courseware[];
}

// 评价
export interface Review {
  id: number;
  userName: string;
  avatar: string;
  rating: number;
  content: string;
  date: string;
}

// 课程
export interface Course {
  id: number;
  title: string;
  cover: string;
  teacherName: string;
  teacherAvatar: string;
  teacherTitle: string;
  category: Category;
  level: CourseLevel;
  price: number;
  originalPrice?: number;
  description: string;
  whatYouLearn: string[];
  requirements: string[];
  chapters: Chapter[];
  reviews: Review[];
  studentCount: number;
  rating: number;
  reviewCount: number;
  totalDuration: string;
  lastUpdated: string;
  enrolled: boolean;
  progress: number;
}

// 我的课程状态
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

// 购物车条目
export interface CartItem {
  courseId: number;
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

// 订单
export interface Order {
  id: string;
  orderNo: string;
  courseId: number;
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

// 分类展示
export interface CategoryShowcase {
  name: Category;
  icon: string;
  courseCount: number;
  studentCount: number;
  description: string;
}

// 分页
export interface PaginatedResponse<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}
