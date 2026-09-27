import { useState } from 'react';
import { CalendarCheck, FileText, HelpCircle, Paperclip, RotateCcw, Trash2 } from 'lucide-react';
import type { TaskStatusUpdate, TodoItem } from '@/api/types';
import { getTodoDisplayStatus, REJECTED_STATUS_NOTE, StatusCycleButton, LABELS as STATUS_LABELS } from '@/features/todos/StatusCycleButton';
import { TodoAttachmentsPanel } from '@/features/todos/TodoAttachmentsPanel';
import { NotePromptDialog } from '@/features/todos/NotePromptDialog';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/cn';

export function TodoCard({
  todo,
  isAssignee,
  canConfirm,
  canRequestHelp,
  onStatusChange,
  onRequestCompletion,
  onApproveCompletion,
  onRejectCompletion,
  onRequestHelp,
  onResolveHelp,
  onShowEvidence,
  canDelete,
  isDeleted,
  onDelete,
  onRestore,
  canPermanentDelete,
  onPermanentDelete,
  compact,
}: {
  todo: TodoItem;
  /** The confirmed assignee, or an ADMIN acting on their behalf - can start/pause work, ask for help,
   * and request completion. Distinct from canConfirm: being the assignee never grants approval power. */
  isAssignee: boolean;
  /** Decision-maker or ADMIN - the only one who can turn a completion request into DONE. */
  canConfirm: boolean;
  /** Help requests must come from the confirmed assignee themself, never an admin acting for them. */
  canRequestHelp: boolean;
  onStatusChange: (status: TaskStatusUpdate) => void;
  onRequestCompletion: (url: string) => void;
  onApproveCompletion: () => void;
  onRejectCompletion: (reason: string) => void;
  onRequestHelp: (note: string) => void;
  onResolveHelp: () => void;
  onShowEvidence: () => void;
  canDelete: boolean;
  isDeleted?: boolean;
  onDelete: () => void;
  onRestore: () => void;
  canPermanentDelete: boolean;
  onPermanentDelete: () => void;
  compact?: boolean;
}) {
  const [showAttachments, setShowAttachments] = useState(false);
  const [helpDialogOpen, setHelpDialogOpen] = useState(false);
  const [completionDialogOpen, setCompletionDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);

  const needsReassignment = todo.assignmentStatus === 'REASSIGNMENT_REQUIRED';
  const displayStatus = getTodoDisplayStatus(todo);
  const canCycle =
    !isDeleted &&
    !needsReassignment &&
    isAssignee &&
    !todo.pendingApproval &&
    (displayStatus === 'TODO' || displayStatus === 'IN_PROGRESS' || displayStatus === 'HOLD' || displayStatus === 'REJECTED');

  return (
    <div className={cn('rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100', compact && 'text-xs')}>
      <div className="mb-1.5 flex items-start justify-between gap-2">
        <p className={cn('font-medium text-ink-900', compact ? 'truncate text-xs' : 'text-sm')}>{todo.title}</p>
        {isDeleted ? (
          <Badge variant="neutral">휴지통</Badge>
        ) : needsReassignment ? (
          <Badge variant="warning">새 담당자 필요</Badge>
        ) : todo.pendingApproval ? (
          <Badge variant="warning">완료 승인 대기</Badge>
        ) : canCycle ? (
          <StatusCycleButton status={displayStatus} disabled={false} onCycle={onStatusChange} />
        ) : (
          <Badge variant={displayStatus === 'DONE' ? 'accent' : displayStatus === 'BLOCKED' || displayStatus === 'REJECTED' ? 'danger' : 'outline'}>
            {STATUS_LABELS[displayStatus]}
          </Badge>
        )}
      </div>
      {!compact && todo.description && <p className="mb-2 line-clamp-2 text-xs text-ink-500">{todo.description}</p>}
      {!compact && needsReassignment && (
        <p className="mb-2 rounded bg-amber-50 px-2 py-1 text-xs text-amber-700">
          기존 담당자가 프로젝트에서 빠져 새 담당자를 정해야 합니다. 관리자가 재배정하면 다시 진행할 수 있습니다.
        </p>
      )}
      {displayStatus === 'BLOCKED' && todo.statusNote && (
        <p className="mb-2 rounded bg-red-50 px-2 py-1 text-xs text-red-700">도움 요청: {todo.statusNote}</p>
      )}
      {displayStatus === 'REJECTED' && (
        <p className="mb-2 rounded bg-amber-50 px-2 py-1 text-xs text-amber-700">
          {todo.statusNote && todo.statusNote !== REJECTED_STATUS_NOTE ? `반려 사유: ${todo.statusNote}` : '완료 요청이 반려되었습니다.'}
        </p>
      )}
      {displayStatus === 'HOLD' && (
        <p className="mb-2 rounded bg-ink-50 px-2 py-1 text-xs text-ink-600">현재 보류 중인 할 일입니다.</p>
      )}
      {!compact && todo.completionUrl && (
        <p className="mb-2 rounded bg-accent-50 px-2 py-1 text-xs text-accent-700">
          제출 URL:{' '}
          <a href={todo.completionUrl} target="_blank" rel="noreferrer" className="font-medium underline">
            제출물 열기
          </a>
        </p>
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

      {!compact && !isDeleted && !needsReassignment && (isAssignee || canConfirm) && todo.taskStatus !== 'DONE' && (
        <div className="mt-2 flex flex-wrap gap-2 border-t border-ink-100 pt-2">
          {isAssignee && !todo.pendingApproval && displayStatus === 'BLOCKED' && (
            <button onClick={onResolveHelp} className="rounded-full bg-ink-100 px-2.5 py-1 text-xs font-medium text-ink-600 hover:bg-ink-200">
              도움 받음 · 재개
            </button>
          )}
          {isAssignee && !todo.pendingApproval && displayStatus === 'IN_PROGRESS' && (
            <button
              onClick={() => setCompletionDialogOpen(true)}
              className="rounded-full bg-accent-100 px-2.5 py-1 text-xs font-medium text-accent-700 hover:bg-accent-200"
            >
              완료 제출
            </button>
          )}
          {canRequestHelp && !todo.pendingApproval && displayStatus !== 'BLOCKED' && displayStatus !== 'DONE' && (
            <button
              onClick={() => setHelpDialogOpen(true)}
              className="flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-1 text-xs font-medium text-red-700 hover:bg-red-100"
            >
              <HelpCircle size={12} /> 도움 요청
            </button>
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

      {!compact && (
        <div className="mt-2 flex justify-end border-t border-ink-100 pt-2">
          {isDeleted ? (
            <div className="flex flex-wrap gap-2">
              {canDelete && (
                <button onClick={onRestore} className="flex items-center gap-1 rounded-full bg-accent-100 px-2.5 py-1 text-xs font-medium text-accent-700 hover:bg-accent-200">
                  <RotateCcw size={12} /> 복원
                </button>
              )}
              {canPermanentDelete && (
                <button onClick={onPermanentDelete} className="flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-1 text-xs font-medium text-red-800 hover:bg-red-200">
                  <Trash2 size={12} /> 영구 삭제
                </button>
              )}
            </div>
          ) : (
            canDelete && (
              <button onClick={onDelete} className="flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-1 text-xs font-medium text-red-700 hover:bg-red-100">
                <Trash2 size={12} /> 삭제
              </button>
            )
          )}
        </div>
      )}

      {(showAttachments || (canConfirm && todo.pendingApproval)) && (
        <TodoAttachmentsPanel todoId={todo.id} canUpload={isAssignee && !todo.pendingApproval && !isDeleted} />
      )}

      <NotePromptDialog
        open={completionDialogOpen}
        onOpenChange={setCompletionDialogOpen}
        title="완료 제출"
        description="관리자가 확인할 제출 URL을 입력하세요. URL 없이 제출하려면 먼저 첨부파일에서 제출 파일을 추가해야 합니다."
        placeholder="https://... (첨부파일로 제출했다면 비워도 됩니다)"
        confirmLabel="관리자에게 제출"
        onSubmit={(url) => {
          onRequestCompletion(url);
          setCompletionDialogOpen(false);
        }}
      />
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
