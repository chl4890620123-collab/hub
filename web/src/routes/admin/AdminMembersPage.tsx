import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { adminSignupApi, adminProjectApi } from '@/api/endpoints/admin';
import { useCurrentProject, useProjects } from '@/hooks/useProjects';
import type { SignupApplication } from '@/api/types';
import { formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function ProjectMembersPanel() {
  const { currentProject, projects } = useCurrentProject();
  const queryClient = useQueryClient();
  const [moveTargetUserId, setMoveTargetUserId] = useState<number | null>(null);
  const [moveToProjectId, setMoveToProjectId] = useState('');

  const { data: members, isLoading } = useQuery({
    queryKey: ['project-members', currentProject?.id],
    queryFn: () => adminProjectApi.members(currentProject!.id),
    enabled: !!currentProject,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['project-members', currentProject?.id] });
    queryClient.invalidateQueries({ queryKey: ['admin-reassignments'] });
  };

  const remove = useMutation({
    mutationFn: (userId: number) => adminProjectApi.removeMember(currentProject!.id, userId),
    onSuccess: (result) => {
      toast.success(`프로젝트에서 제외했습니다. 담당자를 다시 정할 일 ${result.reassignmentCount}건`);
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const move = useMutation({
    mutationFn: () => adminProjectApi.moveMember(currentProject!.id, moveTargetUserId!, Number(moveToProjectId)),
    onSuccess: (result) => {
      toast.success(`프로젝트를 옮겼습니다. 담당자를 다시 정할 일 ${result.reassignmentCount}건`);
      setMoveTargetUserId(null);
      setMoveToProjectId('');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const toggleConfirm = useMutation({
    mutationFn: ({ userId, granted }: { userId: number; granted: boolean }) =>
      adminProjectApi.setConfirmPermission(currentProject!.id, userId, granted),
    onSuccess: (_, variables) => {
      toast.success(variables.granted ? '의사결정권자 권한을 줬습니다.' : '의사결정권자 권한을 뺐습니다.');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!currentProject) return null;
  const otherProjects = projects.filter((p) => p.id !== currentProject.id);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{currentProject.name} 프로젝트 구성원</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : !members || members.length === 0 ? (
          <EmptyState title="이 프로젝트에 추가된 사람이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-2">
            {members.map((m) => (
              <li key={m.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2">
                <div>
                  <p className="text-sm font-medium text-ink-800">{m.displayName}</p>
                  <p className="text-xs text-ink-400">@{m.loginId}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={toggleConfirm.isPending}
                    onClick={() => toggleConfirm.mutate({ userId: m.id, granted: !m.canConfirm })}
                  >
                    {m.canConfirm ? '의사결정권자 권한 빼기' : '의사결정권자 권한 주기'}
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setMoveTargetUserId(m.id)}>
                    다른 프로젝트로 옮기기
                  </Button>
                  <Button size="sm" variant="ghost" disabled={remove.isPending} onClick={() => remove.mutate(m.id)}>
                    이 프로젝트에서 빼기
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>

      <Dialog open={moveTargetUserId != null} onOpenChange={(open) => !open && setMoveTargetUserId(null)}>
        <DialogContent className="max-w-sm">
          <DialogTitle>이동할 프로젝트 선택</DialogTitle>
          <div className="flex flex-col gap-3">
            <Select value={moveToProjectId} onValueChange={setMoveToProjectId}>
              <SelectTrigger>
                <SelectValue placeholder="프로젝트 선택" />
              </SelectTrigger>
              <SelectContent>
                {otherProjects.map((p) => (
                  <SelectItem key={p.id} value={String(p.id)}>
                    {p.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button disabled={!moveToProjectId || move.isPending} onClick={() => move.mutate()}>
              이동
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

export function AdminMembersPage() {
  const queryClient = useQueryClient();
  const { data: applications, isLoading } = useQuery({ queryKey: ['admin-signups'], queryFn: adminSignupApi.list });
  const { data: projects = [] } = useProjects();
  const [projectChoice, setProjectChoice] = useState<Record<number, string>>({});
  const [addMemberUserId, setAddMemberUserId] = useState('');
  const [addMemberProjectId, setAddMemberProjectId] = useState('');
  const [rejectTarget, setRejectTarget] = useState<SignupApplication | null>(null);
  const [rejectReason, setRejectReason] = useState('');

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
    mutationFn: () => adminSignupApi.reject(rejectTarget!.id, rejectReason || undefined),
    onSuccess: () => {
      toast.success('가입을 거절했습니다.');
      setRejectTarget(null);
      setRejectReason('');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
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
                    <Button size="sm" variant="outline" onClick={() => setRejectTarget(app)}>
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

      <ProjectMembersPanel />

      <Dialog
        open={!!rejectTarget}
        onOpenChange={(open) => {
          if (!open) {
            setRejectTarget(null);
            setRejectReason('');
          }
        }}
      >
        <DialogContent className="max-w-sm">
          <DialogTitle>{rejectTarget?.displayName}님의 가입 신청을 거절할까요?</DialogTitle>
          <div className="flex flex-col gap-3">
            <Input placeholder="이유 (선택)" value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} />
            <div className="flex justify-end gap-2">
              <Button
                variant="ghost"
                onClick={() => {
                  setRejectTarget(null);
                  setRejectReason('');
                }}
              >
                취소
              </Button>
              <Button variant="danger" disabled={reject.isPending} onClick={() => reject.mutate()}>
                거절
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
