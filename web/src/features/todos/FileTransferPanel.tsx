import { DragEvent, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, Send, UploadCloud, X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { attachmentsApi } from '@/api/endpoints/attachments';
import { useCurrentUser } from '@/hooks/useAuth';
import { formatBytes, formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

type SelectedFile = { id: string; file: File; checked: boolean };

export function FileTransferPanel({ projectId }: { projectId: number }) {
  const [recipientId, setRecipientId] = useState<string>('');
  const [selectedFiles, setSelectedFiles] = useState<SelectedFile[]>([]);
  const [dragging, setDragging] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();

  const { data: recipients } = useQuery({
    queryKey: ['file-transfer-recipients', projectId],
    queryFn: () => attachmentsApi.recipients(projectId),
  });
  const { data: inbox, isLoading } = useQuery({ queryKey: ['file-transfers', projectId], queryFn: () => attachmentsApi.inbox(projectId) });

  const personName = (id: number | null) => {
    if (id == null) return '알 수 없는 사용자';
    if (id === user?.id) return '나';
    return recipients?.find((person) => person.id === id)?.displayName ?? `사용자 #${id}`;
  };

  const addFiles = (files: FileList | File[]) => {
    const next = Array.from(files).map((file) => ({
      id: `${file.name}-${file.size}-${file.lastModified}`,
      file,
      checked: true,
    }));
    setSelectedFiles((current) => {
      const known = new Set(current.map((item) => item.id));
      return [...current, ...next.filter((item) => !known.has(item.id))];
    });
  };

  const send = useMutation({
    mutationFn: async (items: SelectedFile[]) => {
      let sent = 0;
      for (const item of items) {
        await attachmentsApi.send(projectId, Number(recipientId), item.file);
        sent += 1;
        setSelectedFiles((rows) => rows.filter((row) => row.id !== item.id));
      }
      return sent;
    },
    onSuccess: (count) => {
      toast.success(`${count}개 파일을 전송했습니다.`);
      if (fileRef.current) fileRef.current.value = '';
    },
    onError: (error) => {
      toast.error(`전송하지 못한 파일은 선택 목록에 남겨뒀습니다. ${errorMessage(error)}`);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['file-transfers', projectId] }),
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

  const checkedItems = selectedFiles.filter((item) => item.checked);
  const checkedFiles = checkedItems.map((item) => item.file);
  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    if (event.dataTransfer.files.length) addFiles(event.dataTransfer.files);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>업무 파일 보내기</CardTitle>
        <p className="text-xs text-ink-400">받는 사람과 보낼 파일만 선택하세요. 파일 전송과 업무 상태 관리는 할 일·일정 화면에서 함께 확인할 수 있습니다.</p>
      </CardHeader>
      <CardContent>
        <div className="mb-3 max-w-xs">
          <Select value={recipientId} onValueChange={setRecipientId}>
            <SelectTrigger><SelectValue placeholder="받는 사람 선택" /></SelectTrigger>
            <SelectContent>
              {recipients && recipients.length === 0 ? (
                <SelectItem value="__no-recipients" disabled>전송 가능한 팀원이나 관리자가 없습니다</SelectItem>
              ) : recipients?.map((person) => (
                <SelectItem key={person.id} value={String(person.id)}>
                  {person.displayName}{person.id === user?.id ? ' (나)' : person.admin ? ' · 관리자' : ''}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div
          role="button"
          tabIndex={0}
          onClick={() => fileRef.current?.click()}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') fileRef.current?.click(); }}
          onDragEnter={(e) => { e.preventDefault(); setDragging(true); }}
          onDragOver={(e) => e.preventDefault()}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={`mb-3 flex cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed px-4 py-7 text-center transition-colors ${dragging ? 'border-accent-500 bg-accent-50' : 'border-ink-200 hover:bg-ink-50'}`}
        >
          <UploadCloud size={24} className="mb-2 text-accent-600" />
          <p className="text-sm font-medium text-ink-700">파일을 여기로 끌어놓으세요</p>
          <p className="mt-1 text-xs text-ink-400">또는 눌러서 여러 파일을 선택할 수 있습니다.</p>
          <input ref={fileRef} type="file" multiple className="hidden" onChange={(e) => e.target.files && addFiles(e.target.files)} />
        </div>

        {selectedFiles.length > 0 && (
          <div className="mb-3 overflow-hidden rounded-md border border-ink-100">
            {selectedFiles.map((item) => (
              <label key={item.id} className="flex items-center gap-3 border-b border-ink-100 px-3 py-2 last:border-b-0">
                <input
                  type="checkbox"
                  checked={item.checked}
                  onChange={(e) => setSelectedFiles((rows) => rows.map((row) => row.id === item.id ? { ...row, checked: e.target.checked } : row))}
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-ink-800">{item.file.name}</p>
                  <p className="text-xs text-ink-400">{formatBytes(item.file.size)}</p>
                </div>
                <button
                  type="button"
                  aria-label={`${item.file.name} 제거`}
                  onClick={(e) => { e.preventDefault(); setSelectedFiles((rows) => rows.filter((row) => row.id !== item.id)); }}
                  className="text-ink-400 hover:text-ink-700"
                ><X size={14} /></button>
              </label>
            ))}
          </div>
        )}

        <div className="mb-5 flex items-center justify-between gap-3">
          <span className="text-xs text-ink-400">선택 {checkedFiles.length}개 / 추가 {selectedFiles.length}개</span>
          <Button
            size="sm"
            disabled={!recipientId || checkedFiles.length === 0 || send.isPending}
            onClick={() => send.mutate(checkedItems)}
          >
            <Send size={13} /> {send.isPending ? '전송 중...' : `선택한 ${checkedFiles.length}개 전송`}
          </Button>
        </div>

        <div className="mb-2 text-xs font-medium text-ink-500">최근 주고받은 파일</div>
        {isLoading ? <LoadingBlock /> : !inbox || inbox.length === 0 ? (
          <EmptyState title="아직 주고받은 파일이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-1.5">
            {inbox.map((item) => (
              <li key={item.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate text-ink-800">{item.fileName}</p>
                  <p className="text-xs text-ink-400">
                    {item.senderId === user?.id ? `${personName(item.recipientId)}에게 보냄` : `${personName(item.senderId)}이(가) 보냄`}
                    {' · '}{formatBytes(item.sizeBytes)} · {formatDateTime(item.createdAt)}
                    {item.senderId !== user?.id && !item.read ? ' · 안 읽음' : ''}
                  </p>
                </div>
                <button onClick={() => download.mutate(item.id)} className="shrink-0 text-accent-600 hover:underline" aria-label={`${item.fileName} 다운로드`}>
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
