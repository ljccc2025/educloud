import { http, type ApiEnvelope } from './http';
import { uploadCover } from './file';
import type {
  Category,
  CourseDraft,
  CourseDraftInput,
  CourseStudent,
  PaginatedResponse,
  TeacherCourse,
} from '../types';

/**
 * 教师课程管理真实 API 层（M05 任务 22）。
 *
 * <p>全部经 http.ts 拦截器（Bearer 注入 / 401 刷新重放 / 错误信封归一化）；
 * 契约：Snowflake id 一律 string（前端禁止 Number() 处理 id），price 为十进制金额
 * 字符串；GET /teacher/courses 为任务 22 补齐的后端端点（归属教师全部课程含状态）。
 * 无 mock 回退：调用失败向上抛 Error（code=后端业务码），页面用 apiErrorText 展示。</p>
 */

export interface TeacherCourseListParams {
  page?: number;
  size?: number;
}

export const teacherCourseApi = {
  /** GET /api/v1/categories（匿名）→ 分类树（children 递归），CourseEdit 分类选择用。 */
  getCategories: async (): Promise<Category[]> => {
    const resp = await http.get<ApiEnvelope<Category[]>>('/categories');
    return resp.data.data;
  },

  /** GET /api/v1/teacher/courses（course:update）→ 归属教师的全部课程（含状态）分页。 */
  getTeacherCourses: async (
    params: TeacherCourseListParams = {},
  ): Promise<PaginatedResponse<TeacherCourse>> => {
    const resp = await http.get<ApiEnvelope<PaginatedResponse<TeacherCourse>>>(
      '/teacher/courses',
      { params },
    );
    return resp.data.data;
  },

  /** GET /api/v1/teacher/courses/{id}/draft（course:update+归属）；无活动草稿 → 404。 */
  getDraft: async (courseId: string): Promise<CourseDraft> => {
    const resp = await http.get<ApiEnvelope<CourseDraft>>('/teacher/courses/' + courseId + '/draft');
    return resp.data.data;
  },

  /** POST /api/v1/courses（course:create）→ 建根 + 首版 DRAFT，返回同形状。 */
  createCourse: async (input: CourseDraftInput): Promise<CourseDraft> => {
    const resp = await http.post<ApiEnvelope<CourseDraft>>('/courses', input);
    return resp.data.data;
  },

  /** POST /api/v1/courses/{id}/drafts（course:update+归属）→ 从发布/驳回复制新草稿。 */
  createDraft: async (courseId: string): Promise<CourseDraft> => {
    const resp = await http.post<ApiEnvelope<CourseDraft>>('/courses/' + courseId + '/drafts');
    return resp.data.data;
  },

  /** PUT /api/v1/course-drafts/{versionId}（course:update+归属）→ 全量更新 DRAFT。
   * 注意：全量语义——subtitle/description/coverFileId 传 null 表示清空。 */
  updateDraft: async (versionId: string, input: CourseDraftInput): Promise<CourseDraft> => {
    const resp = await http.put<ApiEnvelope<CourseDraft>>('/course-drafts/' + versionId, input);
    return resp.data.data;
  },

  /** POST /api/v1/course-drafts/{versionId}/submit-review（course:submit+归属）→ 200。 */
  submitReview: async (versionId: string): Promise<void> => {
    await http.post<ApiEnvelope<unknown>>('/course-drafts/' + versionId + '/submit-review');
  },

  /** GET /api/v1/courses/{id}/students（course:student:read+归属）→ 分页学员列表。 */
  getStudents: async (
    courseId: string,
    page = 1,
    size = 100,
  ): Promise<PaginatedResponse<CourseStudent>> => {
    const resp = await http.get<ApiEnvelope<PaginatedResponse<CourseStudent>>>(
      '/courses/' + courseId + '/students',
      { params: { page, size } },
    );
    return resp.data.data;
  },

  /** 封面上传：创建会话 → presigned PUT → complete → fileId（复用 file.ts 模式）。 */
  uploadCover,
};
