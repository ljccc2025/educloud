import { http, type ApiEnvelope } from './http';

export interface CourseSearchQueryParams {
  keyword?: string;
  category?: string;
  difficulty?: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | string;
  isFree?: boolean;
  minPriceCents?: number;
  maxPriceCents?: number;
  sortBy?: 'relevance' | 'popular' | 'newest' | 'price_asc' | 'price_desc' | string;
  page?: number;
  size?: number;
}

export interface CourseSearchItem {
  id: string;
  courseId: string;
  title: string;
  subtitle?: string;
  description?: string;
  teacherId?: string;
  teacherName?: string;
  category?: string;
  categoryCode?: string;
  coverUrl?: string;
  difficulty?: string;
  priceCents?: number;
  isFree?: boolean;
  rating?: number;
  studentCount?: number;
  lessonCount?: number;
  status?: string;
  tags?: string[];
  score?: number;
  publishedAt?: string;
  updatedAt?: string;
}

export interface FacetItem {
  key: string;
  count: number;
}

export interface SearchAggregations {
  categories: FacetItem[];
  difficulties: FacetItem[];
  priceRanges: FacetItem[];
}

export interface CourseSearchResponse {
  total: number;
  page: number;
  size: number;
  isDegraded: boolean;
  items: CourseSearchItem[];
  aggregations: SearchAggregations;
}

export interface SuggestItem {
  text: string;
  highlight?: string;
  category?: string;
  type: 'COURSE' | 'KEYWORD';
  targetId?: string;
  score?: number;
}

export interface SuggestResponse {
  suggestions: SuggestItem[];
}

export const searchApi = {
  /**
   * 课程全文检索与多维聚合统计
   * GET /api/v1/search/courses
   */
  searchCourses: async (params: CourseSearchQueryParams = {}): Promise<CourseSearchResponse> => {
    const resp = await http.get<ApiEnvelope<CourseSearchResponse>>('/search/courses', {
      params,
    });
    return resp.data.data;
  },

  /**
   * 搜索框实时智能建议与前缀自动补全
   * GET /api/v1/search/suggest
   */
  fetchSearchSuggestions: async (q: string, limit = 8): Promise<SuggestResponse> => {
    const resp = await http.get<ApiEnvelope<SuggestResponse>>('/search/suggest', {
      params: { q, limit },
    });
    return resp.data.data;
  },
};
