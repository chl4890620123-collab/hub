import { useState } from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

/** Shown whenever opening/editing a 자료표 comes back 423 SHEET_LOCKED. The hint (if the owner set
 * one) is shown in the clear right here - see the backend migration comment for why that's the point. */
export function PasswordPromptDialog({
  open,
  hint,
  onSubmit,
  onCancel,
}: {
  open: boolean;
  hint?: string | null;
  onSubmit: (password: string) => void;
  onCancel: () => void;
}) {
  const [value, setValue] = useState('');

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent
        onOpenAutoFocus={(e) => e.preventDefault()}
        className="max-w-sm"
      >
        <DialogTitle>비밀번호로 잠긴 표</DialogTitle>
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            onSubmit(value);
            setValue('');
          }}
        >
          <Input
            type="password"
            autoFocus
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="비밀번호"
          />
          {hint && <p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">힌트: {hint}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onCancel}>
              취소
            </Button>
            <Button type="submit">열기</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
