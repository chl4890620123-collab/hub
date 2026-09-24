import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { LoadingBlock, EmptyState } from '@/components/ui/spinner';
import { documentsApi } from '@/api/endpoints/documents';
import { changesApi } from '@/api/endpoints/decisionsChanges';
import { formatDateTime } from '@/lib/format';

const CHANGE_CATEGORY_LABELS: Record<string, string> = {
  SCHEDULE: '일정 변경',
  BUDGET: '예산 변경',
  ASSIGNEE: '담당자 변경',
  FEATURE: '기능 변경',
  CONTRACT: '계약 변경',
  CONTENT: '내용 변경',
};

function changeReason(reason: string): string {
  return reason === 'Detected in the text diff' ? '문서의 변경된 부분에서 확인했습니다.' : reason;
}

export function VersionCompareDialog({
  documentId,
  projectId,
  open,
  onOpenChange,
}: {
  documentId: number | null;
  projectId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [beforeId, setBeforeId] = useState('');
  const [afterId, setAfterId] = useState('');

  const { data: versions, isLoading } = useQuery({
    queryKey: ['document-versions', documentId],
    queryFn: () => documentsApi.versions(documentId as number),
    enabled: open && documentId != null,
  });

  const compare = useMutation({
    mutationFn: () => changesApi.compare(projectId, Number(beforeId), Number(afterId)),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] max-w-2xl overflow-y-auto">
        <DialogTitle>버전 비교</DialogTitle>
        {isLoading ? (
          <LoadingBlock />
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-2">
              <Select value={beforeId} onValueChange={setBeforeId}>
                <SelectTrigger className="w-48">
                  <SelectValue placeholder="이전 버전" />
                </SelectTrigger>
                <SelectContent>
                  {versions?.map((v) => (
                    <SelectItem key={v.id} value={String(v.id)}>
                      버전 {v.version_no} · {formatDateTime(v.created_at)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <span className="text-ink-400">→</span>
              <Select value={afterId} onValueChange={setAfterId}>
                <SelectTrigger className="w-48">
                  <SelectValue placeholder="이후 버전" />
                </SelectTrigger>
                <SelectContent>
                  {versions?.map((v) => (
                    <SelectItem key={v.id} value={String(v.id)}>
                      버전 {v.version_no} · {formatDateTime(v.created_at)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button size="sm" disabled={!beforeId || !afterId || compare.isPending} onClick={() => compare.mutate()}>
                비교
              </Button>
            </div>

            {compare.isPending && <LoadingBlock label="비교 중..." />}
            {compare.data && (
              <div className="flex flex-col gap-2">
                {compare.data.changes.length === 0 ? (
                  <EmptyState title="변경 사항이 감지되지 않았습니다." />
                ) : (
                  compare.data.changes.map((c, i) => (
                    <div key={i} className="rounded-md border border-ink-200 p-3">
                      <p className="mb-1 text-xs font-semibold text-ink-500">{CHANGE_CATEGORY_LABELS[c.category] ?? '변경 사항'}</p>
                      <div className="mb-2 rounded bg-red-50 px-2 py-1.5 dark:bg-red-950/20">
                        <p className="mb-0.5 text-[11px] font-medium text-red-600">변경 전</p>
                        <p className="text-sm text-red-700 line-through">{c.before || '내용 없음'}</p>
                      </div>
                      <div className="mb-2 rounded bg-accent-50 px-2 py-1.5 dark:bg-accent-950/20">
                        <p className="mb-0.5 text-[11px] font-medium text-accent-600">변경 후</p>
                        <p className="text-sm text-accent-700">{c.after || '내용 없음'}</p>
                      </div>
                      <p className="text-xs text-ink-400">변경 이유: {changeReason(c.reason)}</p>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
