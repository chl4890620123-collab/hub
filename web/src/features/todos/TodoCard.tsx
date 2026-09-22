import { useState } from 'react';
import { CalendarCheck, FileText, HelpCircle, Paperclip } from 'lucide-react';
import type { TodoItem } from '@/api/types';
import { StatusCycleButton, LABELS as STATUS_LABELS } from '@/features/todos/StatusCycleButton';
import { TodoAttachmentsPanel } from '@/features/todos/TodoAttachmentsPanel';
import { NotePromptDialog } from '@/features/todos/NotePromptDialog';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/cn';

export function TodoCard({
  todo,
  isAssignee,
  canConfirm,
  onStatusChange,
  onRequestCompletion,
  onApproveCompletion,
  onRejectCompletion,
  onRequestHelp,
  onResolveHelp,
  onShowEvidence,
  compact,
}: {
  todo: TodoItem;
  /** The confirmed assignee, or an ADMIN acting on their behalf - can start/pause work, ask for help,
   * and request completion. Distinct from canConfirm: being the assignee never grants approval power. */
  isAssignee: boolean;
  /** Decision-maker or ADMIN - the only one who can turn a completion request into DONE. */
  canConfirm: boolean;
  onStatusChange: (status: TodoItem['taskStatus']) => void;
  onRequestCompletion: () => void;
  onApproveCompletion: () => void;
  onRejectCompletion: (reason: string) => void;
  onRequestHelp: (note: string) => void;
  onResolveHelp: () => void;
  onShowEvidence: () => void;
  compact?: boolean;
}) {
  const [showAttachments, setShowAttachments] = useState(false);
  const [helpDialogOpen, setHelpDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);

  const canCycle = isAssignee && !todo.pendingApproval && (todo.taskStatus === 'TODO' || todo.taskStatus === 'IN_PROGRESS');

  return (
    <div className={cn('rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100', compact && 'text-xs')}>
      <div className="mb-1.5 flex items-start justify-between gap-2">
        <p className={cn('font-medium text-ink-900', compact ? 'truncate text-xs' : 'text-sm')}>{todo.title}</p>
        {todo.pendingApproval ? (
          <Badge variant="warning">승인 대기 중</Badge>
        ) : canCycle ? (
          <StatusCycleButton status={todo.taskStatus} disabled={false} onCycle={onStatusChange} />
        ) : (
          <Badge variant={todo.taskStatus === 'DONE' ? 'accent' : todo.taskStatus === 'BLOCKED' ? 'danger' : 'outline'}>
            {STATUS_LABELS[todo.taskStatus]}
          </Badge>
        )}
      </div>
      {!compact && todo.description && <p className="mb-2 line-clamp-2 text-xs text-ink-500">{todo.description}</p>}
      {todo.taskStatus === 'BLOCKED' && todo.statusNote && (
        <p className="mb-2 rounded bg-red-50 px-2 py-1 text-xs text-red-700">도움 요청: {todo.statusNote}</p>
      )}
      {!todo.pendingApproval && todo.taskStatus !== 'BLOCKED' && todo.statusNote && (
        <p className="mb-2 rounded bg-amber-50 px-2 py-1 text-xs text-amber-700">반려 사유: {todo.statusNote}</p>
      )}
      <div className="flex flex-wrap items-center gap-2 text-xs text-ink-400">
        <span>{todo.assigneeText ?? '담당자 미정'}</span>
        <span>· {formatDate(todo.dueDate)}</span>
        {todo.googleCalendarEventId && (
          <span className="flex items-center gap-1 text-accent-600" title="담당자의 구글 캘린더에 등록됨">
            <CalendarCheck size={12} /> 캘린더
          </span>
        )}
        <span className="ml-auto flex items-center gap-3">
          <button onClick={() => setShowAttachments((v) => !v)} className="flex items-center gap-1 text-accent-600 hover:underline">
            <Paperclip size={12} /> 첨부파일
          </button>
          <button onClick={onShowEvidence} className="flex items-center gap-1 text-accent-600 hover:underline">
            <FileText size={12} /> 근거
          </button>
        </span>
      </div>

      {!compact && (isAssignee || canConfirm) && todo.taskStatus !== 'DONE' && (
        <div className="mt-2 flex flex-wrap gap-2 border-t border-ink-100 pt-2">
          {isAssignee && !todo.pendingApproval && todo.taskStatus === 'BLOCKED' && (
            <button onClick={onResolveHelp} className="rounded-full bg-ink-100 px-2.5 py-1 text-xs font-medium text-ink-600 hover:bg-ink-200">
              도움 받음 · 재개
            </button>
          )}
          {isAssignee && !todo.pendingApproval && todo.taskStatus !== 'BLOCKED' && (
            <>
              <button onClick={onRequestCompletion} className="rounded-full bg-accent-100 px-2.5 py-1 text-xs font-medium text-accent-700 hover:bg-accent-200">
                완료 요청
              </button>
              <button
                onClick={() => setHelpDialogOpen(true)}
                className="flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-1 text-xs font-medium text-red-700 hover:bg-red-100"
              >
                <HelpCircle size={12} /> 도움 요청
              </button>
            </>
          )}
          {canConfirm && todo.pendingApproval && (
            <>
              <button onClick={onApproveCompletion} className="rounded-full bg-accent-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-accent-600">
                승인
              </button>
              <button
                onClick={() => setRejectDialogOpen(true)}
                className="rounded-full bg-ink-100 px-2.5 py-1 text-xs font-medium text-ink-600 hover:bg-ink-200"
              >
                반려
              </button>
            </>
          )}
        </div>
      )}

      {showAttachments && <TodoAttachmentsPanel todoId={todo.id} />}

      <NotePromptDialog
        open={helpDialogOpen}
        onOpenChange={setHelpDialogOpen}
        title="도움 요청"
        description="어떤 도움이 필요한지 적어 주세요. 같은 프로젝트 팀원들에게 보입니다."
        placeholder="예: OO 시스템 접근 권한이 필요합니다."
        required
        confirmLabel="요청"
        onSubmit={(note) => {
          onRequestHelp(note);
          setHelpDialogOpen(false);
        }}
      />
      <NotePromptDialog
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
        title="완료 반려"
        description="담당자에게 보일 반려 사유를 적어 주세요 (선택)."
        placeholder="예: 검수 항목 하나가 누락되었습니다."
        confirmLabel="반려"
        onSubmit={(reason) => {
          onRejectCompletion(reason);
          setRejectDialogOpen(false);
        }}
      />
    </div>
  );
}
