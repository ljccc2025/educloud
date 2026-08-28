import dayjs from 'dayjs';
import type { Assignment, AssignmentSubmission, Exam } from '../types';
import { courseApi } from './courseApi';
import { authApi } from './api';
import { useAuthStore } from '../stores/useAuthStore';

const STORAGE_KEY_PREFIX = 'educloud_student_assignments_';
const EXAM_STORAGE_KEY_PREFIX = 'educloud_student_exams_';

function getUserStorageKey(prefix: string): string {
  try {
    const user = useAuthStore.getState().user;
    if (user?.id) return `${prefix}${user.id}`;
    const token = localStorage.getItem('student_token');
    if (token) {
      const parts = token.split('.');
      if (parts.length === 3) {
        const payload = JSON.parse(atob(parts[1]));
        const uid = payload.userId || payload.sub || payload.id;
        if (uid) return `${prefix}${uid}`;
      }
    }
  } catch {
    // ignore
  }
  return `${prefix}anonymous`;
}

// 默认基础作业清单（纯净初始态，全为待提交）
const defaultAssignmentsSeed: Assignment[] = [
  {
    id: 'asg-001',
    courseId: 'c_1001',
    courseTitle: 'Spring Boot 微服务实践',
    title: '第三章作业：分布式事务与 Seata 框架集成',
    description: '完成微服务订单与库存服务的分布式事务保障，撰写集成步骤并提供关键代码截图与配置文件。',
    dueDate: dayjs().add(5, 'day').format('YYYY-MM-DD 00:00'),
    status: 'PENDING',
    totalScore: 100,
  },
  {
    id: 'asg-002',
    courseId: 'c_1002',
    courseTitle: 'Python 自动化测试实战',
    title: 'Pytest 夹具与自动化测试用例编写',
    description: '使用 Pytest 编写针对 RESTful 接口的完整自动化测试套件，包含参数化与生成 Allure 测试报告。',
    dueDate: dayjs().add(8, 'day').format('YYYY-MM-DD 00:00'),
    status: 'PENDING',
    totalScore: 100,
  },
  {
    id: 'asg-003',
    courseId: 'c_1003',
    courseTitle: '前端工程化与 React 进阶',
    title: '实现自定义 Hook 与状态管理架构',
    description: '编写 useDebounce、useLocalStorage 及轻量级响应式全局状态管理器，提供单元测试。',
    dueDate: dayjs().add(10, 'day').format('YYYY-MM-DD 00:00'),
    status: 'PENDING',
    totalScore: 100,
  },
  {
    id: 'asg-004',
    courseId: 'c_1004',
    courseTitle: 'Kubernetes 云原生架构',
    title: 'Helm Chart 模板化部署与 Ingress 配置',
    description: '将微服务集群打包为标准 Helm Chart，配置 Ingress-Nginx 与 TLS 证书自动签发。',
    dueDate: dayjs().add(15, 'day').format('YYYY-MM-DD 00:00'),
    status: 'PENDING',
    totalScore: 100,
  },
  {
    id: 'asg-005',
    courseId: 'c_1005',
    courseTitle: '深入浅出数据结构与算法',
    title: '红黑树与 B+ 树底层实现与性能评测',
    description: '手写红黑树左旋、右旋与变色插入逻辑，对比在百万级数据下的查询吞吐性能。',
    dueDate: dayjs().add(12, 'day').format('YYYY-MM-DD 00:00'),
    status: 'PENDING',
    totalScore: 100,
  },
];

// 默认基础考试清单（纯净初始态，全为未开始）
const defaultExamsSeed: Exam[] = [
  {
    id: 'exam-001',
    courseId: 'c_1001',
    courseTitle: 'Spring Boot 微服务实践',
    title: '微服务核心架构期末结业考试',
    description: '检验 Spring Cloud 核心组件、分布式链路追踪、服务熔断与限流及分布式事务知识点。',
    duration: 60,
    totalQuestions: 20,
    totalScore: 100,
    passScore: 60,
    status: 'NOT_STARTED',
    startTime: dayjs().subtract(1, 'hour').format('YYYY-MM-DD HH:mm'),
    endTime: dayjs().add(3, 'day').format('YYYY-MM-DD HH:mm'),
    questions: [
      {
        id: 1,
        question: '在 Spring Cloud 中，哪个组件通常用于提供统一的服务注册与动态配置中心？',
        options: ['Nacos', 'Ribbon', 'OpenFeign', 'Sentinel'],
        correctAnswer: 0,
      },
      {
        id: 2,
        question: '分布式事务 Seata 的 AT 模式中，第一阶段的核心动作是什么？',
        options: ['提交全局事务', '执行业务SQL并生成前置/后置镜像保存到 undo_log', '全局回滚', '锁定所有数据库行'],
        correctAnswer: 1,
      },
      {
        id: 3,
        question: 'Spring Cloud Gateway 底层基于哪种响应式网络框架开发？',
        options: ['Tomcat BIO', 'Netty (Reactor)', 'Undertow', 'Jetty'],
        correctAnswer: 1,
      },
      {
        id: 4,
        question: '关于 JWT (JSON Web Token) 的描述，以下哪项是正确的？',
        options: ['JWT 本身默认加密，任何人无法查看 Payload', 'JWT 是无状态的，签名用于校验数据完整性', '必须依赖 Redis 存储每次请求的 Session', 'JWT 无法设置过期时间'],
        correctAnswer: 1,
      },
    ],
  },
  {
    id: 'exam-002',
    courseId: 'c_1003',
    courseTitle: '前端工程化与 React 进阶',
    title: 'React 18 现代前端工程能力认证考核',
    description: '涵盖 Fiber 架构、Concurrent Mode 并发渲染、Hooks 原理与 Vite 构建调优。',
    duration: 45,
    totalQuestions: 15,
    totalScore: 100,
    passScore: 60,
    status: 'NOT_STARTED',
    startTime: dayjs().subtract(1, 'hour').format('YYYY-MM-DD HH:mm'),
    endTime: dayjs().add(5, 'day').format('YYYY-MM-DD HH:mm'),
    questions: [
      {
        id: 1,
        question: 'React 18 引入的 Automatic Batching (自动批处理) 默认生效在哪些场景？',
        options: ['仅在 React 事件处理函数中', '在 Promise、setTimeout 及原生事件中均自动生效', '必须手动包裹 unstable_batchedUpdates', '仅在 class 组件中生效'],
        correctAnswer: 1,
      },
    ],
  },
];

import { http, type ApiEnvelope } from './http';

export const studentAssignmentService = {
  /** 获取当前学员的作业列表（对接后端 API，回退至本地存储） */
  getAssignments: async (): Promise<Assignment[]> => {
    try {
      const resp = await http.get<ApiEnvelope<Assignment[]>>('/me/assignments');
      if (resp.data?.data && Array.isArray(resp.data.data) && resp.data.data.length > 0) {
        return resp.data.data;
      }
    } catch (e) {
      console.warn('Failed to fetch /api/v1/me/assignments, falling back:', e);
    }

    const key = getUserStorageKey(STORAGE_KEY_PREFIX);
    try {
      const stored = localStorage.getItem(key);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch {
      // ignore
    }
    try {
      localStorage.setItem(key, JSON.stringify(defaultAssignmentsSeed));
    } catch {
      // ignore
    }
    return defaultAssignmentsSeed;
  },

  /** 提交作业 */
  submitAssignment: async (
    assignmentId: string | number,
    payload: { content: string; files?: Array<{ name: string; size: number; url?: string }>; note?: string; studentName?: string; studentAvatar?: string }
  ): Promise<Assignment> => {
    let studentName = payload.studentName;
    let studentAvatar = payload.studentAvatar;
    try {
      let authUser = useAuthStore.getState().user;
      if (!authUser) {
        try {
          authUser = await authApi.me();
          useAuthStore.setState({ user: authUser });
          if (authUser) localStorage.setItem('student_user', JSON.stringify(authUser));
        } catch {
          // ignore
        }
      }
      if (!studentName && authUser) {
        studentName = authUser.realName || authUser.username;
      }
      if (!studentAvatar && authUser) {
        studentAvatar = authUser.avatarUrl || authUser.avatar;
      }
    } catch {
      // ignore
    }

    try {
      const resp = await http.post<ApiEnvelope<Assignment>>(`/assignments/${assignmentId}/submit`, {
        ...payload,
        studentName: (studentName && studentName !== '学员') ? studentName : undefined,
        studentAvatar: studentAvatar || undefined,
      });
      if (resp.data?.data) {
        return resp.data.data;
      }
    } catch (e) {
      console.warn('Failed to submit via API, falling back to local storage:', e);
    }

    const key = getUserStorageKey(STORAGE_KEY_PREFIX);
    const list = await studentAssignmentService.getAssignments();
    const now = dayjs().format('YYYY-MM-DD HH:mm:ss');

    const updatedList = list.map((item) => {
      if (String(item.id) === String(assignmentId)) {
        const submission: AssignmentSubmission = {
          content: payload.content,
          files: payload.files || [],
          note: payload.note || '',
          submittedAt: now,
        };
        return {
          ...item,
          status: 'SUBMITTED' as const,
          submitDate: now,
          submission,
        };
      }
      return item;
    });

    localStorage.setItem(key, JSON.stringify(updatedList));
    const target = updatedList.find((i) => String(i.id) === String(assignmentId));
    if (!target) throw new Error('作业未找到');
    return target;
  },

  /** 获取当前学员的考试列表（对接后端 API，回退至本地存储） */
  getExams: async (): Promise<Exam[]> => {
    try {
      const resp = await http.get<ApiEnvelope<Exam[]>>('/me/exams');
      if (resp.data?.data && Array.isArray(resp.data.data) && resp.data.data.length > 0) {
        // 映射后端契约字段为前端标准化字段：stem -> question，durationMinutes -> duration
        return resp.data.data.map((raw) => {
          const exam = raw as Exam & { questions?: Array<{ id: number; stem?: string; options?: string[]; questionType?: string }> };
          return {
            ...exam,
            duration: exam.durationMinutes ?? exam.duration,
            totalQuestions: exam.questions?.length ?? 0,
            questions: (exam.questions ?? []).map((q) => ({
              id: q.id,
              question: q.stem ?? (q as any).question ?? '',
              options: q.options ?? [],
              questionType: q.questionType,
            })),
          };
        });
      }
    } catch (e) {
      console.warn('Failed to fetch /api/v1/me/exams, falling back:', e);
    }
    const key = getUserStorageKey(EXAM_STORAGE_KEY_PREFIX);
    try {
      const stored = localStorage.getItem(key);
      if (stored) return JSON.parse(stored);
    } catch {
      // ignore
    }
    try {
      localStorage.setItem(key, JSON.stringify(defaultExamsSeed));
    } catch {
      // ignore
    }
    return defaultExamsSeed;
  },

  /** 开始考试（真实 API 失败时回退本地 mock） */
  startExam: async (examId: string | number): Promise<{ attemptId: number | string }> => {
    try {
      const resp = await http.post<ApiEnvelope<any>>(`/me/exams/${examId}/attempts`);
      if (resp.data?.data?.id) return { attemptId: resp.data.data.id };
    } catch (e) {
      console.warn('Failed to start exam via API, falling back:', e);
    }
    return { attemptId: `local-${Date.now()}` };
  },

  /** 提交考试答卷并自动判分 */
  submitExam: async (
    examId: string | number,
    attemptId: string | number,
    answers: Record<number, number[]>,
    tabSwitchCount = 0,
  ): Promise<{ exam: Exam; score: number; passed: boolean }> => {
    // 真实 API 路径：attemptId 非 local- 前缀时走 HTTP
    if (!String(attemptId).startsWith('local-')) {
      let resp;
      try {
        resp = await http.post<ApiEnvelope<any>>(
          `/me/exams/${examId}/attempts/${attemptId}/submit`,
          { answers, tabSwitchCount }
        );
      } catch (e) {
        // 真实 attempt 提交失败：不得静默回退本地判分（真实题目无标准答案必然 0 分）
        console.error('Failed to submit exam via API:', e);
        throw new Error('交卷失败，请重试');
      }
      if (resp.data?.data) {
        const data = resp.data.data;
        const list = await studentAssignmentService.getExams();
        const target = list.find((i) => String(i.id) === String(examId));
        // ExamAttemptResponse 不含 totalScore，取自目标考试
        const totalScore = target?.totalScore ?? 100;
        const updated: Exam = target
          ? { ...target, status: 'GRADED' as const, score: data.score, submittedAt: data.submittedAt }
          : ({ id: examId, title: '', courseId: '', courseTitle: '', description: '', duration: 0,
              totalQuestions: 0, totalScore, status: 'GRADED' as const,
              passScore: 60, score: data.score } as Exam);
        return { exam: updated, score: data.score, passed: data.passed };
      }
    }
    // 本地 mock 回退：保留现有判分逻辑（读取 correctAnswer/answer）
    const key = getUserStorageKey(EXAM_STORAGE_KEY_PREFIX);
    const list = await studentAssignmentService.getExams();
    const now = dayjs().format('YYYY-MM-DD HH:mm:ss');
    let computedScore = 0;
    const updatedList = list.map((item) => {
      if (String(item.id) === String(examId)) {
        const questions = item.questions || [];
        const eachScore = item.totalScore / Math.max(1, questions.length);
        let correctCount = 0;
        questions.forEach((q) => {
          const chosen = answers[q.id] ?? [];
          const correct = Array.isArray(q.answer)
            ? chosen.length === q.answer.length && [...chosen].sort().join() === [...q.answer].sort().join()
            : chosen.length === 1 && chosen[0] === (q as any).correctAnswer;
          if (correct) correctCount++;
        });
        computedScore = Math.round(correctCount * eachScore);
        return { ...item, status: 'GRADED' as const, score: computedScore, submittedAt: now };
      }
      return item;
    });
    localStorage.setItem(key, JSON.stringify(updatedList));
    const target = updatedList.find((i) => String(i.id) === String(examId));
    if (!target) throw new Error('考试未找到');
    return { exam: target, score: computedScore, passed: computedScore >= (target.passScore || 60) };
  },
};
