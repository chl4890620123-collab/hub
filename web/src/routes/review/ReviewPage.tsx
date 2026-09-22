import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { useCurrentProject } from '@/hooks/useProjects';
import { todosApi } from '@/api/endpoints/todos';
import { decisionsApi, changesApi } from '@/api/endpoints/decisionsChanges';
import { projectsApi } from '@/api/endpoints/projects';
import { TodoCandidateCard } from '@/features/review/TodoCandidateCard';
import { BulkSelectionBar } from '@/features/review/BulkSelectionBar';
import { useEvidenceStore } from '@/stores/evidenceStore';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const today = () => new Date().toISOString().slice(0, 10);

function TodoReviewTab({ projectId }: { projectId: number }) {
  const queryClient = useQueryClient();
  const openEvidence = useEvidenceStore((s) => s.open);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkAssignee, setBulkAssignee] = useState('');
  const [bulkDueDate, setBulkDueDate] = useState(today);

  const { data: candidates, isLoading } = useQuery({
    queryKey: ['review-todos', projectId],
    queryFn: () => todosApi.pendingReview(projectId),
  });
  const { data: members } = useQuery({ queryKey: ['project-members', projectId], queryFn: () => projectsApi.members(projectId) });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['review-todos', projectId] });

  const confirm = useMutation({
    mutationFn: ({ id, assigneeId, dueDate }: { id: number; assigneeId: number; dueDate: string | null }) =>
      todosApi.confirm(id, assigneeId, dueDate),
    onSuccess: () => {
      toast.success('할 일을 확정했습니다.');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const reject = useMutation({
    mutationFn: (id: number) => todosApi.reject(id),
    onSuccess: invalidate,
  });
  const mergeDuplicate = useMutation({
    mutationFn: (id: number) => todosApi.mergeDuplicate(id),
    onSuccess: () => {
      toast.success('중복 항목을 병합했습니다.');
      invalidate();
    },
  });
  const bulkConfirm = useMutation({
    mutationFn: () => todosApi.bulkConfirm(projectId, [...selected], Number(bulkAssignee), bulkDueDate || null),
    onSuccess: () => {
      toast.success('선택한 할 일을 일괄 확정했습니다.');
      setSelected(new Set());
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const showEvidence = async (todoId: number, title: string) => {
    const items = await todosApi.evidence(todoId);
    openEvidence(title, items);
  };

  if (isLoading) return <LoadingBlock />;
  if (!candidates || candidates.length === 0) return <EmptyState title="검토 대기 중인 할 일이 없습니다." />;

  return (
    <div>
      <BulkSelectionBar count={selected.size} onClear={() => setSelected(new Set())}>
        <Select value={bulkAssignee} onValueChange={setBulkAssignee}>
          <SelectTrigger className="w-36">
            <SelectValue placeholder="담당자" />
          </SelectTrigger>
          <SelectContent>
            {members?.map((m) => (
              <SelectItem key={m.id} value={String(m.id)}>
                {m.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input type="date" value={bulkDueDate} onChange={(e) => setBulkDueDate(e.target.value)} className="w-36" />
        <Button size="sm" disabled={!bulkAssignee || bulkConfirm.isPending} onClick={() => bulkConfirm.mutate()}>
          일괄 확정
        </Button>
      </BulkSelectionBar>

      <div className="flex flex-col gap-2">
        {candidates.map((todo) => (
          <TodoCandidateCard
            key={todo.id}
            todo={todo}
            members={members ?? []}
            selected={selected.has(todo.id)}
            onToggleSelect={() =>
              setSelected((prev) => {
                const next = new Set(prev);
                next.has(todo.id) ? next.delete(todo.id) : next.add(todo.id);
                return next;
              })
            }
            onConfirm={(assigneeId, dueDate) => confirm.mutate({ id: todo.id, assigneeId, dueDate })}
            onReject={() => reject.mutate(todo.id)}
            onMergeDuplicate={() => mergeDuplicate.mutate(todo.id)}
            onShowEvidence={() => showEvidence(todo.id, todo.title)}
            busy={confirm.isPending || reject.isPending || mergeDuplicate.isPending}
          />
        ))}
      </div>
    </div>
  );
}

function DecisionReviewTab({ projectId }: { projectId: number }) {
  const queryClient = useQueryClient();
  const openEvidence = useEvidenceStore((s) => s.open);
  const { data: decisions, isLoading } = useQuery({
    queryKey: ['review-decisions', projectId],
    queryFn: () => decisionsApi.pending(projectId),
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['review-decisions', projectId] });
  const confirm = useMutation({ mutationFn: (id: number) => decisionsApi.confirm(id), onSuccess: invalidate });
  const reject = useMutation({ mutationFn: (id: number) => decisionsApi.reject(id), onSuccess: invalidate });

  if (isLoading) return <LoadingBlock />;
  if (!decisions || decisions.length === 0) return <EmptyState title="검토 대기 중인 결정 사항이 없습니다." />;

  return (
    <div className="flex flex-col gap-2">
      {decisions.map((d) => (
        <div key={d.id} className="rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100">
          <p className="mb-2 text-sm text-ink-900">{d.statement}</p>
          <div className="flex items-center gap-2">
            <Button size="sm" onClick={() => confirm.mutate(d.id)}>
              확정
            </Button>
            <Button size="sm" variant="outline" onClick={() => reject.mutate(d.id)}>
              제외
            </Button>
            <button
              onClick={async () => openEvidence(d.statement, await decisionsApi.evidence(d.id))}
              className="ml-auto text-xs text-accent-600 hover:underline"
            >
              근거 보기
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

function ChangeReviewTab({ projectId }: { projectId: number }) {
  const queryClient = useQueryClient();
  const { data: changes, isLoading } = useQuery({
    queryKey: ['review-changes', projectId],
    queryFn: () => changesApi.pending(projectId),
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['review-changes', projectId] });
  const confirm = useMutation({ mutationFn: (id: number) => changesApi.confirm(projectId, id), onSuccess: invalidate });
  const reject = useMutation({ mutationFn: (id: number) => changesApi.reject(projectId, id), onSuccess: invalidate });

  if (isLoading) return <LoadingBlock />;
  if (!changes || changes.length === 0) return <EmptyState title="검토 대기 중인 변경 사항이 없습니다." />;

  return (
    <div className="flex flex-col gap-2">
      {changes.map((c) => (
        <div key={c.id} className="rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100">
          <p className="mb-1 text-sm font-medium text-ink-900">{c.category}</p>
          <p className="mb-1 text-xs text-ink-500">before: {c.before_text}</p>
          <p className="mb-2 text-xs text-ink-500">after: {c.after_text}</p>
          <div className="flex items-center gap-2">
            <Button size="sm" onClick={() => confirm.mutate(c.id)}>
              확정
            </Button>
            <Button size="sm" variant="outline" onClick={() => reject.mutate(c.id)}>
              제외
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
}

export function ReviewPage() {
  const { currentProject } = useCurrentProject();
  if (!currentProject) return <NoProjectState />;

  return (
    <div>
      <PageHeader title="담당자 배정" description="AI가 제안한 할 일, 결정, 변경 후보를 검토하고 확정합니다." />
      <Tabs defaultValue="todos">
        <TabsList>
          <TabsTrigger value="todos">할 일</TabsTrigger>
          <TabsTrigger value="decisions">결정</TabsTrigger>
          <TabsTrigger value="changes">변경 이력</TabsTrigger>
        </TabsList>
        <TabsContent value="todos">
          <TodoReviewTab projectId={currentProject.id} />
        </TabsContent>
        <TabsContent value="decisions">
          <DecisionReviewTab projectId={currentProject.id} />
        </TabsContent>
        <TabsContent value="changes">
          <ChangeReviewTab projectId={currentProject.id} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
