import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { BulkSelectionBar } from '@/features/review/BulkSelectionBar';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { adminReassignmentApi, adminProjectApi } from '@/api/endpoints/admin';
import { useCurrentProject } from '@/hooks/useProjects';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';

export function AdminReassignPage() {
  const { currentProject } = useCurrentProject();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkAssignee, setBulkAssignee] = useState('');

  const { data: requests, isLoading } = useQuery({
    queryKey: ['admin-reassignments', currentProject?.id],
    queryFn: () => adminReassignmentApi.pending(currentProject!.id),
    enabled: !!currentProject,
  });
  const { data: members } = useQuery({
    queryKey: ['project-members', currentProject?.id],
    queryFn: () => adminProjectApi.members(currentProject!.id),
    enabled: !!currentProject,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-reassignments', currentProject?.id] });

  const resolve = useMutation({
    mutationFn: ({ id, newAssigneeId }: { id: number; newAssigneeId: number }) => adminReassignmentApi.resolve(id, newAssigneeId),
    onSuccess: invalidate,
    onError: (error) => toast.error(errorMessage(error)),
  });

  const bulkResolve = useMutation({
    mutationFn: () => adminReassignmentApi.bulkResolve(currentProject!.id, [...selected], Number(bulkAssignee)),
    onSuccess: () => {
      toast.success('선택한 항목을 일괄 재배정했습니다.');
      setSelected(new Set());
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!currentProject) return <NoProjectState />;

  return (
    <Card>
      <CardHeader>
        <CardTitle>재배정 대기 목록</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : !requests || requests.length === 0 ? (
          <EmptyState title="재배정 대기 중인 항목이 없습니다." />
        ) : (
          <>
            <BulkSelectionBar count={selected.size} onClear={() => setSelected(new Set())}>
              <Select value={bulkAssignee} onValueChange={setBulkAssignee}>
                <SelectTrigger className="w-36">
                  <SelectValue placeholder="새 담당자" />
                </SelectTrigger>
                <SelectContent>
                  {members && members.length === 0 ? (
                    <SelectItem value="__no-members" disabled>
                      배정 가능한 팀원이 없습니다
                    </SelectItem>
                  ) : (
                    members?.map((m) => (
                      <SelectItem key={m.id} value={String(m.id)}>
                        {m.displayName}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              <Button size="sm" disabled={!bulkAssignee || bulkResolve.isPending} onClick={() => bulkResolve.mutate()}>
                일괄 재배정
              </Button>
            </BulkSelectionBar>

            <ul className="flex flex-col gap-2">
              {requests.map((req) => (
                <li key={req.id} className="flex items-center justify-between gap-3 rounded-md border border-ink-100 px-3 py-2">
                  <label className="flex min-w-0 flex-1 items-start gap-2 text-sm text-ink-700">
                    <input
                      type="checkbox"
                      checked={selected.has(req.id)}
                      onChange={() =>
                        setSelected((prev) => {
                          const next = new Set(prev);
                          next.has(req.id) ? next.delete(req.id) : next.add(req.id);
                          return next;
                        })
                      }
                      className="mt-1"
                    />
                    <div className="min-w-0">
                      <p className="truncate font-medium text-ink-800">{req.title ?? `할 일 #${req.todo_id ?? req.id}`}</p>
                      <p className="text-xs text-ink-400">
                        {req.former_assignee_name ? `기존 담당자: ${req.former_assignee_name}` : '기존 담당자 없음'}
                        {req.due_date ? ` · 기한 ${formatDate(req.due_date)}` : ' · 기한 없음'}
                      </p>
                      {req.reason && <p className="mt-1 text-xs text-ink-500">재배정 사유: {req.reason}</p>}
                    </div>
                  </label>
                  <SingleResolveControl members={members ?? []} onResolve={(assigneeId) => resolve.mutate({ id: req.id, newAssigneeId: assigneeId })} />
                </li>
              ))}
            </ul>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function SingleResolveControl({
  members,
  onResolve,
}: {
  members: { id: number; displayName: string }[];
  onResolve: (assigneeId: number) => void;
}) {
  const [value, setValue] = useState('');
  return (
    <div className="flex items-center gap-2">
      <Select value={value} onValueChange={setValue}>
        <SelectTrigger className="w-32">
          <SelectValue placeholder="담당자" />
        </SelectTrigger>
        <SelectContent>
          {members.length === 0 ? (
            <SelectItem value="__no-members" disabled>
              배정 가능한 팀원이 없습니다
            </SelectItem>
          ) : (
            members.map((m) => (
              <SelectItem key={m.id} value={String(m.id)}>
                {m.displayName}
              </SelectItem>
            ))
          )}
        </SelectContent>
      </Select>
      <Button size="sm" disabled={!value} onClick={() => onResolve(Number(value))}>
        재배정
      </Button>
    </div>
  );
}
