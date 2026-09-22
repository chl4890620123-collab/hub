import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { LoadingBlock, EmptyState } from '@/components/ui/spinner';
import { documentsApi } from '@/api/endpoints/documents';
import { changesApi } from '@/api/endpoints/decisionsChanges';
import { formatDateTime } from '@/lib/format';

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
                      v{v.version_no} · {formatDateTime(v.created_at)}
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
                      v{v.version_no} · {formatDateTime(v.created_at)}
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
                      <p className="mb-1 text-xs font-semibold text-ink-500">{c.category}</p>
                      <p className="mb-1 text-sm text-red-600 line-through">{c.before}</p>
                      <p className="mb-1 text-sm text-accent-700">{c.after}</p>
                      <p className="text-xs text-ink-400">{c.reason}</p>
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
