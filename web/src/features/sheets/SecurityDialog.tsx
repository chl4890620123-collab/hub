import { useState } from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import type { SecurityInput } from '@/api/endpoints/sheets';

export function SecurityDialog({
  open,
  hasPassword,
  onSubmit,
  onCancel,
}: {
  open: boolean;
  hasPassword: boolean;
  onSubmit: (input: SecurityInput) => void;
  onCancel: () => void;
}) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [hint, setHint] = useState('');

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="max-w-sm">
        <DialogTitle>보안 설정</DialogTitle>
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            onSubmit({ currentPassword: currentPassword || null, newPassword: newPassword || null, hint: hint || null });
          }}
        >
          <div>
            <Label>{hasPassword ? '현재 비밀번호' : '설정된 비밀번호가 없습니다'}</Label>
            <Input
              type="password"
              disabled={!hasPassword}
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>
          <div>
            <Label>새 비밀번호 (4자 이상, 비우면 잠금 해제)</Label>
            <Input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
            />
          </div>
          <div>
            <Label>힌트 (선택)</Label>
            <Input value={hint} onChange={(e) => setHint(e.target.value)} maxLength={300} />
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onCancel}>
              취소
            </Button>
            <Button type="submit">저장</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
