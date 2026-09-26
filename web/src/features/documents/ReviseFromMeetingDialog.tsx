import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Label, Textarea } from '@/components/ui/input';
import { LoadingBlock } from '@/components/ui/spinner';
import { documentsApi } from '@/api/endpoints/documents';
import type { DocumentRow } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

/** AI proposes a revision of a user-managed document grounded in a meeting transcript. Nothing is saved until the user reviews the draft and explicitly stores a new version. */
export function ReviseFromMeetingDialog({
  projectId,
  document,
  meetingDocuments,
  open,
  onOpenChange,
  onSaved,
}: {
  projectId: number;
  document: DocumentRow | null;
  meetingDocuments: DocumentRow[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const [meetingId, setMeetingId] = useState('');
  const [draft, setDraft] = useState('');

  useEffect(() => {
    if (open) {
      setMeetingId('');
      setDraft('');
    }
  }, [open]);

  const generate = useMutation({
    mutationFn: () => documentsApi.reviseDraft(projectId, document!.id, Number(meetingId)),
    onSuccess: (result) => setDraft(result.revisedText),
    onError: (error) => toast.error(errorMessage(error)),
  });

  const save = useMutation({
    mutationFn: () => documentsApi.edit(projectId, document!.id, document!.original_name, draft),
    onSuccess: () => {
      toast.success('회의 내용을 반영해 새 버전으로 저장했습니다. 버전 비교에서 달라진 점을 확인하세요.');
      onOpenChange(false);
      onSaved();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
        <DialogTitle>회의 내용으로 수정</DialogTitle>
        {!document ? null : (
          <div className="flex flex-col gap-3">
            <p className="text-xs text-ink-500">
              "{document.original_name}" 문서를 선택한 회의 내용에 실제로 언급된 변경 사항만 반영해 AI가 초안을 만듭니다.
              저장 전까지는 원본이 바뀌지 않습니다.
            </p>
            <div>
              <Label>참고할 회의 기록</Label>
              {meetingDocuments.length === 0 ? (
                <p className="text-xs text-ink-400">아직 등록된 회의 녹음 기록이 없습니다.</p>
              ) : (
                <Select value={meetingId} onValueChange={setMeetingId}>
                  <SelectTrigger>
                    <SelectValue placeholder="회의 선택" />
                  </SelectTrigger>
                  <SelectContent>
                    {meetingDocuments.map((m) => (
                      <SelectItem key={m.id} value={String(m.id)}>
                        {m.original_name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
            <Button
              variant="outline"
              className="self-start"
              disabled={!meetingId || generate.isPending}
              onClick={() => generate.mutate()}
            >
              AI로 초안 만들기
            </Button>

            {generate.isPending ? (
              <LoadingBlock label="회의 내용을 반영하는 중..." />
            ) : (
              draft && (
                <div>
                  <Label>초안 (자유롭게 고친 뒤 저장하세요)</Label>
                  <Textarea rows={12} value={draft} onChange={(e) => setDraft(e.target.value)} />
                  <Button className="mt-3" disabled={!draft.trim() || save.isPending} onClick={() => save.mutate()}>
                    새 버전으로 저장
                  </Button>
                </div>
              )
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
