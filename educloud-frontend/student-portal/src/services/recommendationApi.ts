import { http, type ApiEnvelope } from './http';
import type { RecommendationResponse } from '@/types';

/** M13 推荐模块真实 API（educloud-recommendation，经网关） */
export const recommendationApi = {
  /** GET /api/v1/recommendations（匿名可读；登录自动个性化） */
  getRecommendations: async (
    context: 'home' | 'course',
    courseId?: string,
    limit = 10,
  ): Promise<RecommendationResponse> => {
    const resp = await http.get<ApiEnvelope<RecommendationResponse>>('/recommendations', {
      params: { context, courseId, limit },
    });
    return resp.data.data;
  },

  /** POST /api/v1/recommendations/feedback（必须登录；幂等） */
  dislikeCourse: async (courseId: string): Promise<void> => {
    await http.post('/recommendations/feedback', {
      courseId,
      action: 'DISLIKE',
      reason: '不感兴趣',
    });
  },
};
