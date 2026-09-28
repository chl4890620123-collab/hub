import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input, Label, Textarea } from '@/components/ui/input';
import { LoadingBlock } from '@/components/ui/spinner';
import { documentsApi } from '@/api/endpoints/documents';
import type { DocumentRow } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

/** Saves a new version with edited title/text and re-queues AI analysis; the original file bytes (if any) are kept untouched. */
export function DocumentEditDialog({
  projectId,
  document,
  open,
  onOpenChange,
  onSaved,
}: {
  projectId: number;
  document: DocumentRow | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');

  const { data: latestVersion, isLoading } = useQuery({
    queryKey: ['document-edit-source', document?.id],
    queryFn: async () => {
      const versions = await documentsApi.versions(document!.id);
      const latest = [...versions].sort((a, b) => b.version_no - a.version_no)[0];
      if (!latest) throw new Error('편집할 문서 버전을 찾을 수 없습니다.');
      return documentsApi.version(latest.id);
    },
    enabled: open && !!document,
  });

  useEffect(() => {
    if (open && document) {
      setTitle(document.original_name);
    } else {
      setTitle('');
      setText('');
    }
  }, [open, document]);

  useEffect(() => {
    if (latestVersion) setText(String(latestVersion.full_text ?? ''));
  }, [latestVersion]);

  const save = useMutation({
    mutationFn: () => documentsApi.edit(projectId, document!.id, title.trim(), text),
    onSuccess: () => {
      toast.success('새 버전으로 저장했습니다. AI 요약을 다시 시작합니다.');
      onOpenChange(false);
      onSaved();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
        <DialogTitle>문서 수정</DialogTitle>
        {!document ? null : isLoading ? (
          <LoadingBlock label="원본 내용을 불러오는 중..." />
        ) : (
          <div className="flex flex-col gap-3">
            <p className="text-xs text-ink-500">
              내용을 고쳐 저장하면 새 버전으로 기록되고, AI 요약이 자동으로 다시 시작됩니다. 기존 버전은 버전 비교에서 그대로 확인할 수 있습니다.
            </p>
            <div>
              <Label>제목</Label>
              <Input value={title} onChange={(e) => setTitle(e.target.value)} />
            </div>
            <div>
              <Label>내용</Label>
              <Textarea rows={16} value={text} onChange={(e) => setText(e.target.value)} />
            </div>
            <Button
              className="self-start"
              disabled={!title.trim() || !text.trim() || save.isPending}
              onClick={() => save.mutate()}
            >
              {save.isPending ? '저장 중...' : '새 버전으로 저장'}
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
