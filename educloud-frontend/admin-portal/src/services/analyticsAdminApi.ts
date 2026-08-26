import { http, type ApiEnvelope } from './http';
import type {
  DashboardStats,
  UserGrowthPoint,
  CategoryStat,
  OrderStatusStat,
  ActivityItem,
  FinanceStats,
  MonthlyRevenue,
  AuditLog,
  PaginatedResponse,
} from '../types';

export interface RebuildTaskProgress {
  id?: number;
  taskNo: string;
  triggerBy?: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  stage: 'INITIALIZING' | 'USER' | 'COURSE' | 'PAYMENT' | 'COMPLETED';
  totalItems: number;
  processedItems: number;
  errorMsg?: string;
  startedAt?: string;
  finishedAt?: string;
}

export const analyticsAdminApi = {
  // 1. 运营看板
  getDashboardStats: async (): Promise<DashboardStats> => {
    const resp = await http.get<ApiEnvelope<DashboardStats>>('/analytics/admin/stats');
    return resp.data.data;
  },

  getUserGrowth: async (): Promise<UserGrowthPoint[]> => {
    const resp = await http.get<ApiEnvelope<UserGrowthPoint[]>>('/analytics/admin/growth');
    return resp.data.data;
  },

  getDistributions: async (): Promise<{ categories: CategoryStat[]; orderStatuses: OrderStatusStat[] }> => {
    const resp = await http.get<ApiEnvelope<{ categories: CategoryStat[]; orderStatuses: OrderStatusStat[] }>>('/analytics/admin/distributions');
    return resp.data.data;
  },

  getRecentActivities: async (): Promise<ActivityItem[]> => {
    const resp = await http.get<ApiEnvelope<any[]>>('/analytics/admin/activities');
    return resp.data.data.map((item) => ({
      id: item.id || `act-${Math.random()}`,
      user: item.studentName || item.user || '管理员',
      action: item.action || '操作',
      target: item.courseName || item.target || '系统资源',
      time: item.timeAgo || item.time || '刚刚',
      type: 'course',
    }));
  },

  // 2. 指标平滑重算引擎
  triggerRebuild: async (): Promise<{ taskNo: string; message: string }> => {
    const resp = await http.post<ApiEnvelope<{ taskNo: string; message: string }>>('/analytics/admin/rebuild');
    return resp.data.data;
  },

  getRebuildProgress: async (taskNo: string): Promise<RebuildTaskProgress> => {
    const resp = await http.get<ApiEnvelope<RebuildTaskProgress>>(`/analytics/admin/rebuild/${taskNo}`);
    return resp.data.data;
  },

  // 3. 财务大屏
  getFinanceOverview: async (): Promise<{ stats: FinanceStats; monthly: MonthlyRevenue[] }> => {
    const resp = await http.get<ApiEnvelope<{ stats: FinanceStats; monthly: MonthlyRevenue[] }>>('/analytics/admin/finance/overview');
    return resp.data.data;
  },

  // 4. 集中式审计日志
  getAuditLogs: async (params?: {
    page?: number;
    pageSize?: number;
    level?: string;
    keyword?: string;
    sourceService?: string;
    actorId?: string;
    startDate?: string;
    endDate?: string;
  }): Promise<PaginatedResponse<AuditLog>> => {
    const resp = await http.get<ApiEnvelope<{ total: number; page: number; pageSize: number; list: any[] }>>(
      '/analytics/admin/audit/logs',
      { params }
    );
    const data = resp.data.data;
    return {
      total: data.total,
      page: data.page,
      pageSize: data.pageSize,
      list: data.list.map((item) => ({
        id: item.id,
        timestamp: item.timestamp,
        operator: item.operator,
        action: item.action,
        target: item.target,
        ip: item.ip,
        status: item.level === 'ERROR' ? 'failed' : 'success',
        detail: item.detail,
        level: item.level,
        sourceService: item.sourceService,
      })),
    };
  },
};
