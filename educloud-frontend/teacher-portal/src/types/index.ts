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

export interface Submission {
  id: string;
  assignmentId: string;
  studentId: string;
  studentName: string;
  studentAvatar: string;
  content: string;
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
}

export interface Activity {
  id: string;
  type: 'enrollment' | 'submission' | 'live' | 'comment' | 'system';
  content: string;
  time: string;
}

export interface AnalyticsStats {
  totalCourses: number;
  totalStudents: number;
  monthlyRevenue: number;
  pendingGrading: number;
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
  label: string;
  value: number;
}
