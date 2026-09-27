import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { connectorsApi } from '@/api/endpoints/connectors';
import { documentsApi } from '@/api/endpoints/documents';
import type { DocumentRow } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function defaultExportPath(name: string) {
  const clean = name.trim().replace(/[\\/]+/g, '-');
  if (/\.(md|txt|json|csv|ya?ml|xml|html?)$/i.test(clean)) return clean;
  return clean.replace(/\.[^.]+$/, '') + '.md';
}

export function GitHubExportDialog({
  projectId,
  document,
  open,
  onOpenChange,
}: {
  projectId: number;
  document: DocumentRow | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [repository, setRepository] = useState('');
  const [path, setPath] = useState('');
  const [branch, setBranch] = useState('');
  const [mode, setMode] = useState<'PR' | 'SAVE_AS'>('PR');

  const { data, isLoading } = useQuery({
    queryKey: ['connector-targets', projectId, 'GITHUB', 'export'],
    queryFn: () => connectorsApi.targets(projectId, 'GITHUB'),
    enabled: open,
  });

  useEffect(() => {
    if (!open || !document) return;
    setPath(defaultExportPath(document.original_name));
    setMode('PR');
    setBranch('');
  }, [open, document]);

  useEffect(() => {
    if (!repository && data?.targets?.length) setRepository(data.targets[0].id);
  }, [data, repository]);

  const selected = useMemo(() => data?.targets.find((target) => target.id === repository), [data, repository]);

  const submit = useMutation({
    mutationFn: () => documentsApi.exportToGitHub(projectId, document!.id, {
      repository,
      path,
      mode,
      branch: branch.trim() || undefined,
    }),
    onSuccess: (result) => {
      toast.success(result.status === 'PULL_REQUEST_CREATED' ? 'GitHub Pull Request를 만들었습니다.' : 'GitHub 저장소에 새 파일로 저장했습니다.');
      if (result.pullRequestUrl) window.open(result.pullRequestUrl, '_blank', 'noopener,noreferrer');
      onOpenChange(false);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogTitle>GitHub로 보내기</DialogTitle>
        <DialogDescription className="mb-4 text-sm text-ink-500">
          기본은 새 브랜치와 Pull Request로 제안합니다. 직접 저장은 같은 이름을 덮어쓰지 않고 새 파일만 만듭니다.
        </DialogDescription>
        {isLoading ? (
          <LoadingBlock />
        ) : !data?.connected ? (
          <EmptyState title="GitHub 계정이 연결되지 않았습니다." description="연결 서비스에서 GitHub를 먼저 연결해 주세요." />
        ) : data.targets.length === 0 ? (
          <EmptyState title="쓸 수 있는 GitHub 저장소가 없습니다." />
        ) : (
          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-ink-700">대상 저장소</span>
              <select
                value={repository}
                onChange={(e) => setRepository(e.target.value)}
                className="h-10 rounded-md border border-ink-200 bg-white px-3 text-sm text-ink-800"
              >
                {data.targets.map((target) => <option key={target.id} value={target.id}>{target.name}</option>)}
              </select>
              {selected?.description && <span className="text-xs text-ink-400">{selected.description}</span>}
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-ink-700">저장 경로 / 다른 이름</span>
              <Input value={path} onChange={(e) => setPath(e.target.value)} placeholder="docs/회의록.md" />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-ink-700">기준 브랜치</span>
              <Input value={branch} onChange={(e) => setBranch(e.target.value)} placeholder="비워두면 저장소 기본 브랜치" />
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setMode('PR')} className={'rounded-md border px-3 py-3 text-left text-sm ' + (mode === 'PR' ? 'border-accent-500 bg-accent-50' : 'border-ink-200')}>
                <strong className="block text-ink-800">PR로 제안</strong>
                <span className="text-xs text-ink-500">새 브랜치 생성 후 검토·병합</span>
              </button>
              <button type="button" onClick={() => setMode('SAVE_AS')} className={'rounded-md border px-3 py-3 text-left text-sm ' + (mode === 'SAVE_AS' ? 'border-accent-500 bg-accent-50' : 'border-ink-200')}>
                <strong className="block text-ink-800">다른 이름으로 저장</strong>
                <span className="text-xs text-ink-500">선택 브랜치에 새 파일 생성</span>
              </button>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => onOpenChange(false)}>취소</Button>
              <Button disabled={!repository || !path.trim() || submit.isPending} onClick={() => submit.mutate()}>
                {submit.isPending ? '보내는 중…' : mode === 'PR' ? 'PR 만들기' : '새 파일로 저장'}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
