import { FileText } from 'lucide-react';
import type { TodoItem } from '@/api/types';
import { StatusCycleButton } from '@/features/todos/StatusCycleButton';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/cn';

export function TodoCard({
  todo,
  canEdit,
  onStatusChange,
  onShowEvidence,
  compact,
}: {
  todo: TodoItem;
  canEdit: boolean;
  onStatusChange: (status: TodoItem['taskStatus']) => void;
  onShowEvidence: () => void;
  compact?: boolean;
}) {
  return (
    <div className={cn('rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100', compact && 'text-xs')}>
      <div className="mb-1.5 flex items-start justify-between gap-2">
        <p className={cn('font-medium text-ink-900', compact ? 'truncate text-xs' : 'text-sm')}>{todo.title}</p>
        <StatusCycleButton status={todo.taskStatus} disabled={!canEdit} onCycle={onStatusChange} />
      </div>
      {!compact && todo.description && <p className="mb-2 line-clamp-2 text-xs text-ink-500">{todo.description}</p>}
      <div className="flex flex-wrap items-center gap-2 text-xs text-ink-400">
        <span>{todo.assigneeText ?? '담당자 미정'}</span>
        <span>· {formatDate(todo.dueDate)}</span>
        <button onClick={onShowEvidence} className="ml-auto flex items-center gap-1 text-accent-600 hover:underline">
          <FileText size={12} /> 근거
        </button>
      </div>
    </div>
  );
}
