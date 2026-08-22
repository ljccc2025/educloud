import { paymentApi } from '@/services/api';
import type { PaymentRequest, PaymentStatusSnapshot } from '@/types';

/**
 * Checkout only depends on this boundary. A real backend implementation can
 * replace the MOCK adapter without changing pages or trusting browser returns.
 */
export interface PaymentGateway {
  initiate(request: PaymentRequest): Promise<PaymentStatusSnapshot>;
  query(orderId: string): Promise<PaymentStatusSnapshot | undefined>;
}

export class MockPaymentGateway implements PaymentGateway {
  async initiate(request: PaymentRequest) {
    await paymentApi.create(request.orderId, request.channel);
    await new Promise((resolve) => globalThis.setTimeout(resolve, 700));

    const result = await paymentApi.getByOrderId(request.orderId);
    if (!result) throw new Error('PAYMENT_STATUS_MISSING');
    return result;
  }

  query(orderId: string) {
    return paymentApi.getByOrderId(orderId);
  }
}

export const paymentGateway: PaymentGateway = new MockPaymentGateway();
