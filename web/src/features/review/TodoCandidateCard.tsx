import { useState } from 'react';
import { FileText } from 'lucide-react';
import type { ProjectMember, TodoItem } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';

export function TodoCandidateCard({
  todo,
  members,
  selected,
  onToggleSelect,
  onConfirm,
  onReject,
  onMergeDuplicate,
  onShowEvidence,
  busy,
}: {
  todo: TodoItem;
  members: ProjectMember[];
  selected: boolean;
  onToggleSelect: () => void;
  onConfirm: (assigneeId: number, dueDate: string | null) => void;
  onReject: () => void;
  onMergeDuplicate: () => void;
  onShowEvidence: () => void;
  busy?: boolean;
}) {
  const [assigneeId, setAssigneeId] = useState(todo.assigneeSuggestionId ? String(todo.assigneeSuggestionId) : '');
  const [dueDate, setDueDate] = useState(todo.dueDateSuggestion ?? new Date().toISOString().slice(0, 10));

  return (
    <div className="rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100">
      <div className="mb-2 flex items-start gap-2">
        <input type="checkbox" checked={selected} onChange={onToggleSelect} className="mt-1" />
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            <p className="text-sm font-medium text-ink-900">{todo.title}</p>
            <Badge variant={todo.confidence === 'HIGH' ? 'accent' : 'outline'}>{todo.confidence}</Badge>
            {todo.possibleDuplicateOfId && <Badge variant="warning">중복 의심</Badge>}
          </div>
          {todo.description && <p className="mb-2 text-xs text-ink-500">{todo.description}</p>}
          <p className="text-xs text-ink-400">AI 제안: {todo.assigneeSuggestionText ?? '담당자 없음'} · {todo.dueDateSuggestion ?? '기한 없음'}</p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Select value={assigneeId} onValueChange={setAssigneeId}>
          <SelectTrigger className="w-40">
            <SelectValue placeholder="담당자 선택" />
          </SelectTrigger>
          <SelectContent>
            {members.map((m) => (
              <SelectItem key={m.id} value={String(m.id)}>
                {m.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className="w-40" />
        <Button
          size="sm"
          disabled={!assigneeId || busy}
          onClick={() => onConfirm(Number(assigneeId), dueDate || null)}
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
        <button onClick={onShowEvidence} className="ml-auto flex items-center gap-1 text-xs text-accent-600 hover:underline">
          <FileText size={12} /> 근거
        </button>
      </div>
    </div>
  );
}
