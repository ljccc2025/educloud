import { http, TOKEN_KEY, type ApiEnvelope } from './http';
import type {
  User,
  Course,
  LiveRoom,
  Assignment,
  AssignmentDraftInput,
  Submission,
  Student,
  Exam,
  Activity,
  AnalyticsStats,
  EnrollmentTrend,
  RevenueData,
  EngagementData,
} from '../types';

// ---------- Mock User ----------
const mockUser: User = {
  id: 'u-001',
  name: '张明教授',
  email: 'zhangming@educloud.cn',
  avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangming&backgroundColor=1e1b4b',
  role: 'teacher',
  title: '计算机科学与技术 · 高级讲师',
  bio: '十余年企业级开发与教学经验，专注于 Java 后端、分布式架构与数据分析领域。',
};

// ---------- Mock Students ----------
const studentNames = [
  '李思远', '王梓涵', '陈雨桐', '刘浩然', '赵诗琪',
  '孙明轩', '周雅婷', '吴俊杰', '郑晓彤', '冯泽楷',
  '黄欣怡', '朱晨曦', '徐若曦', '马天宇', '林婉清',
  '胡一鸣', '郭梦洁', '何子轩', '高佳宁', '罗嘉豪',
];

const mockStudents: Student[] = studentNames.map((name, i) => {
  const id = `s-${String(i + 1).padStart(3, '0')}`;
  const enrolled = 1 + (i % 5);
  return {
    id,
    name,
    email: `${name.toLowerCase().replace(/[\u4e00-\u9fa5]/g, '')}student${i + 1}@edu.cn`,
    avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${id}&backgroundColor=c7d2fe,fde68a,ddd6fe`,
    enrolledCourses: enrolled,
    progress: 25 + ((i * 17) % 70),
    lastActive: `2026-08-${String(17 - (i % 10)).padStart(2, '0')}T${String(9 + (i % 10))}:00:00`,
    joinDate: `2026-0${1 + (i % 7)}-${String(1 + (i % 27)).padStart(2, '0')}`,
  };
});

// Fix emails to be valid
mockStudents.forEach((s, i) => {
  s.email = `student${i + 1}@educloud.cn`;
});

// ---------- Mock Courses ----------
const mockCourses: Course[] = [
  {
    id: 'c-001',
    title: 'Spring Boot 3 实战：从入门到微服务架构',
    description:
      '系统讲解 Spring Boot 3 的核心特性，包括自动配置、Starter 机制、数据访问、安全控制与微服务治理。通过真实项目案例，帮助学员掌握企业级后端开发全流程。',
    category: 'backend',
    price: 299,
    cover: 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&h=400&fit=crop',
    status: 'PUBLISHED',
    studentCount: 1284,
    createdAt: '2026-01-15T10:00:00',
    updatedAt: '2026-08-10T14:30:00',
    chapters: [
      {
        id: 'ch-001-1',
        title: '第一章：Spring Boot 3 新特性概览',
        order: 1,
        coursewares: [
          { id: 'cw-1', title: '1.1 课程导论与环境搭建', type: 'VIDEO', url: '#', duration: 28, createdAt: '2026-01-15' },
          { id: 'cw-2', title: '1.2 GraalVM 原生镜像支持', type: 'VIDEO', url: '#', duration: 35, createdAt: '2026-01-15' },
          { id: 'cw-3', title: '课程讲义-第一章.pdf', type: 'PDF', url: '#', size: 2.4, createdAt: '2026-01-16' },
        ],
      },
      {
        id: 'ch-001-2',
        title: '第二章：自动配置原理深度剖析',
        order: 2,
        coursewares: [
          { id: 'cw-4', title: '2.1 自动配置机制源码解读', type: 'VIDEO', url: '#', duration: 42, createdAt: '2026-01-20' },
          { id: 'cw-5', title: '2.2 自定义 Starter 实战', type: 'VIDEO', url: '#', duration: 38, createdAt: '2026-01-20' },
          { id: 'cw-6', title: '源码图解-自动配置.pptx', type: 'PPT', url: '#', size: 5.8, createdAt: '2026-01-21' },
        ],
      },
      {
        id: 'ch-001-3',
        title: '第三章：数据访问与持久化',
        order: 3,
        coursewares: [
          { id: 'cw-7', title: '3.1 Spring Data JPA 进阶', type: 'VIDEO', url: '#', duration: 45, createdAt: '2026-02-01' },
          { id: 'cw-8', title: '3.2 多数据源与事务管理', type: 'VIDEO', url: '#', duration: 40, createdAt: '2026-02-01' },
        ],
      },
    ],
  },
  {
    id: 'c-002',
    title: 'Python 数据分析与可视化实战',
    description:
      '从 Pandas 数据处理到 Matplotlib/Seaborn 可视化，结合真实业务数据集，教授数据清洗、探索性分析与报告呈现的完整方法论。',
    category: 'data',
    price: 199,
    cover: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=600&h=400&fit=crop',
    status: 'PUBLISHED',
    studentCount: 892,
    createdAt: '2026-02-20T09:00:00',
    updatedAt: '2026-08-05T11:20:00',
    chapters: [
      {
        id: 'ch-002-1',
        title: '第一章：数据分析环境与工具链',
        order: 1,
        coursewares: [
          { id: 'cw-9', title: '1.1 Anaconda 与 Jupyter 配置', type: 'VIDEO', url: '#', duration: 22, createdAt: '2026-02-20' },
          { id: 'cw-10', title: '1.2 NumPy 数组运算基础', type: 'VIDEO', url: '#', duration: 30, createdAt: '2026-02-20' },
        ],
      },
      {
        id: 'ch-002-2',
        title: '第二章：Pandas 数据处理核心',
        order: 2,
        coursewares: [
          { id: 'cw-11', title: '2.1 DataFrame 操作与索引', type: 'VIDEO', url: '#', duration: 36, createdAt: '2026-03-01' },
          { id: 'cw-12', title: '2.2 数据清洗与缺失值处理', type: 'VIDEO', url: '#', duration: 33, createdAt: '2026-03-01' },
          { id: 'cw-13', title: '数据集-电商销售样本.csv', type: 'PDF', url: '#', size: 1.2, createdAt: '2026-03-02' },
        ],
      },
    ],
  },
  {
    id: 'c-003',
    title: 'React 18 + TypeScript 现代前端工程化',
    description:
      '深入 React 18 并发渲染、Hooks 进阶、状态管理与 TypeScript 类型体操，配合 Vite 构建高性能企业级前端应用。',
    category: 'frontend',
    price: 259,
    cover: 'https://images.unsplash.com/photo-1633356122544-f134324a6cee?w=600&h=400&fit=crop',
    status: 'PUBLISHED',
    studentCount: 656,
    createdAt: '2026-03-10T08:30:00',
    updatedAt: '2026-07-28T16:45:00',
    chapters: [
      {
        id: 'ch-003-1',
        title: '第一章：React 18 架构演进',
        order: 1,
        coursewares: [
          { id: 'cw-14', title: '1.1 并发渲染与 Fiber 架构', type: 'VIDEO', url: '#', duration: 40, createdAt: '2026-03-10' },
          { id: 'cw-15', title: '1.2 自动批处理与 Transition', type: 'VIDEO', url: '#', duration: 28, createdAt: '2026-03-10' },
        ],
      },
    ],
  },
  {
    id: 'c-004',
    title: 'Docker 与 Kubernetes 容器化部署',
    description:
      '从 Docker 镜像构建到 K8s 集群编排，覆盖容器网络、存储、CI/CD 流水线与生产环境运维最佳实践。',
    category: 'devops',
    price: 349,
    cover: 'https://images.unsplash.com/photo-1667372393119-3d4c48d07fc9?w=600&h=400&fit=crop',
    status: 'DRAFT',
    studentCount: 0,
    createdAt: '2026-07-01T10:00:00',
    updatedAt: '2026-08-12T09:15:00',
    chapters: [
      {
        id: 'ch-004-1',
        title: '第一章：容器技术基础',
        order: 1,
        coursewares: [
          { id: 'cw-16', title: '1.1 容器与虚拟机对比', type: 'VIDEO', url: '#', duration: 25, createdAt: '2026-07-01' },
        ],
      },
    ],
  },
  {
    id: 'c-005',
    title: '机器学习入门：Scikit-Learn 实战',
    description:
      '以 Scikit-Learn 为主线，讲解监督学习、无监督学习经典算法，配合 Kaggle 真实数据集完成端到端建模项目。',
    category: 'ai',
    price: 399,
    cover: 'https://images.unsplash.com/photo-1555949963-aa79dcee981c?w=600&h=400&fit=crop',
    status: 'PUBLISHED',
    studentCount: 445,
    createdAt: '2026-04-05T11:00:00',
    updatedAt: '2026-08-08T13:00:00',
    chapters: [
      {
        id: 'ch-005-1',
        title: '第一章：机器学习概述',
        order: 1,
        coursewares: [
          { id: 'cw-17', title: '1.1 机器学习分类与应用场景', type: 'VIDEO', url: '#', duration: 32, createdAt: '2026-04-05' },
          { id: 'cw-18', title: '1.2 开发环境与 Scikit-Learn 安装', type: 'VIDEO', url: '#', duration: 18, createdAt: '2026-04-05' },
        ],
      },
    ],
  },
  {
    id: 'c-006',
    title: 'Flutter 跨平台移动应用开发',
    description:
      '基于 Flutter 3 与 Dart 语言，从零构建 iOS/Android 双端应用，涵盖状态管理、原生交互与应用上架全流程。',
    category: 'mobile',
    price: 279,
    cover: 'https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=600&h=400&fit=crop',
    status: 'ARCHIVED',
    studentCount: 312,
    createdAt: '2025-09-15T09:00:00',
    updatedAt: '2026-03-20T10:00:00',
    chapters: [],
  },
];

// ---------- Mock Live Rooms ----------
const mockLiveRooms: LiveRoom[] = [
  {
    id: 'lr-001',
    title: 'Spring Boot 3 微服务架构直播答疑（第八期）',
    courseId: 'c-001',
    courseName: 'Spring Boot 3 实战',
    status: 'LIVING',
    startTime: '2026-08-17T19:00:00',
    viewerCount: 326,
    thumbnail: 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=600&h=400&fit=crop',
    description: '本期聚焦微服务网关与服务发现，欢迎学员提前准备问题。',
  },
  {
    id: 'lr-002',
    title: 'Python 数据分析项目实战直播课',
    courseId: 'c-002',
    courseName: 'Python 数据分析与可视化',
    status: 'CREATED',
    startTime: '2026-08-19T20:00:00',
    viewerCount: 0,
    thumbnail: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=600&h=400&fit=crop',
    description: '使用真实电商数据完成一次完整的探索性分析。',
  },
  {
    id: 'lr-003',
    title: 'React 18 性能优化专题直播',
    courseId: 'c-003',
    courseName: 'React 18 + TypeScript',
    status: 'CREATED',
    startTime: '2026-08-22T19:30:00',
    viewerCount: 0,
    thumbnail: 'https://images.unsplash.com/photo-1633356122544-f134324a6cee?w=600&h=400&fit=crop',
    description: '剖析 useMemo/useCallback 与 React DevTools Profiler 的使用。',
  },
  {
    id: 'lr-004',
    title: 'Docker 镜像构建最佳实践（回放）',
    courseId: 'c-004',
    courseName: 'Docker 与 Kubernetes',
    status: 'ENDED',
    startTime: '2026-08-10T20:00:00',
    endTime: '2026-08-10T21:35:00',
    viewerCount: 198,
    thumbnail: 'https://images.unsplash.com/photo-1667372393119-3d4c48d07fc9?w=600&h=400&fit=crop',
  },
  {
    id: 'lr-005',
    title: '机器学习模型评估与调参直播',
    courseId: 'c-005',
    courseName: '机器学习入门',
    status: 'ENDED',
    startTime: '2026-08-05T19:00:00',
    endTime: '2026-08-05T20:40:00',
    viewerCount: 156,
    thumbnail: 'https://images.unsplash.com/photo-1555949963-aa79dcee981c?w=600&h=400&fit=crop',
  },
  {
    id: 'lr-006',
    title: 'Spring Security 安全框架深度讲解',
    courseId: 'c-001',
    courseName: 'Spring Boot 3 实战',
    status: 'CREATED',
    startTime: '2026-08-25T20:00:00',
    viewerCount: 0,
    thumbnail: 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&h=400&fit=crop',
    description: '认证授权流程、JWT 集成与 OAuth2 实战。',
  },
];

// ---------- Mock Assignments ----------
const mockAssignments: Assignment[] = [
  {
    id: 'a-001',
    title: '实验一：实现一个 RESTful 博客 API',
    courseId: 'c-001',
    courseName: 'Spring Boot 3 实战',
    description: '使用 Spring Boot 3 构建博客系统后端 API，包含文章 CRUD、分类管理与评论功能。',
    dueDate: '2026-08-20T23:59:59',
    totalScore: 100,
    status: 'PUBLISHED',
    allowLateSubmission: false,
    maxAttempts: 1,
    publishedAt: '2026-08-10T09:00:00',
    submissionCount: 18,
    gradedCount: 12,
    submissions: [
      {
        id: 'sub-001',
        assignmentId: 'a-001',
        studentId: 's-001',
        studentName: '李思远',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-001',
        content: '已完成博客文章的增删改查接口，使用 Spring Data JPA 进行数据持久化，集成了 Swagger 文档。',
        submittedAt: '2026-08-15T14:22:00',
        score: 92,
        feedback: '接口设计规范，代码结构清晰。建议补充单元测试与异常统一处理。',
        status: 'GRADED',
      },
      {
        id: 'sub-002',
        assignmentId: 'a-001',
        studentId: 's-002',
        studentName: '王梓涵',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-002',
        content: '实现了基本 CRUD，但评论功能尚未完成权限校验。项目已部署到测试环境。',
        submittedAt: '2026-08-15T16:05:00',
        score: 78,
        feedback: '基础功能完整，但安全校验有缺失。请补充 Spring Security 方法级权限控制。',
        status: 'GRADED',
      },
      {
        id: 'sub-003',
        assignmentId: 'a-001',
        studentId: 's-003',
        studentName: '陈雨桐',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-003',
        content: '完整实现了所有要求功能，包括分页查询、全文检索和 Redis 缓存。代码已提交至 GitHub。',
        submittedAt: '2026-08-16T09:30:00',
        status: 'SUBMITTED',
      },
      {
        id: 'sub-004',
        assignmentId: 'a-001',
        studentId: 's-004',
        studentName: '刘浩然',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-004',
        content: '文章与分类模块完成，评论模块正在开发中，预计明日补交。',
        submittedAt: '2026-08-16T11:45:00',
        status: 'SUBMITTED',
      },
      {
        id: 'sub-005',
        assignmentId: 'a-001',
        studentId: 's-005',
        studentName: '赵诗琪',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-005',
        content: '使用了 MyBatis-Plus 替代 JPA，实现了代码生成器与逻辑删除。性能测试通过。',
        submittedAt: '2026-08-16T15:20:00',
        status: 'SUBMITTED',
      },
    ],
  },
  {
    id: 'a-002',
    title: '作业二：Pandas 电商销售数据分析报告',
    courseId: 'c-002',
    courseName: 'Python 数据分析与可视化',
    description: '对给定电商销售数据集进行清洗、探索性分析，输出可视化报告（PDF 或 Notebook）。',
    dueDate: '2026-08-22T23:59:59',
    totalScore: 100,
    status: 'PUBLISHED',
    allowLateSubmission: true,
    maxAttempts: 2,
    publishedAt: '2026-08-11T09:00:00',
    submissionCount: 15,
    gradedCount: 8,
    submissions: [
      {
        id: 'sub-006',
        assignmentId: 'a-002',
        studentId: 's-006',
        studentName: '孙明轩',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-006',
        content: '完成了数据清洗、月度销售趋势分析与商品类目对比，使用 Seaborn 绘制了 12 张图表。',
        submittedAt: '2026-08-14T10:15:00',
        score: 95,
        feedback: '分析维度丰富，图表美观专业。建议增加 RFM 用户分层分析。',
        status: 'GRADED',
      },
      {
        id: 'sub-007',
        assignmentId: 'a-002',
        studentId: 's-007',
        studentName: '周雅婷',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-007',
        content: '基础统计分析已完成，但可视化部分较简单，仅有柱状图和折线图。',
        submittedAt: '2026-08-15T20:00:00',
        status: 'SUBMITTED',
      },
    ],
  },
  {
    id: 'a-003',
    title: '实验三：React 自定义 Hooks 封装',
    courseId: 'c-003',
    courseName: 'React 18 + TypeScript',
    description: '封装 useFetch、useLocalStorage、useDebounce 三个自定义 Hooks，并编写使用示例。',
    dueDate: '2026-08-25T23:59:59',
    totalScore: 100,
    status: 'PUBLISHED',
    allowLateSubmission: false,
    maxAttempts: 1,
    publishedAt: '2026-08-12T09:00:00',
    submissionCount: 9,
    gradedCount: 0,
    submissions: [
      {
        id: 'sub-008',
        assignmentId: 'a-003',
        studentId: 's-008',
        studentName: '吴俊杰',
        studentAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=s-008',
        content: '三个 Hooks 均已实现，useFetch 支持取消请求与错误重试，附带完整 TypeScript 类型定义。',
        submittedAt: '2026-08-16T08:50:00',
        status: 'SUBMITTED',
      },
    ],
  },
];

const additionalSubmissionPlans: Record<string, { count: number; gradedCount: number }> = {
  'a-001': { count: 13, gradedCount: 10 },
  'a-002': { count: 13, gradedCount: 7 },
  'a-003': { count: 8, gradedCount: 0 },
};

const submissionContent: Record<string, string> = {
  'a-001': '已完成博客 API 的核心功能，补充了接口校验、分页查询与异常处理。',
  'a-002': '完成电商销售数据清洗、趋势分析和可视化报告，已整理 Notebook 与结论说明。',
  'a-003': '已实现自定义 Hooks，并补充了 TypeScript 类型定义、错误处理与使用示例。',
};

let nextSubmissionNumber = 9;
for (const assignment of mockAssignments) {
  const plan = additionalSubmissionPlans[assignment.id];
  if (!plan) continue;

  for (let index = 0; index < plan.count; index += 1) {
    const student = mockStudents[(assignment.submissions.length + index) % mockStudents.length];
    const isGraded = index < plan.gradedCount;
    const score = 78 + ((index * 7) % 20);
    const submission: Submission = {
      id: `sub-${String(nextSubmissionNumber).padStart(3, '0')}`,
      assignmentId: assignment.id,
      studentId: student.id,
      studentName: student.name,
      studentAvatar: student.avatar,
      content: submissionContent[assignment.id],
      submittedAt: `2026-08-${String(17 + (index % 3)).padStart(2, '0')}T${String(9 + (index % 10)).padStart(2, '0')}:00:00`,
      status: isGraded ? 'GRADED' : 'SUBMITTED',
      ...(isGraded
        ? {
            score,
            feedback: '作业内容完整，结构清晰。建议继续补充边界场景测试与结果说明。',
          }
        : {}),
    };
    assignment.submissions.push(submission);
    nextSubmissionNumber += 1;
  }

  assignment.submissionCount = assignment.submissions.length;
  assignment.gradedCount = assignment.submissions.filter((submission) => submission.status === 'GRADED').length;
}

// ---------- Mock Exams ----------
const mockExams: Exam[] = [
  {
    id: 'e-001',
    title: 'Spring Boot 3 阶段测验（一至三章）',
    courseId: 'c-001',
    courseName: 'Spring Boot 3 实战',
    questionCount: 30,
    duration: 60,
    studentCount: 856,
    status: 'ENDED',
    scheduledAt: '2026-08-10T14:00:00',
  },
  {
    id: 'e-002',
    title: 'Python 数据分析期中考试',
    courseId: 'c-002',
    courseName: 'Python 数据分析与可视化',
    questionCount: 40,
    duration: 90,
    studentCount: 520,
    status: 'PUBLISHED',
    scheduledAt: '2026-08-25T09:00:00',
  },
  {
    id: 'e-003',
    title: 'React 18 前端工程化期末考试',
    courseId: 'c-003',
    courseName: 'React 18 + TypeScript',
    questionCount: 50,
    duration: 120,
    studentCount: 0,
    status: 'DRAFT',
    scheduledAt: '2026-09-05T14:00:00',
  },
  {
    id: 'e-004',
    title: '机器学习算法基础测验',
    courseId: 'c-005',
    courseName: '机器学习入门',
    questionCount: 25,
    duration: 45,
    studentCount: 310,
    status: 'ONGOING',
    scheduledAt: '2026-08-17T10:00:00',
  },
];

// ---------- Mock Activities ----------
const mockActivities: Activity[] = [
  { id: 'act-1', type: 'submission', content: '李思远 提交了作业「RESTful 博客 API」', time: '2026-08-17T14:22:00' },
  { id: 'act-2', type: 'enrollment', content: '新学员 罗嘉豪 报名了「Spring Boot 3 实战」', time: '2026-08-17T13:05:00' },
  { id: 'act-3', type: 'live', content: '直播「微服务架构答疑」已开始，当前 326 人观看', time: '2026-08-17T19:00:00' },
  { id: 'act-4', type: 'comment', content: '王梓涵 在课程评论区留言提问', time: '2026-08-17T11:40:00' },
  { id: 'act-5', type: 'submission', content: '陈雨桐 提交了作业「RESTful 博客 API」', time: '2026-08-16T09:30:00' },
  { id: 'act-6', type: 'system', content: '课程「Docker 与 K8s」草稿已保存', time: '2026-08-16T08:00:00' },
  { id: 'act-7', type: 'enrollment', content: '新学员 高佳宁 报名了「Python 数据分析」', time: '2026-08-15T17:30:00' },
];

// ---------- Mock Analytics ----------
const mockStats: AnalyticsStats = {
  totalCourses: 6,
  totalStudents: 3589,
  monthlyRevenue: 48620,
  pendingGrading: 22,
  totalRevenue: 328450,
  completionRate: 72.5,
};

const enrollmentTrend: EnrollmentTrend[] = [
  { month: '3月', count: 180 },
  { month: '4月', count: 245 },
  { month: '5月', count: 312 },
  { month: '6月', count: 298 },
  { month: '7月', count: 420 },
  { month: '8月', count: 534 },
];

const revenueData: RevenueData[] = [
  { month: '3月', amount: 18600 },
  { month: '4月', amount: 24800 },
  { month: '5月', amount: 31200 },
  { month: '6月', amount: 28900 },
  { month: '7月', amount: 42300 },
  { month: '8月', amount: 48620 },
];

const engagementData: EngagementData[] = [
  { label: '视频完播', value: 78 },
  { label: '作业提交', value: 65 },
  { label: '讨论参与', value: 42 },
  { label: '直播出勤', value: 58 },
  { label: '测验通过', value: 85 },
];

// ---------- Helper ----------
function delay<T>(data: T, ms = 300): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms));
}

function cloneAssignment(assignment: Assignment): Assignment {
  return {
    ...assignment,
    submissions: assignment.submissions.map((submission) => ({ ...submission })),
  };
}

function requireDraftAssignment(id: string): Assignment {
  const assignment = mockAssignments.find((item) => item.id === id);
  if (!assignment) throw new Error('作业不存在');
  if (assignment.status !== 'DRAFT') throw new Error('只有草稿作业可以修改');
  return assignment;
}

function assertDraftInput(data: AssignmentDraftInput): void {
  if (!data.title.trim()) throw new Error('请输入作业标题');
  if (!Number.isFinite(data.totalScore) || data.totalScore <= 0) {
    throw new Error('满分必须大于 0');
  }
  if (!Number.isInteger(data.maxAttempts) || data.maxAttempts < 1) {
    throw new Error('最大提交次数必须是至少为 1 的整数');
  }
  if (data.dueDate && Number.isNaN(new Date(data.dueDate).getTime())) {
    throw new Error('截止时间格式无效');
  }
}

function courseForAssignment(courseId: string): Course {
  const course = mockCourses.find((item) => item.id === courseId);
  if (!course) throw new Error('所属课程不存在');
  return course;
}

// ---------- API Functions ----------
export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  userType: string;
  roles: string[];
  permissions: string[];
  avatarUrl?: string;
}

function mapAuthUser(a: AuthUser): User {
  return {
    id: a.id,
    name: a.displayName || a.username,
    email: a.username,
    avatar: a.avatarUrl ?? `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(a.username)}&backgroundColor=1e1b4b`,
    avatarUrl: a.avatarUrl,
    role: a.userType === 'ADMIN' ? 'admin' : 'teacher',
    title: a.roles.join('、') || 'EduCloud 教师',
    bio: '',
  };
}

export const api = {
  // Auth（M03 联调：经 Gateway 与 educloud-user 服务对接）
  login: async (loginName: string, password: string) => {
    const resp = await http.post<ApiEnvelope<{ accessToken: string; expiresIn: number; user: AuthUser }>>(
      '/auth/login',
      { loginName, password, portal: 'TEACHER' },
    );
    const token = resp.data.data.accessToken;
    localStorage.setItem(TOKEN_KEY, token);
    const fromLogin = mapAuthUser(resp.data.data.user);
    try {
      // 登录响应不含 avatarUrl（M04：仅 /me 经 File 批量授权解析），补一次 me() 拿真实头像。
      return { user: await api.getCurrentUser(), token };
    } catch {
      return { user: fromLogin, token };
    }
  },
  logout: async (): Promise<void> => {
    try {
      await http.post('/auth/logout');
    } catch {
      // 本地始终清理。
    }
    localStorage.removeItem(TOKEN_KEY);
  },

  // User
  getCurrentUser: async (): Promise<User> => {
    const resp = await http.get<ApiEnvelope<AuthUser>>('/me');
    return mapAuthUser(resp.data.data);
  },

  // Courses
  getCourses: () => delay(mockCourses),
  getCourse: (id: string) => delay(mockCourses.find((c) => c.id === id) ?? null),
  createCourse: (data: Partial<Course>) => {
    const newCourse: Course = {
      id: 'c-' + Date.now(),
      title: data.title ?? '未命名课程',
      description: data.description ?? '',
      category: data.category ?? 'backend',
      price: data.price ?? 0,
      cover: data.cover ?? 'https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=600&h=400&fit=crop',
      status: data.status ?? 'DRAFT',
      studentCount: 0,
      chapters: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    mockCourses.unshift(newCourse);
    return delay(newCourse);
  },
  updateCourse: (id: string, data: Partial<Course>) => {
    const idx = mockCourses.findIndex((c) => c.id === id);
    if (idx !== -1) {
      mockCourses[idx] = { ...mockCourses[idx], ...data, updatedAt: new Date().toISOString() };
      return delay(mockCourses[idx]);
    }
    return delay(null);
  },
  deleteCourse: (id: string) => {
    const idx = mockCourses.findIndex((c) => c.id === id);
    if (idx !== -1) mockCourses.splice(idx, 1);
    return delay({ success: true });
  },

  // Live Rooms
  getLiveRooms: () => delay(mockLiveRooms),
  createLiveRoom: (data: Partial<LiveRoom>) => {
    const newRoom: LiveRoom = {
      id: 'lr-' + Date.now(),
      title: data.title ?? '未命名直播',
      courseId: data.courseId ?? '',
      courseName: data.courseName ?? '',
      status: 'CREATED',
      startTime: data.startTime ?? new Date().toISOString(),
      viewerCount: 0,
      thumbnail: data.thumbnail ?? 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=600&h=400&fit=crop',
      description: data.description,
    };
    mockLiveRooms.unshift(newRoom);
    return delay(newRoom);
  },
  startLive: (id: string) => {
    const room = mockLiveRooms.find((r) => r.id === id);
    if (room) {
      room.status = 'LIVING';
      room.viewerCount = Math.floor(Math.random() * 200) + 50;
    }
    return delay(room);
  },
  endLive: (id: string) => {
    const room = mockLiveRooms.find((r) => r.id === id);
    if (room) {
      room.status = 'ENDED';
      room.endTime = new Date().toISOString();
    }
    return delay(room);
  },

  // Assignments
  getAssignments: () => delay(mockAssignments.map(cloneAssignment)),
  getAssignment: (id: string) => {
    const assignment = mockAssignments.find((item) => item.id === id);
    return delay(assignment ? cloneAssignment(assignment) : null);
  },
  createAssignmentDraft: (data: AssignmentDraftInput) => {
    assertDraftInput(data);
    const course = courseForAssignment(data.courseId);
    const assignment: Assignment = {
      id: `a-${Date.now()}`,
      courseId: course.id,
      courseName: course.title,
      title: data.title.trim(),
      description: data.description.trim(),
      dueDate: data.dueDate ? new Date(data.dueDate).toISOString() : '',
      totalScore: data.totalScore,
      status: 'DRAFT',
      allowLateSubmission: data.allowLateSubmission,
      maxAttempts: data.maxAttempts,
      submissionCount: 0,
      gradedCount: 0,
      submissions: [],
    };
    mockAssignments.unshift(assignment);
    return delay(cloneAssignment(assignment));
  },
  updateAssignmentDraft: (id: string, data: AssignmentDraftInput) => {
    assertDraftInput(data);
    const assignment = requireDraftAssignment(id);
    const course = courseForAssignment(data.courseId);
    Object.assign(assignment, {
      courseId: course.id,
      courseName: course.title,
      title: data.title.trim(),
      description: data.description.trim(),
      dueDate: data.dueDate ? new Date(data.dueDate).toISOString() : '',
      totalScore: data.totalScore,
      allowLateSubmission: data.allowLateSubmission,
      maxAttempts: data.maxAttempts,
    });
    return delay(cloneAssignment(assignment));
  },
  publishAssignment: (id: string) => {
    const assignment = requireDraftAssignment(id);
    const course = courseForAssignment(assignment.courseId);
    const dueAt = new Date(assignment.dueDate);
    if (course.status !== 'PUBLISHED') {
      throw new Error('当前课程尚未发布，作业只能保存为草稿');
    }
    if (!assignment.description.trim()) throw new Error('请输入作业说明');
    if (Number.isNaN(dueAt.getTime()) || dueAt.getTime() <= Date.now()) {
      throw new Error('截止时间必须晚于当前时间');
    }
    if (assignment.totalScore <= 0) throw new Error('满分必须大于 0');
    if (!Number.isInteger(assignment.maxAttempts) || assignment.maxAttempts < 1) {
      throw new Error('最大提交次数必须是至少为 1 的整数');
    }
    assignment.status = 'PUBLISHED';
    assignment.publishedAt = new Date().toISOString();
    return delay(cloneAssignment(assignment));
  },
  gradeSubmission: (submissionId: string, score: number, feedback: string) => {
    for (const a of mockAssignments) {
      const sub = a.submissions.find((s) => s.id === submissionId);
      if (sub) {
        sub.score = score;
        sub.feedback = feedback;
        sub.status = 'GRADED';
        a.gradedCount = a.submissions.filter((s) => s.status === 'GRADED').length;
        return delay({ ...sub });
      }
    }
    return delay(null);
  },

  // Students
  getStudents: () => delay(mockStudents),

  // Exams
  getExams: () => delay(mockExams),
  createExam: (data: Partial<Exam>) => {
    const newExam: Exam = {
      id: 'e-' + Date.now(),
      title: data.title ?? '未命名考试',
      courseId: data.courseId ?? '',
      courseName: data.courseName ?? '',
      questionCount: data.questionCount ?? 0,
      duration: data.duration ?? 60,
      studentCount: 0,
      status: 'DRAFT',
      scheduledAt: data.scheduledAt ?? new Date().toISOString(),
    };
    mockExams.unshift(newExam);
    return delay(newExam);
  },

  // Dashboard
  getActivities: () => delay(mockActivities),
  getStats: () => delay(mockStats),
  getEnrollmentTrend: () => delay(enrollmentTrend),
  getRevenueData: () => delay(revenueData),
  getEngagementData: () => delay(engagementData),
};
