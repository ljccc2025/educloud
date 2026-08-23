import { http, type ApiEnvelope } from './http';
import type {
  Category,
  Course,
  CourseDetail,
  MyCourse,
  PaginatedResponse,
  Review,
} from '@/types';

/**
 * 课程模块真实 API（M05 任务 21）。
 *
 * <p>全部经 http.ts 拦截器（Bearer 注入 / 401 刷新重放 / 错误信封归一化）；
 * 契约见 2026-08-23-educloud-course-design.md §6：Snowflake id 一律 string，
 * 前端禁止 Number() 处理 id；price 为十进制金额字符串。</p>
 */

export interface CourseListParams {
  keyword?: string;
  categoryId?: string;
  level?: string;
  priceRange?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export const courseApi = {
  /** GET /api/v1/categories（匿名）→ 分类树（children 递归）。 */
  getCategories: async (): Promise<Category[]> => {
    const resp = await http.get<ApiEnvelope<Category[]>>('/categories');
    return resp.data.data;
  },

  /**
   * GET /api/v1/courses → 分页课程列表。
   * sort 白名单 popular/newest/price-asc/price-desc/rating；
   * priceRange 枚举 free/under200/200to400/above400。
   */
  getCourses: async (params: CourseListParams = {}): Promise<PaginatedResponse<Course>> => {
    const resp = await http.get<ApiEnvelope<PaginatedResponse<Course>>>('/courses', {
      params,
    });
    return resp.data.data;
  },

  /** GET /api/v1/courses/{id} → 课程详情（含可见评价列表）。 */
  getById: async (id: string): Promise<CourseDetail> => {
    const resp = await http.get<ApiEnvelope<CourseDetail>>(`/courses/${id}`);
    return resp.data.data;
  },

  /** POST /api/v1/courses/{id}/enrollments（course:enroll，免费选课；付费 → 409 COURSE_NOT_FREE；幂等 200）。 */
  enroll: async (id: string): Promise<void> => {
    await http.post<ApiEnvelope<unknown>>(`/courses/${id}/enrollments`);
  },

  /** GET /api/v1/me/enrollments → 我的课程分页列表。 */
  getMyEnrollments: async (
    page = 1,
    size = 100,
  ): Promise<PaginatedResponse<MyCourse>> => {
    const resp = await http.get<ApiEnvelope<PaginatedResponse<MyCourse>>>(
      '/me/enrollments',
      { params: { page, size } },
    );
    return resp.data.data;
  },

  /** POST /api/v1/courses/{id}/reviews（已选课学生；rating 1-5；未选课 → 403 NOT_ENROLLED）。 */
  submitReview: async (
    courseId: string,
    rating: number,
    content: string,
  ): Promise<Review> => {
    const resp = await http.post<ApiEnvelope<Review>>(
      `/courses/${courseId}/reviews`,
      { rating, content },
    );
    return resp.data.data;
  },
};
