import { http, type ApiEnvelope } from './http';

export interface CashierPayRequest {
  orderId: string;
  channelCode: 'MOCK' | 'ALIPAY' | 'WECHAT';
  tradeType?: 'NATIVE' | 'PAGE' | 'APP' | 'MOCK';
  subject?: string;
  clientIp?: string;
}

export interface CashierPayResponse {
  paymentOrderId: string;
  orderId: string;
  channelCode: 'MOCK' | 'ALIPAY' | 'WECHAT';
  amountCents: number;
  currency: string;
  payUrl?: string;
  qrCode?: string;
  status: 'INITIATED' | 'PAYING' | 'SUCCESS' | 'FAILED' | 'CLOSED';
  expiresAt: string;
}

export interface PaymentDetailResponse {
  paymentOrderId: string;
  orderId: string;
  userId: string;
  amountCents: number;
  currency: string;
  channelCode: 'MOCK' | 'ALIPAY' | 'WECHAT';
  tradeType: string;
  status: 'INITIATED' | 'PAYING' | 'SUCCESS' | 'FAILED' | 'CLOSED';
  channelTradeNo?: string;
  payUrl?: string;
  qrCode?: string;
  expiresAt?: string;
  paidAt?: string;
}

export const paymentApi = {
  createCashier: async (data: CashierPayRequest): Promise<CashierPayResponse> => {
    const res = await http.post<ApiEnvelope<CashierPayResponse>>('/payments/cashier', data);
    return res.data.data;
  },

  getPaymentDetail: async (paymentOrderId: string): Promise<PaymentDetailResponse> => {
    const res = await http.get<ApiEnvelope<PaymentDetailResponse>>(`/payments/${paymentOrderId}`);
    return res.data.data;
  },

  mockConfirm: async (paymentOrderId: string): Promise<PaymentDetailResponse> => {
    const res = await http.post<ApiEnvelope<PaymentDetailResponse>>(`/payments/${paymentOrderId}/mock-confirm`);
    return res.data.data;
  },

  applyRefund: async (data: { orderId: string; refundAmountCents: number; reason?: string; paymentOrderId?: string }): Promise<any> => {
    const res = await http.post<ApiEnvelope<any>>('/payments/refunds/apply', data);
    return res.data.data;
  },
};
