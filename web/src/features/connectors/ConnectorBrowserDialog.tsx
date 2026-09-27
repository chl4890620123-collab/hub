import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { EmptyState, LoadingBlock, Spinner } from '@/components/ui/spinner';
import { connectorsApi } from '@/api/endpoints/connectors';
import type { ConnectorType } from '@/api/types';
import { useJobPolling } from '@/hooks/useJobPolling';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const PAGE_SIZES = [10, 20, 50, 100];

export function ConnectorBrowserDialog({
  projectId,
  type,
  open,
  onOpenChange,
  providerLabel,
  targetNoun,
}: {
  projectId: number;
  type: ConnectorType | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  providerLabel: string;
  targetNoun: string;
}) {
  const queryClient = useQueryClient();
  const [importingTargetId, setImportingTargetId] = useState<string | null>(null);
  const [jobId, setJobId] = useState<number | null>(null);
  const [cursor, setCursor] = useState<string | null>(null);
  const [cursorHistory, setCursorHistory] = useState<(string | null)[]>([]);
  const [pageSize, setPageSize] = useState(20);
  const { data: job } = useJobPolling(jobId);

  useEffect(() => {
    if (!open) return;
    setCursor(null);
    setCursorHistory([]);
    setPageSize(20);
  }, [open, type]);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['connector-targets', projectId, type, cursor ?? '', pageSize],
    queryFn: () => connectorsApi.targets(projectId, type as ConnectorType, cursor, pageSize),
    enabled: open && !!type,
    placeholderData: (previous) => previous,
  });

  const importMutation = useMutation({
    mutationFn: (scope: string) => connectorsApi.import(projectId, type as ConnectorType, scope),
    onSuccess: (result) => {
      setJobId(result.jobId);
      queryClient.invalidateQueries({ queryKey: ['jobs-recent', projectId] });
      toast.success('가져오기를 시작했습니다. 이 창을 닫거나 다른 화면으로 이동해도 백그라운드에서 계속 진행됩니다.');
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setImportingTargetId(null);
    },
  });

  useEffect(() => {
    if (!job || job.status === 'PENDING' || job.status === 'PROCESSING' || job.status === 'RUNNING') return;
    queryClient.invalidateQueries({ queryKey: ['jobs-recent', projectId] });
    queryClient.invalidateQueries({ queryKey: ['connector-status', projectId] });
    if (job.status === 'SUCCESS') {
      const imported = job.resultJson ? (JSON.parse(job.resultJson).imported ?? 0) : 0;
      toast.success(`${providerLabel}에서 ${imported}건을 가져왔습니다.`);
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
    } else if (job.status === 'FAILED') {
      toast.error(job.errorMessage || '자료를 가져오지 못했습니다.');
    }
    setJobId(null);
    setImportingTargetId(null);
  }, [job, projectId, providerLabel, queryClient]);

  const importing = importMutation.isPending || (jobId != null && job?.status !== 'SUCCESS' && job?.status !== 'FAILED');
  const page = cursorHistory.length + 1;

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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[75vh] max-w-lg overflow-y-auto">
        <DialogTitle>{providerLabel} {targetNoun} 가져오기</DialogTitle>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2 text-xs text-ink-500">
          <span>목록은 필요한 페이지씩만 불러옵니다.</span>
          <label className="flex items-center gap-1">
            <span>한 번에</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setCursor(null);
                setCursorHistory([]);
              }}
              className="h-7 rounded border border-ink-200 bg-white px-2 text-xs"
            >
              {PAGE_SIZES.map((size) => <option key={size} value={size}>{size}개</option>)}
            </select>
          </label>
        </div>

        {isLoading ? (
          <LoadingBlock />
        ) : !data?.connected ? (
          <EmptyState
            title={providerLabel + ' 계정이 연결되지 않았습니다.'}
            description={'먼저 ' + providerLabel + ' 연결하기를 누른 뒤 가져올 ' + targetNoun + '를 선택해 주세요.'}
          />
        ) : data.targets.length === 0 ? (
          <EmptyState title={'가져올 수 있는 ' + targetNoun + '가 없습니다.'} />
        ) : (
          <>
            <ul className={'flex flex-col gap-2 ' + (isFetching ? 'opacity-60' : '')}>
              {data.targets.map((target) => (
                <li key={target.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink-800">{target.name}</p>
                    {target.description && <p className="truncate text-xs text-ink-400">{target.description}</p>}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {target.url && (
                      <a
                        href={target.url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-xs text-accent-600 underline-offset-2 hover:underline"
                      >
                        열기
                      </a>
                    )}
                    <Button
                      size="sm"
                      disabled={importing}
                      onClick={() => {
                        setImportingTargetId(target.id);
                        importMutation.mutate(target.id);
                      }}
                    >
                      {importingTargetId === target.id && importing ? (
                        <>
                          <Spinner className="h-3.5 w-3.5" /> 시작 중
                        </>
                      ) : (
                        '가져오기'
                      )}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex items-center justify-between text-xs text-ink-500">
              <Button variant="outline" size="sm" disabled={cursorHistory.length === 0 || isFetching} onClick={goPrevious}>
                이전
              </Button>
              <span>{page}페이지 {isFetching ? '· 불러오는 중…' : ''}</span>
              <Button variant="outline" size="sm" disabled={!data.hasMore || isFetching} onClick={goNext}>
                다음
              </Button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
