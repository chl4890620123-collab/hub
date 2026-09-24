import type { TaskStatus } from '@/api/types';
import { cn } from '@/lib/cn';

// DONE is reached only through completion approval, and BLOCKED only through a help request (both
// carry information a bare cycle can't - who approved, or what help is needed) - so the free click
// cycle only ever toggles between the two "just working on it" states.
const CYCLE: TaskStatus[] = ['TODO', 'IN_PROGRESS'];

export const LABELS: Record<TaskStatus, string> = {
  TODO: '시작 전',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
  BLOCKED: '도움 필요',
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
      title={disabled ? '담당자 또는 관리자만 변경할 수 있습니다.' : `${LABELS[status]}에서 ${LABELS[nextStatus(status)]}(으)로 변경`}
    >
      {LABELS[status]} → {LABELS[nextStatus(status)]}
    </button>
  );
}
