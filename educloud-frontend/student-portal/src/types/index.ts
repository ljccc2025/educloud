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

// 订单状态
export type OrderStatus = 'PAID' | 'PENDING' | 'REFUNDED' | 'CANCELLED';

// 支付方式
export type PaymentMethod = 'ALIPAY' | 'WECHAT';

// 订单
export interface Order {
  id: string;
  orderNo: string;
  courseId: number;
  courseTitle: string;
  courseCover: string;
  amount: number;
  paymentMethod: PaymentMethod;
  status: OrderStatus;
  createdAt: string;
}

// 用户
export interface StudentUser {
  id: number;
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
