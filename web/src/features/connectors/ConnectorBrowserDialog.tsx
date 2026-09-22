import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { connectorsApi } from '@/api/endpoints/connectors';
import type { ConnectorType } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function ConnectorBrowserDialog({
  projectId,
  type,
  open,
  onOpenChange,
}: {
  projectId: number;
  type: ConnectorType | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['connector-targets', projectId, type],
    queryFn: () => connectorsApi.targets(projectId, type as ConnectorType),
    enabled: open && !!type,
  });

  const importMutation = useMutation({
    mutationFn: (scope: string) => connectorsApi.import(projectId, type as ConnectorType, scope),
    onSuccess: (result) => {
      toast.success(`${result.imported}건을 가져왔습니다.`);
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[70vh] max-w-lg overflow-y-auto">
        <DialogTitle>가져올 항목 선택</DialogTitle>
        {isLoading ? (
          <LoadingBlock />
        ) : !data?.connected ? (
          <EmptyState title="연결된 계정이 없습니다." description="먼저 연결하기를 눌러 계정을 연동해 주세요." />
        ) : data.targets.length === 0 ? (
          <EmptyState title="가져올 수 있는 항목이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-2">
            {data.targets.map((target) => (
              <li key={target.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ink-800">{target.name}</p>
                  {target.description && <p className="truncate text-xs text-ink-400">{target.description}</p>}
                </div>
                <Button size="sm" disabled={importMutation.isPending} onClick={() => importMutation.mutate(target.id)}>
                  가져오기
                </Button>
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );
}
