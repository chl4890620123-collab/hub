import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Label, Textarea } from '@/components/ui/input';
import { LoadingBlock } from '@/components/ui/spinner';
import { documentsApi } from '@/api/endpoints/documents';
import { useJobPolling } from '@/hooks/useJobPolling';
import type { DocumentRow } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

/** AI proposes a revision of a user-managed document grounded in a meeting transcript. The proposal
 * runs as a requester-scoped server job, so navigation/refresh does not cancel it. Nothing is saved
 * until the user reviews the draft and explicitly stores a new immutable version. */
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
  const [jobId, setJobId] = useState<number | null>(null);
  const jobKey = document ? `hub.document-revision-job.${projectId}.${document.id}` : '';
  const { data: job } = useJobPolling(jobId);

  useEffect(() => {
    if (!open || !document) return;
    setMeetingId('');
    setDraft('');
    try {
      const raw = window.localStorage.getItem(`hub.document-revision-job.${projectId}.${document.id}`);
      setJobId(raw ? Number(raw) || null : null);
    } catch {
      setJobId(null);
    }
  }, [open, document?.id, projectId]);

  useEffect(() => {
    if (job?.status !== 'SUCCESS' || !job.resultJson) return;
    try {
      const result = JSON.parse(job.resultJson) as { revisedText?: string };
      if (result.revisedText) setDraft(result.revisedText);
    } catch {
      // The error state below asks the user to generate a new draft if the stored result is malformed.
    }
  }, [job?.status, job?.resultJson]);

  const generate = useMutation({
    mutationFn: () => documentsApi.reviseDraftJob(projectId, document!.id, Number(meetingId)),
    onSuccess: (result) => {
      setJobId(result.jobId);
      try { window.localStorage.setItem(jobKey, String(result.jobId)); } catch { /* optional persistence */ }
      toast.success('수정 초안 생성을 시작했습니다. 다른 화면으로 이동해도 서버에서 계속 진행됩니다.');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const save = useMutation({
    mutationFn: () => documentsApi.edit(projectId, document!.id, document!.original_name, draft),
    onSuccess: () => {
      toast.success('회의 내용을 반영해 새 버전으로 저장했습니다. 버전 비교에서 달라진 점을 확인하세요.');
      try { if (jobKey) window.localStorage.removeItem(jobKey); } catch { /* optional */ }
      setJobId(null);
      onOpenChange(false);
      onSaved();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const processing = generate.isPending || job?.status === 'PENDING' || job?.status === 'RUNNING';

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
        <DialogTitle>회의 내용으로 수정</DialogTitle>
        {!document ? null : (
          <div className="flex flex-col gap-3">
            <p className="text-xs text-ink-500">
              "{document.original_name}" 문서를 선택한 회의 내용에 실제로 언급된 변경 사항만 반영해 AI가 초안을 만듭니다.
              생성 작업은 서버에서 계속되고, 저장 전까지 원본은 바뀌지 않습니다.
            </p>
            <div>
              <Label>참고할 회의 기록</Label>
              {meetingDocuments.length === 0 ? (
                <p className="text-xs text-ink-400">아직 등록된 회의 녹음 기록이 없습니다.</p>
              ) : (
                <Select value={meetingId} onValueChange={setMeetingId}>
                  <SelectTrigger><SelectValue placeholder="회의 선택" /></SelectTrigger>
                  <SelectContent>
                    {meetingDocuments.map((m) => <SelectItem key={m.id} value={String(m.id)}>{m.original_name}</SelectItem>)}
                  </SelectContent>
                </Select>
              )}
            </div>
            <Button variant="outline" className="self-start" disabled={!meetingId || processing} onClick={() => generate.mutate()}>
              AI로 초안 만들기
            </Button>

            {processing && <LoadingBlock label="서버에서 회의 내용을 반영하고 있습니다. 이 창이나 화면을 닫아도 계속 진행됩니다." />}
            {job?.status === 'FAILED' && <p className="text-sm text-red-600">{job.errorMessage || '수정 초안을 만들지 못했습니다.'}</p>}
            {job?.status === 'SUCCESS' && !draft && <p className="text-sm text-red-600">완료된 초안을 읽지 못했습니다. 다시 생성해 주세요.</p>}
            {draft && !processing && (
              <div>
                <Label>초안 (자유롭게 고친 뒤 저장하세요)</Label>
                <Textarea rows={12} value={draft} onChange={(e) => setDraft(e.target.value)} />
                <Button className="mt-3" disabled={!draft.trim() || save.isPending} onClick={() => save.mutate()}>
                  새 버전으로 저장
                </Button>
              </div>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
