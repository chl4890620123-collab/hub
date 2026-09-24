import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useJobPolling } from '@/hooks/useJobPolling';
import { useCanConfirm } from '@/hooks/useProjects';
import { JobStatusPanel } from '@/components/feedback/JobStatusPanel';
import { TodoCandidateCard } from '@/features/review/TodoCandidateCard';
import { todosApi } from '@/api/endpoints/todos';
import { projectsApi } from '@/api/endpoints/projects';
import { useEvidenceStore } from '@/stores/evidenceStore';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

interface AnalyzedResult {
  summary?: string;
  todos?: { id: number | null }[];
  decisions?: { statement: string }[];
}

/** Shown right where a document/meeting/quick-note analysis job was started - once it succeeds, this
 * is the AI's actual output (summary + extracted todos/decisions), with the same confirm/reject/merge
 * controls as the 담당자 배정 review screen, so a user with confirm permission never has to leave the
 * page they were already on to act on what the AI just found. */
export function AnalysisResultPanel({
  jobId,
  projectId,
  context = 'generic',
}: {
  jobId: number | null;
  projectId: number;
  context?: 'generic' | 'meeting';
}) {
  const { data: job } = useJobPolling(jobId);
  const queryClient = useQueryClient();
  const canConfirm = useCanConfirm();
  const openEvidence = useEvidenceStore((s) => s.open);
  const ready = !!job && job.status === 'SUCCESS';

  const { data: members } = useQuery({
    queryKey: ['project-members', projectId],
    queryFn: () => projectsApi.members(projectId),
    enabled: ready && canConfirm,
  });
  const { data: pending } = useQuery({
    queryKey: ['review-todos', projectId],
    queryFn: () => todosApi.pendingReview(projectId),
    enabled: ready,
  });

  const invalidatePending = () => queryClient.invalidateQueries({ queryKey: ['review-todos', projectId] });
  const confirm = useMutation({
    mutationFn: ({ id, assigneeId, dueDate }: { id: number; assigneeId: number; dueDate: string | null }) =>
      todosApi.confirm(id, assigneeId, dueDate),
    onSuccess: () => {
      toast.success('담당자를 저장하고 할 일에 등록했습니다.');
      invalidatePending();
      queryClient.invalidateQueries({ queryKey: ['todos', projectId] });
      queryClient.invalidateQueries({ queryKey: ['todos-undated', projectId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const edit = useMutation({
    mutationFn: ({ id, title, description }: { id: number; title: string; description: string }) =>
      todosApi.editCandidate(id, title, description),
    onSuccess: () => {
      toast.success('할 일 내용을 수정했습니다.');
      invalidatePending();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const reject = useMutation({ mutationFn: (id: number) => todosApi.reject(id), onSuccess: invalidatePending });
  const mergeDuplicate = useMutation({
    mutationFn: (id: number) => todosApi.mergeDuplicate(id),
    onSuccess: () => {
      toast.success('중복 항목을 병합했습니다.');
      invalidatePending();
    },
  });

  if (!jobId) return null;
  if (!job || job.status === 'PENDING' || job.status === 'RUNNING' || job.status === 'FAILED') {
    return (
      <div className="mt-4 flex flex-col gap-3">
        {context === 'meeting' && (
          <div className="rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100">
            <p className="mb-2 text-xs font-semibold text-ink-500">회의 처리 흐름</p>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <Badge variant="accent">1 녹음/업로드 완료</Badge>
              <Badge variant={job?.status === 'FAILED' ? 'danger' : 'warning'}>2 음성 변환 · AI 분석</Badge>
              <Badge variant="outline">3 할 일 후보 확인</Badge>
              <Badge variant="outline">4 담당자 · 기한 확정</Badge>
              <Badge variant="outline">5 Todo 등록</Badge>
            </div>
          </div>
        )}
        <JobStatusPanel jobId={jobId} label={context === 'meeting' ? '회의 음성을 텍스트로 바꾸고 할 일을 정리하고 있습니다' : undefined} />
      </div>
    );
  }

  let result: AnalyzedResult = {};
  try {
    result = job.resultJson ? JSON.parse(job.resultJson) : {};
  } catch {
    // malformed resultJson - fall through and show the summary-less empty state below
  }

  const todoIds = new Set((result.todos ?? []).map((t) => t.id).filter((id): id is number => typeof id === 'number'));
  const matchingTodos = (pending ?? []).filter((t) => todoIds.has(t.id));
  const decisions = result.decisions ?? [];

  return (
    <div className="mt-4 flex flex-col gap-3 rounded-md border border-ink-200 bg-white p-4 dark:bg-ink-100">
      {context === 'meeting' && (
        <div className="border-b border-ink-100 pb-3">
          <p className="mb-2 text-xs font-semibold text-ink-500">회의 처리 흐름</p>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <Badge variant="accent">1 녹음/업로드 완료</Badge>
            <Badge variant="accent">2 음성 변환 · AI 분석 완료</Badge>
            <Badge variant={matchingTodos.length > 0 ? 'warning' : 'accent'}>3 할 일 후보 확인</Badge>
            <Badge variant={matchingTodos.length > 0 ? 'outline' : 'accent'}>4 담당자 · 기한 확정</Badge>
            <Badge variant="outline">5 Todo 등록</Badge>
          </div>
          <p className="mt-2 text-xs text-ink-400">
            AI가 후보를 만들고, 아래에서 담당자와 기한을 확인한 뒤 확정하면 실제 할 일 목록에 등록됩니다.
          </p>
        </div>
      )}
      <div>
        <p className="mb-1 text-xs font-semibold text-ink-500">AI 회의 요약</p>
        <p className="text-sm text-ink-700">{result.summary || '정리할 내용이 없습니다.'}</p>
      </div>

      {matchingTodos.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="text-xs font-semibold text-ink-500">확인할 할 일 {matchingTodos.length}건</p>
          {canConfirm ? (
            matchingTodos.map((todo) => (
              <TodoCandidateCard
                key={todo.id}
                todo={todo}
                members={members ?? []}
                onEdit={async (title, description) => { await edit.mutateAsync({ id: todo.id, title, description }); }}
                onConfirm={(assigneeId, dueDate) => confirm.mutate({ id: todo.id, assigneeId, dueDate })}
                onReject={() => reject.mutate(todo.id)}
                onMergeDuplicate={() => mergeDuplicate.mutate(todo.id)}
                onShowEvidence={async () => openEvidence(todo.title, await todosApi.evidence(todo.id))}
                busy={edit.isPending || confirm.isPending || reject.isPending || mergeDuplicate.isPending}
              />
            ))
          ) : (
            <ul className="flex flex-col gap-1">
              {matchingTodos.map((todo) => (
                <li key={todo.id} className="text-sm text-ink-700">
                  {todo.title}
                  {todo.dueDateSuggestion ? ` · ${todo.dueDateSuggestion}` : ''}
                </li>
              ))}
            </ul>
          )}
          {!canConfirm && (
            <p className="text-xs text-ink-400">AI가 정리한 내용은 바로 업무로 확정되지 않습니다. 담당자 배정 권한이 있는 사람이 원문을 확인한 뒤 확정합니다.</p>
          )}
        </div>
      )}

      {decisions.length > 0 && (
        <div className="flex flex-col gap-1">
          <p className="text-xs font-semibold text-ink-500">확인할 결정 {decisions.length}건</p>
          {decisions.map((d, i) => (
            <p key={i} className="text-sm text-ink-700">
              {d.statement}
            </p>
          ))}
        </div>
      )}

      {matchingTodos.length === 0 && decisions.length === 0 && (
        <p className="text-xs text-ink-400">새로 정리된 할 일이나 결정 사항은 없습니다.</p>
      )}
    </div>
  );
}
