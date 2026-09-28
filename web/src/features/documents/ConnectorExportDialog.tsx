import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { connectorsApi } from '@/api/endpoints/connectors';
import { documentsApi } from '@/api/endpoints/documents';
import type { ConnectorType, DocumentRow } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function defaultExportPath(name: string) {
  const clean = name.trim().replace(/[\\/]+/g, '-');
  if (/\.(md|txt|json|csv|ya?ml|xml|html?)$/i.test(clean)) return clean;
  return clean.replace(/\.[^.]+$/, '') + '.md';
}

const PROVIDERS: { type: ConnectorType; label: string; targetNoun: string }[] = [
  { type: 'GITHUB', label: 'GitHub', targetNoun: '저장소' },
  { type: 'GOOGLE_DRIVE', label: 'Google Drive', targetNoun: '폴더' },
  { type: 'SLACK', label: 'Slack', targetNoun: '채널' },
  { type: 'NOTION', label: 'Notion', targetNoun: '페이지' },
];

export function ConnectorExportDialog({
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
  const [provider, setProvider] = useState<ConnectorType>('GITHUB');
  const [targetId, setTargetId] = useState('');
  const [cursor, setCursor] = useState<string | null>(null);
  const [cursorHistory, setCursorHistory] = useState<(string | null)[]>([]);

  // GitHub-only fields
  const [path, setPath] = useState('');
  const [branch, setBranch] = useState('');
  const [mode, setMode] = useState<'PR' | 'SAVE_AS'>('PR');
  // Google Drive-only
  const [filename, setFilename] = useState('');
  // Slack-only
  const [message, setMessage] = useState('');
  // Notion-only
  const [title, setTitle] = useState('');

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['connector-targets', projectId, provider, 'export', cursor ?? ''],
    queryFn: () => connectorsApi.targets(projectId, provider, cursor, 50),
    enabled: open,
    placeholderData: (previous) => previous,
  });

  useEffect(() => {
    if (!open || !document) return;
    setProvider('GITHUB');
    setPath(defaultExportPath(document.original_name));
    setMode('PR');
    setBranch('');
    setFilename(document.original_name);
    setMessage('');
    setTitle(document.original_name);
    setTargetId('');
    setCursor(null);
    setCursorHistory([]);
  }, [open, document]);

  // Switching provider mid-dialog re-browses that provider's own targets from page 1.
  useEffect(() => {
    setTargetId('');
    setCursor(null);
    setCursorHistory([]);
  }, [provider]);

  useEffect(() => {
    if (!data?.targets?.length) {
      setTargetId('');
      return;
    }
    if (!data.targets.some((target) => target.id === targetId)) setTargetId(data.targets[0].id);
  }, [data, targetId]);

  const selected = useMemo(() => data?.targets.find((target) => target.id === targetId), [data, targetId]);
  const page = cursorHistory.length + 1;
  const info = PROVIDERS.find((p) => p.type === provider)!;

  const submit = useMutation({
    mutationFn: async (): Promise<{ message: string; openUrl: string | null }> => {
      if (!document) throw new Error('문서를 다시 선택해 주세요.');
      if (provider === 'GITHUB') {
        const result = await documentsApi.exportToGitHub(projectId, document.id, {
          repository: targetId,
          path,
          mode,
          branch: branch.trim() || undefined,
        });
        return {
          message: result.status === 'PULL_REQUEST_CREATED' ? 'GitHub Pull Request를 만들었습니다.' : 'GitHub 저장소에 새 파일로 저장했습니다.',
          openUrl: result.pullRequestUrl,
        };
      }
      if (provider === 'GOOGLE_DRIVE') {
        const result = await documentsApi.exportToDrive(projectId, document.id, {
          folderId: targetId,
          filename: filename.trim() || undefined,
        });
        return { message: 'Google Drive에 새 파일로 저장했습니다.', openUrl: result.webViewLink };
      }
      if (provider === 'SLACK') {
        const result = await documentsApi.exportToSlack(projectId, document.id, {
          channelId: targetId,
          message: message.trim() || undefined,
        });
        return { message: 'Slack 채널에 문서를 보냈습니다.', openUrl: result.permalink };
      }
      const result = await documentsApi.exportToNotion(projectId, document.id, {
        parentPageId: targetId,
        title: title.trim() || undefined,
      });
      return { message: 'Notion에 새 페이지를 만들었습니다.', openUrl: result.url };
    },
    onSuccess: ({ message, openUrl }) => {
      toast.success(message);
      if (openUrl) window.open(openUrl, '_blank', 'noopener,noreferrer');
      onOpenChange(false);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const goNext = () => {
    if (!data?.hasMore || !data.nextCursor) return;
    setCursorHistory((history) => [...history, cursor]);
    setCursor(data.nextCursor);
  };

  const goPrevious = () => {
    if (cursorHistory.length === 0) return;
    const previous = cursorHistory[cursorHistory.length - 1] ?? null;
    setCursorHistory((history) => history.slice(0, -1));
    setCursor(previous);
  };

  const canSubmit =
    Boolean(targetId) &&
    !isFetching &&
    !submit.isPending &&
    (provider === 'GITHUB' ? path.trim().length > 0 : provider === 'GOOGLE_DRIVE' ? filename.trim().length > 0 : provider === 'NOTION' ? title.trim().length > 0 : true);

  const submitLabel = submit.isPending
    ? '보내는 중…'
    : provider === 'GITHUB'
    ? mode === 'PR'
      ? 'PR 만들기'
      : '새 파일로 저장'
    : provider === 'GOOGLE_DRIVE'
    ? '새 파일로 저장'
    : provider === 'SLACK'
    ? '채널로 보내기'
    : '페이지 만들기';

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogTitle>다른 서비스로 보내기</DialogTitle>
        <DialogDescription className="mb-4 text-sm text-ink-500">
          문서를 선택한 서비스에 새 내용으로 만듭니다. 목록도 필요한 페이지씩만 불러옵니다.
        </DialogDescription>

        <div className="mb-4 grid grid-cols-4 gap-2">
          {PROVIDERS.map((p) => (
            <button
              key={p.type}
              type="button"
              onClick={() => setProvider(p.type)}
              className={
                'rounded-md border px-2 py-2 text-center text-sm font-medium ' +
                (provider === p.type ? 'border-accent-500 bg-accent-50 text-ink-800' : 'border-ink-200 text-ink-500')
              }
            >
              {p.label}
            </button>
          ))}
        </div>

        {isLoading ? (
          <LoadingBlock />
        ) : !data?.connected ? (
          <EmptyState title={`${info.label} 계정이 연결되지 않았습니다.`} description="연결 서비스에서 먼저 연결해 주세요." />
        ) : data.targets.length === 0 ? (
          <EmptyState title={`쓸 수 있는 ${info.label} ${info.targetNoun}가 없습니다.`} />
        ) : (
          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-ink-700">대상 {info.targetNoun}</span>
              <select
                value={targetId}
                onChange={(e) => setTargetId(e.target.value)}
                disabled={isFetching}
                className="h-10 rounded-md border border-ink-200 bg-white px-3 text-sm text-ink-800"
              >
                {data.targets.map((target) => (
                  <option key={target.id} value={target.id}>{target.name}</option>
                ))}
              </select>
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-ink-400">{selected?.description || `${page}페이지`}</span>
                <div className="flex items-center gap-1">
                  <Button variant="outline" size="sm" disabled={cursorHistory.length === 0 || isFetching} onClick={goPrevious}>이전</Button>
                  <span className="px-1 text-xs text-ink-400">{page}</span>
                  <Button variant="outline" size="sm" disabled={!data.hasMore || isFetching} onClick={goNext}>다음</Button>
                </div>
              </div>
            </label>

            {provider === 'GITHUB' && (
              <>
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
              </>
            )}

            {provider === 'GOOGLE_DRIVE' && (
              <label className="flex flex-col gap-1 text-sm">
                <span className="font-medium text-ink-700">파일 이름</span>
                <Input value={filename} onChange={(e) => setFilename(e.target.value)} placeholder="회의록.txt" />
              </label>
            )}

            {provider === 'SLACK' && (
              <label className="flex flex-col gap-1 text-sm">
                <span className="font-medium text-ink-700">함께 보낼 메시지 (선택)</span>
                <Input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Hub에서 보낸 문서입니다." />
              </label>
            )}

            {provider === 'NOTION' && (
              <label className="flex flex-col gap-1 text-sm">
                <span className="font-medium text-ink-700">새 페이지 제목</span>
                <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="회의록" />
              </label>
            )}

            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => onOpenChange(false)}>취소</Button>
              <Button disabled={!canSubmit} onClick={() => submit.mutate()}>{submitLabel}</Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
