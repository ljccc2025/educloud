export interface User {
  id: string;
  name: string;
  email: string;
  avatar: string;
  avatarUrl?: string;
  role: 'teacher' | 'admin';
  title: string;
  bio?: string;
}

export type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type CourseCategory = 'backend' | 'frontend' | 'data' | 'ai' | 'devops' | 'mobile';

export interface Course {
  id: string;
  title: string;
  description: string;
  category: CourseCategory;
  price: number;
  cover: string;
  status: CourseStatus;
  studentCount: number;
  chapters: Chapter[];
  createdAt: string;
  updatedAt: string;
}

export type CoursewareType = 'VIDEO' | 'PDF' | 'PPT';

export interface Courseware {
  id: string;
  title: string;
  type: CoursewareType;
  url: string;
  duration?: number; // minutes, for VIDEO
  size?: number; // MB, for PDF/PPT
  createdAt: string;
}

export interface Chapter {
  id: string;
  title: string;
  order: number;
  coursewares: Courseware[];
}

export type LiveStatus = 'CREATED' | 'LIVING' | 'ENDED';

export interface LiveRoom {
  id: string;
  title: string;
  courseId: string;
  courseName: string;
  status: LiveStatus;
  startTime: string;
  endTime?: string;
  viewerCount: number;
  thumbnail: string;
  description?: string;
}

export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

export interface AssignmentDraftInput {
  courseId: string;
  title: string;
  description: string;
  dueDate: string;
  totalScore: number;
  allowLateSubmission: boolean;
  maxAttempts: number;
}

export interface Assignment {
  id: string;
  title: string;
  courseId: string;
  courseName: string;
  description: string;
  dueDate: string;
  totalScore: number;
  status: AssignmentStatus;
  allowLateSubmission: boolean;
  maxAttempts: number;
  publishedAt?: string;
  submissionCount: number;
  gradedCount: number;
  submissions: Submission[];
}

export type SubmissionStatus = 'SUBMITTED' | 'GRADED' | 'LATE';

export interface SubmissionFile {
  name: string;
  size?: number;
  url?: string;
}

export interface Submission {
  id: string;
  assignmentId: string;
  studentId: string;
  studentName: string;
  studentAvatar: string;
  content: string;
  note?: string;
  files?: SubmissionFile[];
  submittedAt: string;
  score?: number;
  feedback?: string;
  status: SubmissionStatus;
}

export interface Student {
  id: string;
  name: string;
  email: string;
  avatar: string;
  enrolledCourses: number;
  progress: number; // percentage 0-100
  lastActive: string;
  joinDate: string;
}

export type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'ONGOING' | 'ENDED';

export interface Exam {
  id: string;
  title: string;
  courseId: string;
  courseName: string;
  questionCount: number;
  duration: number; // minutes
  studentCount: number;
  status: ExamStatus;
  scheduledAt: string;
  /** 以下字段为对接后端 ExamResponse 后的扩展（M05 任务 13）。 */
  description?: string;
  totalScore?: number;
  passScore?: number;
  startTime?: string;
  endTime?: string;
}

/** 教师端题库题目（后端 ExamBankQuestionResponse；answer 为选项索引）。 */
export type ExamQuestionType = 'SINGLE' | 'MULTIPLE' | 'JUDGE';

export interface ExamBankQuestion {
  id: string;
  courseId: string;
  questionType: ExamQuestionType;
  stem: string;
  options: string[];
  /** 正确答案索引列表（示例：SINGLE [0]、MULTIPLE [0, 2]、JUDGE [0]）。 */
  answer: number[];
  analysis?: string;
  defaultScore: number;
  createdAt?: string;
}

export interface Activity {
  id: string;
  type?: 'enrollment' | 'submission' | 'live' | 'comment' | 'system';
  content?: string;
  time: string;
  studentName?: string;
  studentAvatar?: string;
  action?: string;
  courseName?: string;
}

export interface AnalyticsStats {
  totalCourses: number;
  totalStudents: number;
  monthlyRevenue?: number;
  pendingGrading?: number;
  totalRevenue: number;
  completionRate: number;
}

export interface EnrollmentTrend {
  month: string;
  count: number;
}

export interface RevenueData {
  month: string;
  amount: number;
}

export interface EngagementData {
  label?: string;
  value?: number;
  courseId?: string;
  courseName?: string;
  studentCount?: number;
  avgRating?: number;
  completionRate?: number;
}

// ---------- 课程真实 API 类型（M05 任务 22：教师课程管理与封面上传） ----------
// 契约见后端 CourseDraftResponse/TeacherCourseResponse：Snowflake ID 一律 string，
// 前端禁止 Number() 处理 id；price 为十进制金额字符串。
export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

export type CourseVersionStatus = 'DRAFT' | 'PENDING_REVIEW' | 'REJECTED' | 'WITHDRAWN' | 'PUBLISHED';

export type CourseLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | 'ARCHIVED';

export interface Category {
  id: string;
  name: string;
  slug: string;
  sortOrder: number;
  children: Category[];
}

export interface CourseDraftInput {
  title: string;
  subtitle?: string | null;
  description?: string | null;
  coverFileId?: string | null;
  level: CourseLevel;
  /** 十进制金额字符串（如 "199.00"，"0" 表示免费）。 */
  price: string;
  currency: string;
  categoryId: string;
}

export interface CourseDraftTeacher {
  teacherId: string;
  role: string;
}

export interface CourseDraft {
  courseId: string;
  versionId: string;
  versionNo: number;
  title: string;
  subtitle: string | null;
  description: string | null;
  coverFileId: string | null;
  /** 教师视角封面 URL（后端 USER grant）；null 时前端占位。 */
  coverUrl: string | null;
  level: CourseLevel;
  price: string | null;
  currency: string | null;
  categoryId: string | null;
  versionStatus: CourseVersionStatus;
  lifecycleStatus: CourseLifecycleStatus;
  teachers: CourseDraftTeacher[];
}

export interface TeacherCourse {
  courseId: string;
  /** 当前工作版本 id（撤回后草稿指针清空时可能为 null）。 */
  versionId: string | null;
  versionNo: number | null;
  title: string;
  coverUrl: string | null;
  level: string | null;
  price: string | null;
  currency: string | null;
  categoryId: string | null;
  versionStatus: string;
  lifecycleStatus: string;
  enrollmentCount: number;
}

export interface CourseStudent {
  studentId: string;
  displayName: string | null;
  enrolledAt: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}
