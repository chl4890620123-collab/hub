import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { ViewModeToggle, type ViewMode } from '@/components/layout/ViewModeToggle';
import { CalendarGrid } from '@/components/layout/CalendarGrid';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { useCanConfirm, useCurrentProject } from '@/hooks/useProjects';
import { useCurrentUser, useIsAdmin } from '@/hooks/useAuth';
import { todosApi } from '@/api/endpoints/todos';
import { projectsApi } from '@/api/endpoints/projects';
import { TodoCard } from '@/features/todos/TodoCard';
import { FileTransferPanel } from '@/features/todos/FileTransferPanel';
import { useEvidenceStore } from '@/stores/evidenceStore';
import type { TaskStatus, TodoItem } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function TodoProgressPanel({ todos, month }: { todos: TodoItem[]; month: string }) {
  const rows = todos.filter((t) => t.reviewStatus === 'CONFIRMED' && !!t.dueDate && t.dueDate.startsWith(month));
  const total = rows.length;
  const done = rows.filter((t) => t.taskStatus === 'DONE').length;
  const activeRows = rows.filter((t) => t.assignmentStatus === 'ACTIVE');
  const doing = activeRows.filter((t) => t.taskStatus === 'IN_PROGRESS').length;
  const waiting = activeRows.filter((t) => t.taskStatus === 'TODO').length;
  const blocked = activeRows.filter((t) => t.taskStatus === 'BLOCKED').length;
  const reassign = rows.filter((t) => t.assignmentStatus === 'REASSIGNMENT_REQUIRED').length;
  const pct = total ? Math.round((done * 100) / total) : 0;

  return (
    <div className="mb-4 rounded-md border border-ink-200 bg-white p-3 dark:bg-ink-100">
      <div className="mb-2 flex items-center justify-between text-sm">
        <span className="text-ink-500">이번 달 진행 {pct}%</span>
        <strong className="text-ink-800">{done}/{total || 0} 완료</strong>
      </div>
      <div className="mb-3 h-1.5 overflow-hidden rounded-full bg-ink-100">
        <div className="h-full rounded-full bg-accent-500" style={{ width: `${pct}%` }} />
      </div>
      <div className="flex gap-4 text-xs">
        <span>
          <strong className="text-ink-800">{doing}</strong> <span className="text-ink-400">진행 중</span>
        </span>
        <span>
          <strong className="text-ink-800">{waiting}</strong> <span className="text-ink-400">시작 전</span>
        </span>
        <span>
          <strong className="text-ink-800">{blocked}</strong> <span className="text-ink-400">도움 필요</span>
        </span>
        {reassign > 0 && (
          <span>
            <strong className="text-ink-800">{reassign}</strong> <span className="text-ink-400">새 담당자 필요</span>
          </span>
        )}
      </div>
    </div>
  );
}

export function TodosPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  const isAdmin = useIsAdmin();
  const canConfirm = useCanConfirm();
  const queryClient = useQueryClient();
  const openEvidence = useEvidenceStore((s) => s.open);
  const [searchParams] = useSearchParams();

  const [cursor, setCursor] = useState(() => new Date());
  const [viewMode, setViewMode] = useState<ViewMode>(() => (searchParams.get('view') === 'calendar' ? 'calendar' : 'list'));
  const [statusFilter, setStatusFilter] = useState<TaskStatus | 'ALL'>('ALL');
  const [assigneeFilter, setAssigneeFilter] = useState('ALL');

  const year = cursor.getFullYear();
  const month = cursor.getMonth() + 1;
  const monthKey = `${year}-${String(month).padStart(2, '0')}`;

  const { data: monthTodos, isLoading: loadingMonth } = useQuery({
    queryKey: ['todos-month', currentProject?.id, year, month],
    queryFn: () => todosApi.month(currentProject!.id, year, month),
    enabled: !!currentProject,
  });
  const { data: undated, isLoading: loadingUndated } = useQuery({
    queryKey: ['todos-undated', currentProject?.id],
    queryFn: () => todosApi.undated(currentProject!.id),
    enabled: !!currentProject,
  });
  const { data: members } = useQuery({
    queryKey: ['project-members', currentProject?.id],
    queryFn: () => projectsApi.members(currentProject!.id),
    enabled: !!currentProject,
  });

  const invalidateTodos = () => {
    queryClient.invalidateQueries({ queryKey: ['todos-month', currentProject?.id] });
    queryClient.invalidateQueries({ queryKey: ['todos-undated', currentProject?.id] });
  };

  const statusMutation = useMutation({
    mutationFn: ({ todoId, status }: { todoId: number; status: TaskStatus }) => todosApi.updateStatus(todoId, status),
    onSuccess: invalidateTodos,
    onError: (error) => toast.error(errorMessage(error)),
  });
  const requestCompletionMutation = useMutation({
    mutationFn: (todoId: number) => todosApi.requestCompletion(todoId),
    onSuccess: () => {
      toast.success('완료 승인을 요청했습니다.');
      invalidateTodos();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const approveCompletionMutation = useMutation({
    mutationFn: (todoId: number) => todosApi.approveCompletion(todoId),
    onSuccess: () => {
      toast.success('완료를 승인했습니다.');
      invalidateTodos();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const rejectCompletionMutation = useMutation({
    mutationFn: ({ todoId, reason }: { todoId: number; reason: string }) => todosApi.rejectCompletion(todoId, reason || undefined),
    onSuccess: () => {
      toast.success('완료를 반려했습니다.');
      invalidateTodos();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const requestHelpMutation = useMutation({
    mutationFn: ({ todoId, note }: { todoId: number; note: string }) => todosApi.requestHelp(todoId, note),
    onSuccess: () => {
      toast.success('도움을 요청했습니다.');
      invalidateTodos();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const resolveHelpMutation = useMutation({
    mutationFn: (todoId: number) => todosApi.resolveHelp(todoId),
    onSuccess: invalidateTodos,
    onError: (error) => toast.error(errorMessage(error)),
  });

  const evidenceMutation = useMutation({
    mutationFn: (todo: TodoItem) => todosApi.evidence(todo.id).then((items) => ({ todo, items })),
    onSuccess: ({ todo, items }) => openEvidence(todo.title, items),
    onError: (error) => toast.error(errorMessage(error)),
  });

  const allTodos = useMemo(() => [...(monthTodos ?? []), ...(undated ?? [])], [monthTodos, undated]);
  const filtered = useMemo(
    () =>
      (statusFilter === 'ALL' ? allTodos.filter((t) => t.taskStatus !== 'DONE') : allTodos.filter((t) => t.taskStatus === statusFilter))
        .filter((t) => assigneeFilter === 'ALL' || String(t.assigneeId ?? '') === assigneeFilter)
        .sort((a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31')),
    [allTodos, statusFilter, assigneeFilter],
  );

  if (!currentProject || !user) return <NoProjectState />;

  const isAssignee = (todo: TodoItem) =>
    todo.assignmentStatus === 'ACTIVE' && (isAdmin || (todo.reviewStatus === 'CONFIRMED' && todo.assigneeId === user.id));

  const cardFor = (todo: TodoItem, compact?: boolean) => (
    <TodoCard
      todo={todo}
      isAssignee={isAssignee(todo)}
      canConfirm={canConfirm}
      onStatusChange={(status) => statusMutation.mutate({ todoId: todo.id, status })}
      onRequestCompletion={() => requestCompletionMutation.mutate(todo.id)}
      onApproveCompletion={() => approveCompletionMutation.mutate(todo.id)}
      onRejectCompletion={(reason) => rejectCompletionMutation.mutate({ todoId: todo.id, reason })}
      onRequestHelp={(note) => requestHelpMutation.mutate({ todoId: todo.id, note })}
      onResolveHelp={() => resolveHelpMutation.mutate(todo.id)}
      onShowEvidence={() => evidenceMutation.mutate(todo)}
      compact={compact}
    />
  );

  const isLoading = loadingMonth || loadingUndated;

  return (
    <div>
      <PageHeader
        title="할 일·일정"
        description="확정된 할 일을 확인하고 진행 상태를 관리합니다. 담당자가 완료를 요청하면 의사결정권자 또는 관리자가 승인해야 완료됩니다."
        action={<ViewModeToggle value={viewMode} onChange={setViewMode} />}
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" aria-label="이전 달" onClick={() => setCursor(new Date(year, month - 2, 1))}>
            <ChevronLeft size={14} />
          </Button>
          <span className="w-24 text-center text-sm font-medium text-ink-700">
            {year}년 {month}월
          </span>
          <Button variant="outline" size="icon" aria-label="다음 달" onClick={() => setCursor(new Date(year, month, 1))}>
            <ChevronRight size={14} />
          </Button>
        </div>
        <Select value={assigneeFilter} onValueChange={setAssigneeFilter}>
          <SelectTrigger className="w-36">
            <SelectValue placeholder="담당자 전체" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">담당자 전체</SelectItem>
            {members?.map((m) => (
              <SelectItem key={m.id} value={String(m.id)}>
                {m.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="flex gap-1">
          {(['ALL', 'TODO', 'IN_PROGRESS', 'DONE', 'BLOCKED'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={
                'rounded-full px-2.5 py-1 text-xs font-medium ' +
                (statusFilter === s ? 'bg-accent-600 text-white' : 'bg-ink-100 text-ink-500 hover:bg-ink-200')
              }
            >
              {s === 'ALL' ? '미완료 전체' : s === 'TODO' ? '시작 전' : s === 'IN_PROGRESS' ? '진행 중' : s === 'DONE' ? '완료' : '도움 필요'}
            </button>
          ))}
        </div>
      </div>

      <TodoProgressPanel todos={allTodos} month={monthKey} />

      {isLoading ? (
        <LoadingBlock />
      ) : viewMode === 'calendar' ? (
        <CalendarGrid items={filtered} getDate={(t) => t.dueDate} renderItem={(t) => cardFor(t, true)} year={year} month={month} />
      ) : filtered.length === 0 ? (
        <EmptyState title="조건에 맞는 할 일이 없습니다." />
      ) : (
        <div className={viewMode === 'grid' ? 'grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3' : 'flex flex-col gap-2'}>
          {filtered.map((todo) => (
            <div key={todo.id}>{cardFor(todo)}</div>
          ))}
        </div>
      )}

      <div className="mt-6">
        <FileTransferPanel projectId={currentProject.id} />
      </div>
    </div>
  );
}
