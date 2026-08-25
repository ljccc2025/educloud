import { http, type ApiEnvelope } from './http';

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface RefundDetail {
  refundId: string;
  paymentOrderId: string;
  orderId: string;
  refundRequestId?: string;
  refundAmountCents: number;
  currency: string;
  reason?: string;
  channelCode: 'MOCK' | 'ALIPAY' | 'WECHAT';
  channelRefundNo?: string;
  status: 'APPLIED' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'REJECTED';
  auditedBy?: string;
  auditedAt?: string;
  auditRemark?: string;
  refundedAt?: string;
  createdAt: string;
}

export interface ReconciliationBatch {
  id: string;
  batchNo: string;
  reconcileDate: string;
  channelCode: 'MOCK' | 'ALIPAY' | 'WECHAT';
  totalCount: number;
  totalAmountCents: number;
  diffCount: number;
  status: 'RUNNING' | 'MATCHED' | 'DIFF_FOUND' | 'RESOLVED';
  startedAt: string;
  finishedAt?: string;
}

export interface ReconciliationDiff {
  id: string;
  batchId: string;
  diffType: 'LOCAL_MORE' | 'CHANNEL_MORE' | 'AMOUNT_MISMATCH' | 'STATUS_MISMATCH';
  paymentOrderId?: string;
  channelTradeNo?: string;
  localAmountCents?: number;
  channelAmountCents?: number;
  localStatus?: string;
  channelStatus?: string;
  resolveStatus: 'UNRESOLVED' | 'RESOLVED' | 'IGNORED';
  resolveAction?: string;
  resolveRemark?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt: string;
}

export const paymentAdminApi = {
  listRefunds: async (status?: string, page = 1, size = 20): Promise<PageResponse<RefundDetail>> => {
    const res = await http.get<ApiEnvelope<PageResponse<RefundDetail>>>('/admin/payments/refunds', {
      params: { status, page, size },
    });
    return res.data.data;
  },

  auditRefund: async (refundId: string, approve: boolean, remark?: string): Promise<RefundDetail> => {
    const res = await http.post<ApiEnvelope<RefundDetail>>(`/admin/payments/refunds/${refundId}/audit`, {
      approve,
      remark,
    });
    return res.data.data;
  },

  triggerReconciliation: async (reconcileDate: string, channelCode: string): Promise<ReconciliationBatch> => {
    const res = await http.post<ApiEnvelope<ReconciliationBatch>>('/admin/payments/reconciliation/trigger', {
      reconcileDate,
      channelCode,
    });
    return res.data.data;
  },

  listBatches: async (page = 1, size = 20): Promise<PageResponse<ReconciliationBatch>> => {
    const res = await http.get<ApiEnvelope<PageResponse<ReconciliationBatch>>>('/admin/payments/reconciliation/batches', {
      params: { page, size },
    });
    return res.data.data;
  },

  listDiffs: async (batchId: string, page = 1, size = 20): Promise<PageResponse<ReconciliationDiff>> => {
    const res = await http.get<ApiEnvelope<PageResponse<ReconciliationDiff>>>(`/admin/payments/reconciliation/batches/${batchId}/diffs`, {
      params: { page, size },
    });
    return res.data.data;
  },

  resolveDiff: async (diffId: string, action: string, remark?: string): Promise<ReconciliationDiff> => {
    const res = await http.post<ApiEnvelope<ReconciliationDiff>>(`/admin/payments/reconciliation/diffs/${diffId}/resolve`, {
      action,
      remark,
    });
    return res.data.data;
  },
};
