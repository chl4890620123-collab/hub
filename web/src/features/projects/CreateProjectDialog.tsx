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
  const [departmentName, setDepartmentName] = useState('');
  const [teamName, setTeamName] = useState('');
  const queryClient = useQueryClient();

  const create = useMutation({
    mutationFn: () => projectsApi.create(name.trim(), undefined, departmentName.trim() || undefined, teamName.trim() || undefined),
    onSuccess: () => {
      toast.success('프로젝트를 만들었습니다.');
      setName('');
      setDepartmentName('');
      setTeamName('');
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
            <Label htmlFor="new-project-name">공유 프로젝트/저장소 이름</Label>
            <Input id="new-project-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="new-project-department">부서 (선택)</Label>
              <Input id="new-project-department" value={departmentName} onChange={(e) => setDepartmentName(e.target.value)} placeholder="예: 개발부" />
            </div>
            <div>
              <Label htmlFor="new-project-team">팀 (선택)</Label>
              <Input id="new-project-team" value={teamName} onChange={(e) => setTeamName(e.target.value)} placeholder="예: 플랫폼팀" />
            </div>
          </div>
          <p className="text-xs text-ink-400">같은 부서·팀의 프로젝트는 선택 메뉴에서 함께 묶입니다. 이 프로젝트로 가져온 GitHub 저장소와 문서 자료는 프로젝트 구성원이 같이 사용합니다.</p>
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
