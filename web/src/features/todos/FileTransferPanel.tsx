import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, Send } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { attachmentsApi } from '@/api/endpoints/attachments';
import { projectsApi } from '@/api/endpoints/projects';
import { formatBytes, formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function FileTransferPanel({ projectId }: { projectId: number }) {
  const [recipientId, setRecipientId] = useState<string>('');
  const fileRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const { data: members } = useQuery({ queryKey: ['project-members', projectId], queryFn: () => projectsApi.members(projectId) });
  const { data: inbox, isLoading } = useQuery({ queryKey: ['file-transfers', projectId], queryFn: () => attachmentsApi.inbox(projectId) });

  const send = useMutation({
    mutationFn: (file: File) => attachmentsApi.send(projectId, Number(recipientId), file),
    onSuccess: () => {
      toast.success('파일을 전송했습니다.');
      queryClient.invalidateQueries({ queryKey: ['file-transfers', projectId] });
      if (fileRef.current) fileRef.current.value = '';
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const download = useMutation({
    mutationFn: async (id: number) => {
      const { blob, filename } = await attachmentsApi.download(id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename ?? 'download';
      a.click();
      URL.revokeObjectURL(url);
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>파일 전송</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="mb-4 flex items-center gap-2">
          <Select value={recipientId} onValueChange={setRecipientId}>
            <SelectTrigger className="max-w-[200px]">
              <SelectValue placeholder="받는 사람" />
            </SelectTrigger>
            <SelectContent>
              {members?.map((m) => (
                <SelectItem key={m.id} value={String(m.id)}>
                  {m.displayName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <input ref={fileRef} type="file" className="text-xs" />
          <Button
            size="sm"
            disabled={!recipientId || send.isPending}
            onClick={() => {
              const file = fileRef.current?.files?.[0];
              if (file) send.mutate(file);
            }}
          >
            <Send size={13} /> 전송
          </Button>
        </div>

        {isLoading ? (
          <LoadingBlock />
        ) : !inbox || inbox.length === 0 ? (
          <EmptyState title="받은 파일이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-1.5">
            {inbox.map((item) => (
              <li key={item.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate text-ink-800">{item.fileName}</p>
                  <p className="text-xs text-ink-400">
                    {formatBytes(item.sizeBytes)} · {formatDateTime(item.createdAt)}
                  </p>
                </div>
                <button onClick={() => download.mutate(item.id)} className="shrink-0 text-accent-600 hover:underline">
                  <Download size={14} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
