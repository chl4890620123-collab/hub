import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { FileText, Users } from 'lucide-react';
import type { ProjectMember, TodoItem } from '@/api/types';
import { todosApi } from '@/api/endpoints/todos';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { EvidenceItem } from '@/features/evidence/EvidenceItem';

const INLINE_EVIDENCE_LIMIT = 2;

const CONFIDENCE_LABELS = {
  HIGH: '신뢰 높음',
  MEDIUM: '신뢰 보통',
  LOW: '신뢰 낮음',
} as const;

export function TodoCandidateCard({
  todo,
  members,
  selected,
  onToggleSelect,
  onConfirm,
  onEdit,
  onReject,
  onMergeDuplicate,
  onShowEvidence,
  busy,
}: {
  todo: TodoItem;
  members: ProjectMember[];
  /** Omit both when this card isn't part of a bulk-select list (e.g. shown right after an AI
   * analysis finishes) - the checkbox itself is hidden rather than left non-functional. */
  selected?: boolean;
  onToggleSelect?: () => void;
  onConfirm: (assigneeId: number, dueDate: string | null) => void;
  onEdit?: (title: string, description: string) => Promise<void> | void;
  onReject: () => void;
  onMergeDuplicate: () => void;
  onShowEvidence: () => void;
  busy?: boolean;
}) {
  const [assigneeId, setAssigneeId] = useState(todo.assigneeSuggestionId ? String(todo.assigneeSuggestionId) : '');
  const [dueDate, setDueDate] = useState(todo.dueDateSuggestion ?? '');
  const [title, setTitle] = useState(todo.title);
  const [description, setDescription] = useState(todo.description ?? '');

  const { data: evidence } = useQuery({
    queryKey: ['todo-evidence', todo.id],
    queryFn: () => todosApi.evidence(todo.id),
  });
  const visibleEvidence = evidence?.slice(0, INLINE_EVIDENCE_LIMIT) ?? [];
  const hiddenEvidenceCount = evidence ? evidence.length - visibleEvidence.length : 0;

  return (
    <div className="rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100">
      <div className="mb-2 flex items-start gap-2">
        {onToggleSelect && <input type="checkbox" checked={selected ?? false} onChange={onToggleSelect} className="mt-1" />}
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            {onEdit ? (
              <Input value={title} onChange={(e) => setTitle(e.target.value)} aria-label="할 일 제목" className="min-w-64 flex-1" />
            ) : (
              <p className="text-sm font-medium text-ink-900">{todo.title}</p>
            )}
            <Badge variant={todo.confidence === 'HIGH' ? 'accent' : 'outline'}>{CONFIDENCE_LABELS[todo.confidence]}</Badge>
            {todo.possibleDuplicateOfId && <Badge variant="warning">중복 의심</Badge>}
          </div>
          {onEdit ? (
            <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="할 일 설명" aria-label="할 일 설명" className="mb-2" />
          ) : todo.description ? <p className="mb-2 text-xs text-ink-500">{todo.description}</p> : null}
          <div className="grid gap-2 sm:grid-cols-2">
            <div className="rounded-md bg-ink-50 px-3 py-2 dark:bg-ink-200/60">
              <p className="text-[11px] font-medium text-ink-400">AI 추천 담당자</p>
              <p className="mt-0.5 text-xs font-medium text-ink-700">
                {todo.assigneeSuggestionText ?? '추천 없음'}
                {todo.assigneeSuggestionId ? ' · 프로젝트 팀원 연결됨' : ''}
              </p>
            </div>
            <div className="rounded-md bg-ink-50 px-3 py-2 dark:bg-ink-200/60">
              <p className="text-[11px] font-medium text-ink-400">AI 추천 기한</p>
              <p className="mt-0.5 text-xs font-medium text-ink-700">{todo.dueDateSuggestion ?? '추천 없음'}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="mb-2 flex items-center gap-1 text-xs text-ink-400">
        <Users size={12} /> 배정 가능한 팀원 {members.length}명
      </div>

      <div className="mb-2">
        {evidence && evidence.length === 0 ? (
          <p className="text-xs text-ink-400">증거 자료가 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {visibleEvidence.map((item) => (
              <EvidenceItem key={item.id} item={item} />
            ))}
          </ul>
        )}
        {hiddenEvidenceCount > 0 && (
          <button onClick={onShowEvidence} className="mt-1 flex items-center gap-1 text-xs text-accent-600 hover:underline">
            <FileText size={12} /> 근거 더보기 ({hiddenEvidenceCount})
          </button>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <div>
          <p className="mb-1 text-[11px] font-medium text-ink-400">최종 담당자</p>
          <Select value={assigneeId} onValueChange={setAssigneeId}>
            <SelectTrigger className="w-40">
              <SelectValue placeholder="담당자 선택" />
            </SelectTrigger>
            <SelectContent>
              {members.length === 0 ? (
                <SelectItem value="__no-members" disabled>
                  배정 가능한 팀원이 없습니다
                </SelectItem>
              ) : (
                members.map((m) => (
                  <SelectItem key={m.id} value={String(m.id)}>
                    {m.displayName}{m.jobTitle ? ` · ${m.jobTitle}` : ` · ${m.loginId}`}{m.projectRole === 'ADMIN' ? ' · 관리자' : ''}
                  </SelectItem>
                ))
              )}
            </SelectContent>
          </Select>
        </div>
        <div>
          <p className="mb-1 text-[11px] font-medium text-ink-400">최종 기한 (선택)</p>
          <Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} onClick={(e) => e.currentTarget.showPicker?.()} className="w-44 cursor-pointer [color-scheme:dark]" />
        </div>
        <Button
          size="sm"
          disabled={!assigneeId || busy}
          onClick={async () => {
            if (onEdit && (title.trim() !== todo.title || description.trim() !== (todo.description ?? ''))) {
              await onEdit(title.trim(), description.trim());
            }
            onConfirm(Number(assigneeId), dueDate || null);
          }}
        >
          확정
        </Button>
        <Button size="sm" variant="outline" disabled={busy} onClick={onReject}>
          제외
        </Button>
        {todo.possibleDuplicateOfId && (
          <Button size="sm" variant="outline" disabled={busy} onClick={onMergeDuplicate}>
            중복 병합
          </Button>
        )}
      </div>
    </div>
  );
}
