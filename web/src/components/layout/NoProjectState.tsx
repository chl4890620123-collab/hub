import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useIsAdmin } from '@/hooks/useAuth';
import { projectsApi } from '@/api/endpoints/projects';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

/** An admin with zero projects has no other way to get one - this is the only place that can offer
 * "만들기" instead of just telling them to ask an admin (who is themself). */
export function NoProjectState() {
  const isAdmin = useIsAdmin();
  const [name, setName] = useState('');
  const queryClient = useQueryClient();

  const create = useMutation({
    mutationFn: () => projectsApi.create(name.trim()),
    onSuccess: () => {
      toast.success('프로젝트를 만들었습니다.');
      setName('');
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-ink-300 py-16 text-center">
        <p className="text-sm font-medium text-ink-600">소속된 프로젝트가 없습니다.</p>
        <p className="mt-1 text-xs text-ink-400">관리자에게 프로젝트 배정을 요청해 주세요.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-ink-300 py-16 text-center">
      <p className="text-sm font-medium text-ink-600">아직 프로젝트가 없습니다.</p>
      <p className="text-xs text-ink-400">첫 프로젝트를 만들면 문서, 회의, 검색 기능을 사용할 수 있습니다.</p>
      <form
        className="mt-2 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) create.mutate();
        }}
      >
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="프로젝트 이름" className="w-56" />
        <Button type="submit" disabled={!name.trim() || create.isPending}>
          첫 프로젝트 만들기
        </Button>
      </form>
    </div>
  );
}
