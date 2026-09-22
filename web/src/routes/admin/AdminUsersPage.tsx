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

export function AdminUsersPage() {
  const queryClient = useQueryClient();
  const { data: users, isLoading } = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list });
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [tempPassword, setTempPassword] = useState('');

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] });

  const setStatus = useMutation({
    mutationFn: ({ userId, status }: { userId: number; status: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN' }) =>
      adminUsersApi.setStatus(userId, status),
    onSuccess: (result) => {
      toast.success(`상태를 변경했습니다. (재배정 필요 ${result.reassignmentCount}건)`);
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
                    <td className="py-2 pr-3">
                      <Badge variant={u.globalRole === 'ADMIN' ? 'accent' : 'neutral'}>{u.globalRole}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge variant={u.accountStatus === 'ACTIVE' ? 'accent' : 'danger'}>{u.accountStatus}</Badge>
                    </td>
                    <td className="flex flex-wrap gap-1.5 py-2">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() =>
                          setStatus.mutate({ userId: u.id, status: u.accountStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' })
                        }
                      >
                        {u.accountStatus === 'ACTIVE' ? '정지' : '활성화'}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setRole.mutate({ userId: u.id, role: u.globalRole === 'ADMIN' ? 'MEMBER' : 'ADMIN' })}
                      >
                        {u.globalRole === 'ADMIN' ? '권한 내리기' : '관리자로 지정'}
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
