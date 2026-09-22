import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Input, Label } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { projectsApi } from '@/api/endpoints/projects';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function CreateProjectDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [name, setName] = useState('');
  const queryClient = useQueryClient();

  const create = useMutation({
    mutationFn: () => projectsApi.create(name.trim()),
    onSuccess: () => {
      toast.success('프로젝트를 만들었습니다.');
      setName('');
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      onOpenChange(false);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogTitle>새 프로젝트</DialogTitle>
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (name.trim()) create.mutate();
          }}
        >
          <div>
            <Label htmlFor="new-project-name">프로젝트 이름</Label>
            <Input id="new-project-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              취소
            </Button>
            <Button type="submit" disabled={!name.trim() || create.isPending}>
              만들기
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
