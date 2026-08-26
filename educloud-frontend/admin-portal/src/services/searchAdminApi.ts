import { http, type ApiEnvelope } from './http';

export type TaskType = 'FULL_REBUILD' | 'INCREMENTAL_REPAIR';
export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface IndexTaskProgressResponse {
  taskNo: string;
  indexName: string;
  aliasName: string;
  taskType: TaskType;
  status: TaskStatus;
  totalRecords: number;
  processedRecords: number;
  failedRecords: number;
  errorMessage?: string;
  progressPercent: number;
  startedAt?: string;
  finishedAt?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const searchAdminApi = {
  /**
   * 触发全量索引平滑重建（异步执行，原子切换别名，零停机）
   * POST /api/v1/search/admin/rebuild-index
   */
  triggerIndexRebuild: async (): Promise<IndexTaskProgressResponse> => {
    const resp = await http.post<ApiEnvelope<IndexTaskProgressResponse>>('/search/admin/rebuild-index');
    return resp.data.data;
  },

  /**
   * 查询指定任务编号的重建进度
   * GET /api/v1/search/admin/tasks/{taskNo}
   */
  fetchTaskProgress: async (taskNo: string): Promise<IndexTaskProgressResponse> => {
    const resp = await http.get<ApiEnvelope<IndexTaskProgressResponse>>(`/search/admin/tasks/${taskNo}`);
    return resp.data.data;
  },

  /**
   * 查询最近触发的索引任务列表
   * GET /api/v1/search/admin/tasks
   */
  fetchRecentTasks: async (limit = 20): Promise<IndexTaskProgressResponse[]> => {
    const resp = await http.get<ApiEnvelope<IndexTaskProgressResponse[]>>('/search/admin/tasks', {
      params: { limit },
    });
    return resp.data.data;
  },
};
