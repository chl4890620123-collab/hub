import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { useCurrentUser, useLogout } from '@/hooks/useAuth';
import { authApi, meApi } from '@/api/endpoints/auth';
import type { AccountStatus } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = { ACTIVE: '사용 중', SUSPENDED: '사용 정지', WITHDRAWN: '탈퇴' };

function IdentitySummary() {
  const { data: user } = useCurrentUser();
  if (!user) return null;

  return (
    <Card>
      <CardContent className="grid grid-cols-2 gap-3 py-4 sm:grid-cols-4">
        <div>
          <p className="text-xs text-ink-400">아이디</p>
          <p className="text-sm font-medium text-ink-800">{user.loginId}</p>
        </div>
        <div>
          <p className="text-xs text-ink-400">이메일</p>
          <p className="text-sm font-medium text-ink-800">{user.email}</p>
        </div>
        <div>
          <p className="text-xs text-ink-400">권한</p>
          <p className="text-sm font-medium text-ink-800">{user.globalRole === 'ADMIN' ? '관리자' : '팀원'}</p>
        </div>
        <div>
          <p className="text-xs text-ink-400">계정 상태</p>
          <p className="text-sm font-medium text-ink-800">{ACCOUNT_STATUS_LABELS[user.accountStatus] ?? user.accountStatus}</p>
        </div>
      </CardContent>
    </Card>
  );
}

function ProfilePanel() {
  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const [jobTitle, setJobTitle] = useState(user?.jobTitle ?? '');

  useEffect(() => {
    setJobTitle(user?.jobTitle ?? '');
  }, [user]);

  const save = useMutation({
    mutationFn: () => meApi.updateProfile({ jobTitle }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['me'], updated);
      toast.success('프로필을 저장했습니다.');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!user) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>내 정보</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label>이름</Label>
            <Input value={user.displayName} disabled />
          </div>
          <div>
            <Label>회사</Label>
            <Input value={user.companyName ?? '-'} disabled />
          </div>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label htmlFor="department">부서</Label>
            <Input id="department" value={user.departmentName ?? '-'} disabled />
          </div>
          <div>
            <Label htmlFor="team">팀</Label>
            <Input id="team" value={user.teamName ?? '-'} disabled />
          </div>
        </div>
        <p className="text-xs text-ink-400">부서·팀 변경은 회사 관리자가 지정합니다.</p>
        <div>
          <Label htmlFor="jobTitle">직급/직책</Label>
          <Input id="jobTitle" value={jobTitle ?? ''} onChange={(e) => setJobTitle(e.target.value)} className="max-w-xs" />
        </div>
        <Button onClick={() => save.mutate()} disabled={save.isPending} className="self-start">
          저장
        </Button>
      </CardContent>
    </Card>
  );
}

function PasswordPanel({ forceChange }: { forceChange: boolean }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const navigate = useNavigate();

  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;

  const change = useMutation({
    mutationFn: () => authApi.changePassword(currentPassword, newPassword),
    onSuccess: (result) => {
      toast.success(result.message);
      navigate('/login');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>비밀번호 변경</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {forceChange && (
          <p className="rounded-md bg-amber-50 p-2 text-xs text-amber-700">
            관리자가 임시 비밀번호를 발급했습니다. 계속하려면 비밀번호를 변경해 주세요.
          </p>
        )}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div>
            <Label htmlFor="current-password">현재 비밀번호</Label>
            <Input id="current-password" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="new-password">새 비밀번호</Label>
            <Input id="new-password" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="confirm-password">새 비밀번호 확인</Label>
            <Input
              id="confirm-password"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>
        </div>
        {mismatch && <p className="text-xs text-red-600">새 비밀번호가 일치하지 않습니다.</p>}
        <Button
          onClick={() => change.mutate()}
          disabled={!currentPassword || !newPassword || mismatch || confirmPassword !== newPassword || change.isPending}
          className="self-start"
        >
          변경 (재로그인 필요)
        </Button>
      </CardContent>
    </Card>
  );
}

function WithdrawPanel() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [reason, setReason] = useState('');
  const logout = useLogout();
  const navigate = useNavigate();

  const withdraw = useMutation({
    mutationFn: () => meApi.withdraw(currentPassword, reason),
    onSuccess: (result) => {
      toast.success(`탈퇴가 처리되었습니다. (재배정 필요 항목 ${result.reassignmentCount}건)`);
      logout.mutate(undefined, { onSuccess: () => navigate('/login') });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>회원 탈퇴</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="text-xs text-ink-500">탈퇴 시 담당 중인 할 일은 재배정 대상으로 전환됩니다. 이 작업은 되돌릴 수 없습니다.</p>
        <Input
          type="password"
          placeholder="현재 비밀번호"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          className="max-w-xs"
        />
        <Input placeholder="탈퇴 사유 (선택)" value={reason} onChange={(e) => setReason(e.target.value)} className="max-w-xs" />
        <Button
          variant="danger"
          disabled={!currentPassword || withdraw.isPending}
          onClick={() => {
            if (confirm('정말 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) withdraw.mutate();
          }}
          className="self-start"
        >
          탈퇴하기
        </Button>
      </CardContent>
    </Card>
  );
}

export function AccountPage() {
  const [params] = useSearchParams();
  const forceChange = params.get('forcePasswordChange') === '1';

  return (
    <div>
      <PageHeader title="내 정보" description="프로필과 계정 정보를 관리합니다." />
      <div className="flex max-w-2xl flex-col gap-4">
        <IdentitySummary />
        <ProfilePanel />
        <PasswordPanel forceChange={forceChange} />
        <WithdrawPanel />
      </div>
    </div>
  );
}
