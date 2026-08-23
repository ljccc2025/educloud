import { http, type ApiEnvelope } from './http';
import type { AdminCourse, CourseAuditItem, PageResult } from '../types';

/**
 * 管理端课程审核与生命周期管理真实 API 层（M05 任务 23）。
 *
 * <p>全部经 http.ts 拦截器（Bearer 注入 / 401 刷新重放 / 错误信封归一化）；契约见
 * 2026-08-23-educloud-course-design.md §6：Snowflake id 一律 string（前端禁止 Number()
 * 处理 id），price 为十进制金额字符串。无 mock 回退：调用失败向上抛 Error
 * （code=后端业务码），页面用 apiErrorText 展示。</p>
 *
 * <p>管理全状态课程列表端点 GET /api/v1/admin/courses 为本任务补齐的后端端点
 * （course:audit，任务 22 先例）；offline/republish/archive 复用既有生命周期端点，
 * 服务内 TeacherAccessGuard 对 SYSTEM_ADMIN/SUPER_ADMIN 放行归属校验。</p>
 */

export interface CourseAuditListParams {
  page?: number;
  pageSize?: number;
}

export interface AdminCourseListParams {
  page?: number;
  pageSize?: number;
  lifecycleStatus?: string;
}

export const courseAuditApi = {
  /** GET /api/v1/course-audits（course:audit）→ 待审核分页（PENDING 按提交时间倒序）。 */
  listPending: async (params: CourseAuditListParams = {}): Promise<PageResult<CourseAuditItem>> => {
    const resp = await http.get<ApiEnvelope<PageResult<CourseAuditItem>>>('/course-audits', {
      params,
    });
    return resp.data.data;
  },

  /** GET /api/v1/course-audits/{id}（course:audit）→ 审核快照与历史。 */
  getDetail: async (auditId: string): Promise<CourseAuditItem> => {
    const resp = await http.get<ApiEnvelope<CourseAuditItem>>(`/course-audits/${auditId}`);
    return resp.data.data;
  },

  /** POST /api/v1/course-audits/{id}/approve（course:audit）→ 200（同事务原子发布）。 */
  approve: async (auditId: string): Promise<CourseAuditItem> => {
    const resp = await http.post<ApiEnvelope<CourseAuditItem>>(`/course-audits/${auditId}/approve`);
    return resp.data.data;
  },

  /** POST /api/v1/course-audits/{id}/reject（course:audit）→ 原因必填（400 时后端拒绝）。 */
  reject: async (auditId: string, reason: string): Promise<CourseAuditItem> => {
    const resp = await http.post<ApiEnvelope<CourseAuditItem>>(`/course-audits/${auditId}/reject`, {
      reason,
    });
    return resp.data.data;
  },
};

export const adminCourseApi = {
  /** GET /api/v1/admin/courses（course:audit）→ 全生命周期课程分页（任务 23 补齐端点）。 */
  list: async (params: AdminCourseListParams = {}): Promise<PageResult<AdminCourse>> => {
    const resp = await http.get<ApiEnvelope<PageResult<AdminCourse>>>('/admin/courses', {
      params,
    });
    return resp.data.data;
  },
};

export const courseLifecycleApi = {
  /** POST /api/v1/courses/{id}/offline（course:offline）→ 仅 PUBLISHED 可下架。 */
  offline: async (courseId: string): Promise<void> => {
    await http.post<ApiEnvelope<unknown>>(`/courses/${courseId}/offline`);
  },

  /** POST /api/v1/courses/{id}/republish（course:republish）→ 仅 OFFLINE 可重上架。 */
  republish: async (courseId: string): Promise<void> => {
    await http.post<ApiEnvelope<unknown>>(`/courses/${courseId}/republish`);
  },

  /** POST /api/v1/courses/{id}/archive（course:archive）→ 仅 OFFLINE 可归档（不可逆）。 */
  archive: async (courseId: string): Promise<void> => {
    await http.post<ApiEnvelope<unknown>>(`/courses/${courseId}/archive`);
  },
};
