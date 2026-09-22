import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { GitCompare, Sparkles, Trash2, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label, Textarea } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { JobStatusPanel } from '@/components/feedback/JobStatusPanel';
import { VersionCompareDialog } from '@/features/documents/VersionCompareDialog';
import { ReviseFromMeetingDialog } from '@/features/documents/ReviseFromMeetingDialog';
import type { DocumentRow } from '@/api/types';
import { useCurrentProject } from '@/hooks/useProjects';
import { useIsAdmin } from '@/hooks/useAuth';
import { documentsApi } from '@/api/endpoints/documents';
import { formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function UploadPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [dueDate, setDueDate] = useState('');
  const queryClient = useQueryClient();

  const upload = useMutation({
    mutationFn: (file: File) => documentsApi.upload(projectId, file, { dueDate: dueDate || undefined }),
    onSuccess: (result) => {
      toast.success('업로드했습니다. AI 분석이 진행됩니다.');
      onJobStarted(result.jobId);
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
      if (fileRef.current) fileRef.current.value = '';
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>파일 업로드</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-wrap items-end gap-3">
        <input ref={fileRef} type="file" className="text-sm" />
        <div>
          <Label htmlFor="doc-due-date">후속 할 일 기한 (선택)</Label>
          <Input id="doc-due-date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className="w-40" />
        </div>
        <Button
          disabled={upload.isPending}
          onClick={() => {
            const file = fileRef.current?.files?.[0];
            if (file) upload.mutate(file);
          }}
        >
          <Upload size={14} /> 업로드
        </Button>
      </CardContent>
    </Card>
  );
}

function ManualEntryPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');
  const queryClient = useQueryClient();

  const submit = useMutation({
    mutationFn: () => documentsApi.manual(projectId, { title, text }),
    onSuccess: (result) => {
      toast.success('저장했습니다. AI 분석이 진행됩니다.');
      onJobStarted(result.jobId);
      setTitle('');
      setText('');
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>직접 입력</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="문서 제목" />
        <Textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} placeholder="내용을 입력하세요" />
        <Button disabled={!title.trim() || !text.trim() || submit.isPending} onClick={() => submit.mutate()} className="self-start">
          저장 및 분석 요청
        </Button>
      </CardContent>
    </Card>
  );
}

export function DocumentsPage() {
  const { currentProject } = useCurrentProject();
  const isAdmin = useIsAdmin();
  const queryClient = useQueryClient();
  const [activeJobId, setActiveJobId] = useState<number | null>(null);
  const [compareDocId, setCompareDocId] = useState<number | null>(null);
  const [revising, setRevising] = useState<DocumentRow | null>(null);

  const { data: documents, isLoading } = useQuery({
    queryKey: ['documents', currentProject?.id],
    queryFn: () => documentsApi.list(currentProject!.id),
    enabled: !!currentProject,
  });

  const archive = useMutation({
    mutationFn: (documentId: number) => documentsApi.archive(documentId),
    onSuccess: () => {
      toast.success('문서를 보관 처리했습니다.');
      queryClient.invalidateQueries({ queryKey: ['documents', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!currentProject) return <NoProjectState />;

  return (
    <div>
      <PageHeader title="문서 요약" description="문서를 업로드하거나 직접 입력하면 AI가 요약과 할 일을 추출합니다." />

      <div className="mb-5 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <UploadPanel projectId={currentProject.id} onJobStarted={setActiveJobId} />
        <ManualEntryPanel projectId={currentProject.id} onJobStarted={setActiveJobId} />
      </div>

      {activeJobId && (
        <div className="mb-5">
          <JobStatusPanel jobId={activeJobId} />
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>문서 목록</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <LoadingBlock />
          ) : !documents || documents.length === 0 ? (
            <EmptyState title="등록된 문서가 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {documents.map((doc) => (
                <li key={doc.id} className="flex items-center justify-between gap-3 rounded-md border border-ink-100 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink-800">{doc.original_name}</p>
                    <p className="text-xs text-ink-400">
                      {doc.source_type} · v{doc.latest_version} · {formatDateTime(doc.created_at)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {doc.archived && <Badge variant="outline">보관됨</Badge>}
                    {doc.source_type === 'MANUAL_TEXT' && !doc.archived && (
                      <Button variant="ghost" size="sm" onClick={() => setRevising(doc)}>
                        <Sparkles size={13} /> 회의 내용으로 수정
                      </Button>
                    )}
                    <Button variant="ghost" size="sm" onClick={() => setCompareDocId(doc.id)}>
                      <GitCompare size={13} /> 버전 비교
                    </Button>
                    {isAdmin && !doc.archived && (
                      <Button variant="ghost" size="sm" onClick={() => archive.mutate(doc.id)}>
                        <Trash2 size={13} className="text-red-500" />
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <VersionCompareDialog
        documentId={compareDocId}
        projectId={currentProject.id}
        open={compareDocId != null}
        onOpenChange={(open) => !open && setCompareDocId(null)}
      />

      <ReviseFromMeetingDialog
        projectId={currentProject.id}
        document={revising}
        meetingDocuments={(documents ?? []).filter((d) => d.source_type === 'MEETING_TRANSCRIPT')}
        open={revising != null}
        onOpenChange={(open) => !open && setRevising(null)}
        onSaved={() => queryClient.invalidateQueries({ queryKey: ['documents', currentProject.id] })}
      />
    </div>
  );
}
