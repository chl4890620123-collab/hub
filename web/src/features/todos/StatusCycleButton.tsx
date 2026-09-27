import type { TaskStatusUpdate, TodoDisplayStatus, TodoItem } from '@/api/types';
import { cn } from '@/lib/cn';

export const HOLD_STATUS_NOTE = '__HUB_HOLD__';
export const REJECTED_STATUS_NOTE = '__HUB_REJECTED__';

export const LABELS: Record<TodoDisplayStatus, string> = {
  TODO: '시작 전',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
  REJECTED: '반려',
  HOLD: '보류',
  BLOCKED: '도움 필요',
};

const STYLES: Record<TodoDisplayStatus, string> = {
  TODO: 'bg-ink-100 text-ink-600',
  IN_PROGRESS: 'bg-amber-100 text-amber-700',
  DONE: 'bg-accent-100 text-accent-700',
  REJECTED: 'bg-red-100 text-red-700',
  HOLD: 'bg-slate-100 text-slate-700',
  BLOCKED: 'bg-red-100 text-red-700',
};

const NEXT_STATUS: Partial<Record<TodoDisplayStatus, TaskStatusUpdate>> = {
  TODO: 'IN_PROGRESS',
  IN_PROGRESS: 'HOLD',
  HOLD: 'IN_PROGRESS',
  REJECTED: 'IN_PROGRESS',
};

export function getTodoDisplayStatus(todo: Pick<TodoItem, 'taskStatus' | 'statusNote' | 'pendingApproval'>): TodoDisplayStatus {
  if (todo.taskStatus === 'DONE') return 'DONE';
  if (todo.taskStatus === 'BLOCKED') return 'BLOCKED';
  if (todo.statusNote === HOLD_STATUS_NOTE) return 'HOLD';
  if (todo.statusNote === REJECTED_STATUS_NOTE || (!todo.pendingApproval && todo.statusNote)) return 'REJECTED';
  return todo.taskStatus === 'TODO' ? 'TODO' : 'IN_PROGRESS';
}

export function StatusCycleButton({
  status,
  disabled,
  onCycle,
}: {
  status: TodoDisplayStatus;
  disabled?: boolean;
  onCycle: (next: TaskStatusUpdate) => void;
}) {
  const next = NEXT_STATUS[status];
  if (!next) {
    return <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium', STYLES[status])}>{LABELS[status]}</span>;
  }

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onCycle(next)}
      className={cn(
        'rounded-full px-2.5 py-1 text-xs font-medium transition-opacity',
        STYLES[status],
        disabled ? 'cursor-not-allowed opacity-60' : 'hover:opacity-80',
      )}
      title={disabled ? '담당자 또는 관리자만 변경할 수 있습니다.' : `${LABELS[status]}에서 ${LABELS[next]}(으)로 변경`}
    >
      {LABELS[status]} → {LABELS[next]}
    </button>
  );
}
