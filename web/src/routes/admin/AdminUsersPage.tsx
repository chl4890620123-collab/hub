import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { adminUsersApi } from '@/api/endpoints/admin';
import type { User } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const ROLE_LABELS = { ADMIN: '관리자', MEMBER: '일반 사용자' } as const;
const ACCOUNT_STATUS_LABELS = { ACTIVE: '사용 중', SUSPENDED: '사용 정지', WITHDRAWN: '탈퇴' } as const;

export function AdminUsersPage() {
  const queryClient = useQueryClient();
  const { data: users, isLoading } = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list });
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [tempPassword, setTempPassword] = useState('');
  const [suspendTarget, setSuspendTarget] = useState<User | null>(null);
  const [suspendReason, setSuspendReason] = useState('');

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] });

  const setStatus = useMutation({
    mutationFn: ({ userId, status, reason }: { userId: number; status: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'; reason?: string }) =>
      adminUsersApi.setStatus(userId, status, reason),
    onSuccess: (result) => {
      toast.success(result.reassignmentCount > 0
        ? `사용 상태를 변경했습니다. 담당자를 다시 정해야 할 업무가 ${result.reassignmentCount}건 있습니다.`
        : '사용 상태를 변경했습니다.');
      setSuspendTarget(null);
      setSuspendReason('');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const setRole = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: 'ADMIN' | 'MEMBER' }) => adminUsersApi.setRole(userId, role),
    onSuccess: () => {
      toast.success('권한을 변경했습니다.');
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const resetPassword = useMutation({
    mutationFn: () => adminUsersApi.resetPassword(resetTarget!.id, tempPassword),
    onSuccess: () => {
      toast.success('임시 비밀번호를 발급했습니다.');
      setResetTarget(null);
      setTempPassword('');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>전체 사용자</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : !users || users.length === 0 ? (
          <EmptyState title="사용자가 없습니다." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-ink-200 text-left text-xs text-ink-400">
                  <th className="py-2 pr-3">ID</th>
                  <th className="py-2 pr-3">이름</th>
                  <th className="py-2 pr-3">아이디</th>
                  <th className="py-2 pr-3">소속</th>
                  <th className="py-2 pr-3">권한</th>
                  <th className="py-2 pr-3">상태</th>
                  <th className="py-2 pr-3">작업</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id} className="border-b border-ink-100">
                    <td className="py-2 pr-3 text-ink-400">{u.id}</td>
                    <td className="py-2 pr-3 font-medium text-ink-800">{u.displayName}</td>
                    <td className="py-2 pr-3 text-ink-500">{u.loginId}</td>
                    <td className="py-2 pr-3 text-xs text-ink-500">
                      {[u.companyName || '회사 미입력', u.departmentName, u.teamName].filter(Boolean).join(' · ')}
                      <br />
                      {u.email} · {u.jobTitle || '직급 미입력'}
                    </td>
                    <td className="py-2 pr-3">
                      <Badge variant={u.globalRole === 'ADMIN' ? 'accent' : 'neutral'}>{ROLE_LABELS[u.globalRole]}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge variant={u.accountStatus === 'ACTIVE' ? 'accent' : 'danger'}>{ACCOUNT_STATUS_LABELS[u.accountStatus]}</Badge>
                    </td>
                    <td className="flex flex-wrap gap-1.5 py-2">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          if (u.accountStatus === 'ACTIVE') setSuspendTarget(u);
                          else setStatus.mutate({ userId: u.id, status: 'ACTIVE' });
                        }}
                      >
                        {u.accountStatus === 'ACTIVE' ? '정지' : '활성화'}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setRole.mutate({ userId: u.id, role: u.globalRole === 'ADMIN' ? 'MEMBER' : 'ADMIN' })}
                      >
                        {u.globalRole === 'ADMIN' ? '일반 사용자로 변경' : '관리자로 변경'}
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setResetTarget(u)}>
                        비밀번호 초기화
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>

      <Dialog
        open={!!suspendTarget}
        onOpenChange={(open) => {
          if (!open) {
            setSuspendTarget(null);
            setSuspendReason('');
          }
        }}
      >
        <DialogContent>
          <DialogTitle>{suspendTarget?.displayName}님의 사용을 잠시 멈출까요?</DialogTitle>
          <div className="flex flex-col gap-3">
            <p className="text-xs text-ink-400">
              이 사용자는 바로 로그인할 수 없게 되고, 프로젝트 배정이 해제되어 미완료 업무는 담당자를 다시 정해야 할 수 있습니다.
            </p>
            <Input
              type="text"
              placeholder="이유 (선택)"
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
            />
            <div className="flex justify-end gap-2">
              <Button
                variant="ghost"
                onClick={() => {
                  setSuspendTarget(null);
                  setSuspendReason('');
                }}
              >
                취소
              </Button>
              <Button
                variant="danger"
                disabled={setStatus.isPending}
                onClick={() => setStatus.mutate({ userId: suspendTarget!.id, status: 'SUSPENDED', reason: suspendReason || undefined })}
              >
                정지
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={!!resetTarget} onOpenChange={(open) => !open && setResetTarget(null)}>
        <DialogContent>
          <DialogTitle>{resetTarget?.displayName} 비밀번호 초기화</DialogTitle>
          <div className="flex flex-col gap-3">
            <Input
              type="text"
              placeholder="임시 비밀번호 (12자 이상, 문자+숫자)"
              value={tempPassword}
              onChange={(e) => setTempPassword(e.target.value)}
            />
            <Button disabled={tempPassword.length < 12 || resetPassword.isPending} onClick={() => resetPassword.mutate()}>
              초기화
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
