import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { ViewModeToggle, type ViewMode } from '@/components/layout/ViewModeToggle';
import { CalendarGrid } from '@/components/layout/CalendarGrid';
import { Button } from '@/components/ui/button';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { useCurrentProject } from '@/hooks/useProjects';
import { useCurrentUser, useIsAdmin } from '@/hooks/useAuth';
import { todosApi } from '@/api/endpoints/todos';
import { TodoCard } from '@/features/todos/TodoCard';
import { FileTransferPanel } from '@/features/todos/FileTransferPanel';
import { useEvidenceStore } from '@/stores/evidenceStore';
import type { TaskStatus, TodoItem } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function TodosPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  const isAdmin = useIsAdmin();
  const queryClient = useQueryClient();
  const openEvidence = useEvidenceStore((s) => s.open);
  const [searchParams] = useSearchParams();

  const [cursor, setCursor] = useState(() => new Date());
  const [viewMode, setViewMode] = useState<ViewMode>(() => (searchParams.get('view') === 'calendar' ? 'calendar' : 'list'));
  const [statusFilter, setStatusFilter] = useState<TaskStatus | 'ALL'>('ALL');

  const year = cursor.getFullYear();
  const month = cursor.getMonth() + 1;

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

  const statusMutation = useMutation({
    mutationFn: ({ todoId, status }: { todoId: number; status: TaskStatus }) => todosApi.updateStatus(todoId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['todos-month', currentProject?.id] });
      queryClient.invalidateQueries({ queryKey: ['todos-undated', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const evidenceMutation = useMutation({
    mutationFn: (todo: TodoItem) => todosApi.evidence(todo.id).then((items) => ({ todo, items })),
    onSuccess: ({ todo, items }) => openEvidence(todo.title, items),
  });

  const allTodos = useMemo(() => [...(monthTodos ?? []), ...(undated ?? [])], [monthTodos, undated]);
  const filtered = useMemo(
    () =>
      (statusFilter === 'ALL' ? allTodos.filter((t) => t.taskStatus !== 'DONE') : allTodos.filter((t) => t.taskStatus === statusFilter)).sort(
        (a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31'),
      ),
    [allTodos, statusFilter],
  );

  if (!currentProject || !user) return <NoProjectState />;

  const canEdit = (todo: TodoItem) => isAdmin || (todo.reviewStatus === 'CONFIRMED' && todo.assigneeId === user.id);

  const cardFor = (todo: TodoItem, compact?: boolean) => (
    <TodoCard
      todo={todo}
      canEdit={canEdit(todo)}
      onStatusChange={(status) => statusMutation.mutate({ todoId: todo.id, status })}
      onShowEvidence={() => evidenceMutation.mutate(todo)}
      compact={compact}
    />
  );

  const isLoading = loadingMonth || loadingUndated;

  return (
    <div>
      <PageHeader
        title="할 일·일정"
        description="확정된 할 일을 월별로 확인하고 상태를 관리합니다."
        action={<ViewModeToggle value={viewMode} onChange={setViewMode} />}
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => setCursor(new Date(year, month - 2, 1))}>
            <ChevronLeft size={14} />
          </Button>
          <span className="w-24 text-center text-sm font-medium text-ink-700">
            {year}년 {month}월
          </span>
          <Button variant="outline" size="icon" onClick={() => setCursor(new Date(year, month, 1))}>
            <ChevronRight size={14} />
          </Button>
        </div>
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
              {s === 'ALL' ? '진행 중' : s === 'TODO' ? '시작 전' : s === 'IN_PROGRESS' ? '진행 중' : s === 'DONE' ? '완료' : '보류'}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <LoadingBlock />
      ) : filtered.length === 0 ? (
        <EmptyState title="조건에 맞는 할 일이 없습니다." />
      ) : viewMode === 'calendar' ? (
        <CalendarGrid items={filtered} getDate={(t) => t.dueDate} renderItem={(t) => cardFor(t, true)} year={year} month={month} />
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
