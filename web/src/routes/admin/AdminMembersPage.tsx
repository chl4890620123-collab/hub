import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { adminSignupApi, adminProjectApi } from '@/api/endpoints/admin';
import { useProjects } from '@/hooks/useProjects';
import { formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function AdminMembersPage() {
  const queryClient = useQueryClient();
  const { data: applications, isLoading } = useQuery({ queryKey: ['admin-signups'], queryFn: adminSignupApi.list });
  const { data: projects = [] } = useProjects();
  const [projectChoice, setProjectChoice] = useState<Record<number, string>>({});
  const [addMemberUserId, setAddMemberUserId] = useState('');
  const [addMemberProjectId, setAddMemberProjectId] = useState('');

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-signups'] });

  const approve = useMutation({
    mutationFn: ({ userId, projectId }: { userId: number; projectId?: number }) => adminSignupApi.approve(userId, projectId),
    onSuccess: () => {
      toast.success('가입을 승인했습니다.');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const reject = useMutation({
    mutationFn: (userId: number) => adminSignupApi.reject(userId),
    onSuccess: () => {
      toast.success('가입을 거절했습니다.');
      invalidate();
    },
  });

  const addMember = useMutation({
    mutationFn: () => adminProjectApi.addMember(Number(addMemberProjectId), Number(addMemberUserId)),
    onSuccess: () => {
      toast.success('프로젝트에 추가했습니다.');
      setAddMemberUserId('');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>가입 신청 대기 목록</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <LoadingBlock />
          ) : !applications || applications.length === 0 ? (
            <EmptyState title="대기 중인 가입 신청이 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {applications.map((app) => (
                <li key={app.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-ink-100 px-3 py-2">
                  <div>
                    <p className="text-sm font-medium text-ink-800">
                      {app.displayName} <span className="text-ink-400">({app.loginId})</span>
                    </p>
                    <p className="text-xs text-ink-400">
                      {app.email} · {app.requestedRole} · {formatDateTime(app.createdAt)}
                    </p>
                    {app.requestedProjectName && <Badge variant="outline">희망: {app.requestedProjectName}</Badge>}
                  </div>
                  <div className="flex items-center gap-2">
                    {app.requestedRole === 'MEMBER' && (
                      <Select
                        value={projectChoice[app.id] ?? (app.requestedProjectId ? String(app.requestedProjectId) : '')}
                        onValueChange={(v) => setProjectChoice((prev) => ({ ...prev, [app.id]: v }))}
                      >
                        <SelectTrigger className="w-36">
                          <SelectValue placeholder="프로젝트" />
                        </SelectTrigger>
                        <SelectContent>
                          {projects.map((p) => (
                            <SelectItem key={p.id} value={String(p.id)}>
                              {p.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                    <Button
                      size="sm"
                      onClick={() =>
                        approve.mutate({
                          userId: app.id,
                          projectId: projectChoice[app.id] ? Number(projectChoice[app.id]) : app.requestedProjectId ?? undefined,
                        })
                      }
                    >
                      승인
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => reject.mutate(app.id)}>
                      거절
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>기존 사용자를 프로젝트에 추가</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap items-end gap-3">
          <div>
            <Input
              placeholder="사용자 ID"
              value={addMemberUserId}
              onChange={(e) => setAddMemberUserId(e.target.value)}
              className="w-32"
            />
          </div>
          <Select value={addMemberProjectId} onValueChange={setAddMemberProjectId}>
            <SelectTrigger className="w-44">
              <SelectValue placeholder="프로젝트 선택" />
            </SelectTrigger>
            <SelectContent>
              {projects.map((p) => (
                <SelectItem key={p.id} value={String(p.id)}>
                  {p.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button disabled={!addMemberUserId || !addMemberProjectId || addMember.isPending} onClick={() => addMember.mutate()}>
            추가
          </Button>
          <p className="w-full text-xs text-ink-400">
            사용자 ID는 사용자 관리 탭에서 확인할 수 있습니다.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
