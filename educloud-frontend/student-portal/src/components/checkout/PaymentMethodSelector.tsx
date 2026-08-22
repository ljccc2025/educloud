import { CheckCircle2, Smartphone } from 'lucide-react';
import type { PaymentMethod } from '@/types';
import { cn } from '@/utils/cn';

const methods: Array<{
  value: PaymentMethod;
  label: string;
  description: string;
}> = [
  { value: 'ALIPAY', label: '支付宝', description: '安全快捷支付' },
  { value: 'WECHAT', label: '微信支付', description: '使用微信完成支付' },
];

interface PaymentMethodSelectorProps {
  value: PaymentMethod;
  onChange: (value: PaymentMethod) => void;
  disabled?: boolean;
}

export default function PaymentMethodSelector({
  value,
  onChange,
  disabled = false,
}: PaymentMethodSelectorProps) {
  return (
    <fieldset disabled={disabled}>
      <legend className="mb-3 text-sm font-semibold text-ink-700">
        选择支付方式
      </legend>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
        {methods.map((method) => {
          const selected = method.value === value;
          return (
            <button
              key={method.value}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={disabled}
              onClick={() => onChange(method.value)}
              className={cn(
                'flex items-center gap-3 rounded-2xl border p-4 text-left transition-colors',
                'disabled:cursor-not-allowed disabled:opacity-60',
                selected
                  ? 'border-indigo-700 bg-indigo-50/70'
                  : 'border-ink-100 bg-white hover:border-ink-200',
              )}
            >
              <Smartphone className="text-indigo-700" aria-hidden="true" />
              <span className="flex-1">
                <strong className="block text-ink-900">{method.label}</strong>
                <span className="text-xs text-ink-400">
                  {method.description}
                </span>
              </span>
              {selected && (
                <CheckCircle2 className="text-indigo-700" aria-hidden="true" />
              )}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
