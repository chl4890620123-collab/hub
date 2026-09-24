import { useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { LoadingBlock } from '@/components/ui/spinner';
import { attachmentsApi } from '@/api/endpoints/attachments';
import { formatBytes } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function TodoAttachmentsPanel({ todoId }: { todoId: number }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const { data: attachments, isLoading } = useQuery({
    queryKey: ['todo-attachments', todoId],
    queryFn: () => attachmentsApi.listForTodo(todoId),
  });

  const upload = useMutation({
    mutationFn: (file: File) => attachmentsApi.attachToTodo(todoId, file),
    onSuccess: () => {
      toast.success('파일을 첨부했습니다.');
      queryClient.invalidateQueries({ queryKey: ['todo-attachments', todoId] });
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
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="mt-2 flex flex-col gap-2 rounded-md border border-dashed border-ink-200 p-2">
      {isLoading ? (
        <LoadingBlock />
      ) : !attachments || attachments.length === 0 ? (
        <p className="text-xs text-ink-400">아직 첨부된 파일이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-1">
          {attachments.map((a) => (
            <li key={a.id} className="flex items-center justify-between gap-2 text-xs">
              <span className="truncate text-ink-700">
                {a.fileName} ({formatBytes(a.sizeBytes)})
              </span>
              <button
                onClick={() => download.mutate(a.id)}
                className="shrink-0 text-accent-600 hover:underline"
                aria-label={`${a.fileName} 다운로드`}
                title="다운로드"
              >
                <Download size={12} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <form
        className="flex items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          const file = fileRef.current?.files?.[0];
          if (file) upload.mutate(file);
        }}
      >
        <input ref={fileRef} type="file" className="min-w-0 flex-1 text-xs" />
        <Button type="submit" size="sm" variant="ghost" disabled={upload.isPending}>
          추가
        </Button>
      </form>
    </div>
  );
}
