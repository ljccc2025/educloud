import { AlertCircle, Loader2, XCircle } from 'lucide-react';

interface PaymentStatusPanelProps {
  state: 'CONFIRMING' | 'FAILED' | 'CANCELLED';
  onRetry?: () => void;
}

export default function PaymentStatusPanel({
  state,
  onRetry,
}: PaymentStatusPanelProps) {
  const confirming = state === 'CONFIRMING';
  const failed = state === 'FAILED';

  return (
    <div
      role={confirming ? 'status' : 'alert'}
      className="rounded-2xl border border-ink-100 bg-white/80 p-4"
    >
      <div className="flex items-start gap-3">
        {confirming ? (
          <Loader2 className="animate-spin text-indigo-700" />
        ) : failed ? (
          <AlertCircle className="text-red-600" />
        ) : (
          <XCircle className="text-amber-600" />
        )}
        <div className="flex-1">
          <strong className="text-ink-900">
            {confirming
              ? '正在确认支付结果'
              : failed
                ? '支付未完成'
                : '已取消本次支付'}
          </strong>
          <p className="mt-1 text-sm text-ink-500">
            {confirming
              ? '请不要关闭页面，系统正在查询权威订单状态。'
              : '课程尚未开通，你可以保留当前订单并重新支付。'}
          </p>
          {!confirming && onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="mt-3 text-sm font-medium text-indigo-800 underline"
            >
              重新支付
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
