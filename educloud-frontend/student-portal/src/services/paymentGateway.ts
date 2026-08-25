import { paymentApi, type CashierPayResponse, type PaymentDetailResponse } from './paymentApi';
import type { PaymentMethod, PaymentAttemptStatus } from '@/types';

export interface PaymentRequest {
  orderId: string;
  channel: PaymentMethod;
}

export interface PaymentStatusSnapshot {
  paymentId: string;
  orderId: string;
  channel: PaymentMethod;
  status: PaymentAttemptStatus;
  payUrl?: string;
  qrCode?: string;
}

export class RealPaymentGateway {
  async initiate(request: PaymentRequest): Promise<PaymentStatusSnapshot> {
    const cashier = await paymentApi.createCashier({
      orderId: request.orderId,
      channelCode: request.channel,
      tradeType: 'NATIVE',
    });

    if (request.channel === 'MOCK') {
      // Mock 渠道：自动触发模拟确认
      const confirmed = await paymentApi.mockConfirm(cashier.paymentOrderId);
      return {
        paymentId: confirmed.paymentOrderId,
        orderId: confirmed.orderId,
        channel: confirmed.channelCode as PaymentMethod,
        status: confirmed.status === 'SUCCESS' ? 'SUCCESS' : 'ACTIVE',
        payUrl: confirmed.payUrl,
        qrCode: confirmed.qrCode,
      };
    }

    return {
      paymentId: cashier.paymentOrderId,
      orderId: cashier.orderId,
      channel: cashier.channelCode as PaymentMethod,
      status: cashier.status === 'SUCCESS' ? 'SUCCESS' : 'ACTIVE',
      payUrl: cashier.payUrl,
      qrCode: cashier.qrCode,
    };
  }

  async query(paymentOrderId: string): Promise<PaymentStatusSnapshot | undefined> {
    try {
      const detail = await paymentApi.getPaymentDetail(paymentOrderId);
      return {
        paymentId: detail.paymentOrderId,
        orderId: detail.orderId,
        channel: detail.channelCode as PaymentMethod,
        status: detail.status === 'SUCCESS' ? 'SUCCESS' : 'ACTIVE',
        payUrl: detail.payUrl,
        qrCode: detail.qrCode,
      };
    } catch {
      return undefined;
    }
  }
}

export const paymentGateway = new RealPaymentGateway();
