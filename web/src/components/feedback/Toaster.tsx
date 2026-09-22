import { CheckCircle2, Info, XCircle } from 'lucide-react';
import { useToastStore } from '@/stores/toastStore';
import { cn } from '@/lib/cn';

const ICONS = {
  success: <CheckCircle2 size={16} className="text-success-500" />,
  error: <XCircle size={16} className="text-red-500" />,
  info: <Info size={16} className="text-ink-400" />,
};

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-[100] flex w-80 flex-col gap-2">
      {toasts.map((t) => (
        <button
          key={t.id}
          onClick={() => dismiss(t.id)}
          className={cn(
            'flex items-start gap-2 rounded-md border bg-white p-3 text-left text-sm shadow-lg dark:bg-ink-100',
            t.variant === 'error' ? 'border-red-200' : 'border-ink-200',
          )}
        >
          {ICONS[t.variant]}
          <span className="text-ink-700">{t.message}</span>
        </button>
      ))}
    </div>
  );
}
