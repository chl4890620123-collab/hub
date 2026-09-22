import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

export function NotePromptDialog({
  open,
  onOpenChange,
  title,
  description,
  placeholder,
  required,
  confirmLabel,
  onSubmit,
  pending,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  placeholder?: string;
  required?: boolean;
  confirmLabel: string;
  onSubmit: (note: string) => void;
  pending?: boolean;
}) {
  const [note, setNote] = useState('');

  useEffect(() => {
    if (!open) setNote('');
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogTitle>{title}</DialogTitle>
        <div className="flex flex-col gap-3">
          {description && <p className="text-xs text-ink-500">{description}</p>}
          <Textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} placeholder={placeholder} autoFocus />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => onOpenChange(false)}>
              취소
            </Button>
            <Button disabled={(required && !note.trim()) || pending} onClick={() => onSubmit(note.trim())}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
