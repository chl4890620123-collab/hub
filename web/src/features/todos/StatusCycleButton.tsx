import type { TaskStatus } from '@/api/types';
import { cn } from '@/lib/cn';

const CYCLE: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

const LABELS: Record<TaskStatus, string> = {
  TODO: '시작 전',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
  BLOCKED: '보류',
};

const STYLES: Record<TaskStatus, string> = {
  TODO: 'bg-ink-100 text-ink-600',
  IN_PROGRESS: 'bg-amber-100 text-amber-700',
  DONE: 'bg-accent-100 text-accent-700',
  BLOCKED: 'bg-red-100 text-red-700',
};

export function nextStatus(current: TaskStatus): TaskStatus {
  const index = CYCLE.indexOf(current);
  return CYCLE[(index + 1) % CYCLE.length] ?? 'TODO';
}

export function StatusCycleButton({
  status,
  disabled,
  onCycle,
}: {
  status: TaskStatus;
  disabled?: boolean;
  onCycle: (next: TaskStatus) => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onCycle(nextStatus(status))}
      className={cn(
        'rounded-full px-2.5 py-1 text-xs font-medium transition-opacity',
        STYLES[status],
        disabled ? 'cursor-not-allowed opacity-60' : 'hover:opacity-80',
      )}
      title={disabled ? '담당자 또는 관리자만 변경할 수 있습니다.' : '클릭하여 상태 변경'}
    >
      {LABELS[status]}
    </button>
  );
}
