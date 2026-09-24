import { useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Archive, Download, GitCompare, RotateCcw, Sparkles, Trash2, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { ViewModeToggle, type ViewMode } from '@/components/layout/ViewModeToggle';
import { CalendarGrid } from '@/components/layout/CalendarGrid';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label, Textarea } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { AnalysisResultPanel } from '@/features/jobs/AnalysisResultPanel';
import { AssigneeField } from '@/components/form/AssigneeField';
import { VersionCompareDialog } from '@/features/documents/VersionCompareDialog';
import { ReviseFromMeetingDialog } from '@/features/documents/ReviseFromMeetingDialog';
import { JobHistoryPanel } from '@/features/documents/JobHistoryPanel';
import { usePagination, PaginationControls } from '@/components/layout/Pagination';
import type { DocumentRow } from '@/api/types';
import { useCurrentProject } from '@/hooks/useProjects';
import { useIsAdmin } from '@/hooks/useAuth';
import { documentsApi } from '@/api/endpoints/documents';
import { formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const DOCUMENT_SOURCE_LABELS: Record<string, string> = {
  FILE: '업로드 파일',
  MANUAL_TEXT: '직접 입력',
  MEETING_TRANSCRIPT: '회의 녹음 기록',
  LOCAL_PC: '내 PC 파일',
  GITHUB: 'GitHub',
  GOOGLE_DRIVE: 'Google Drive',
  SLACK: 'Slack',
  NOTION: 'Notion',
};

function UploadPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [dueDate, setDueDate] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const queryClient = useQueryClient();

  const upload = useMutation({
    mutationFn: (file: File) =>
      documentsApi.upload(projectId, file, {
        dueDate: dueDate || undefined,
        assigneeId: assigneeId ? Number(assigneeId) : undefined,
      }),
    onSuccess: (result) => {
      toast.success('업로드했습니다. AI 분석이 진행됩니다.');
      onJobStarted(result.jobId);
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
      setSelectedFile(null);
      setDueDate('');
      setAssigneeId('');
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
        <div className="flex min-w-64 flex-col gap-1">
          <Label htmlFor="document-file">파일 선택</Label>
          <input
            id="document-file"
            ref={fileRef}
            type="file"
            className="sr-only"
            onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
          />
          <Button type="button" variant="outline" onClick={() => fileRef.current?.click()} className="justify-start">
            파일 선택
          </Button>
          <span className="max-w-64 truncate text-xs text-ink-400">
            {selectedFile ? selectedFile.name : '선택된 파일 없음'}
          </span>
        </div>
        <div>
          <Label htmlFor="doc-due-date">후속 할 일 기한 (선택)</Label>
          <Input id="doc-due-date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} onClick={(e) => e.currentTarget.showPicker?.()} className="w-48 cursor-pointer [color-scheme:dark]" />
        </div>
        <AssigneeField projectId={projectId} value={assigneeId} onChange={setAssigneeId} />
        <Button
          disabled={!selectedFile || upload.isPending}
          onClick={() => {
            if (!selectedFile) {
              toast.error('업로드할 파일을 먼저 선택해주세요.');
              return;
            }
            upload.mutate(selectedFile);
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
  const [dueDate, setDueDate] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const queryClient = useQueryClient();

  const submit = useMutation({
    mutationFn: () =>
      documentsApi.manual(projectId, {
        title,
        text,
        dueDate: dueDate || undefined,
        assigneeId: assigneeId ? Number(assigneeId) : undefined,
      }),
    onSuccess: (result) => {
      toast.success('저장했습니다. AI 분석이 진행됩니다.');
      onJobStarted(result.jobId);
      setTitle('');
      setText('');
      setDueDate('');
      setAssigneeId('');
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
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <Label htmlFor="manual-due-date">후속 할 일 기한 (선택)</Label>
            <Input id="manual-due-date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} onClick={(e) => e.currentTarget.showPicker?.()} className="w-48 cursor-pointer [color-scheme:dark]" />
          </div>
          <AssigneeField projectId={projectId} value={assigneeId} onChange={setAssigneeId} />
        </div>
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
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [nameFilter, setNameFilter] = useState('');
  const [archiveFilter, setArchiveFilter] = useState<'ACTIVE' | 'ARCHIVED' | 'ALL'>('ACTIVE');
  const [cursor, setCursor] = useState(() => new Date());

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

  const restore = useMutation({
    mutationFn: (documentId: number) => documentsApi.restore(documentId),
    onSuccess: () => {
      toast.success('문서를 다시 사용 중으로 복원했습니다.');
      queryClient.invalidateQueries({ queryKey: ['documents', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const permanentDelete = useMutation({
    mutationFn: (documentId: number) => documentsApi.deletePermanently(documentId),
    onSuccess: () => {
      toast.success('문서를 영구 삭제했습니다.');
      queryClient.invalidateQueries({ queryKey: ['documents', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const download = useMutation({
    mutationFn: async (doc: DocumentRow) => {
      const { blob, filename } = await documentsApi.download(doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename ?? doc.original_name;
      a.click();
      URL.revokeObjectURL(url);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const filtered = useMemo(
    () =>
      (documents ?? []).filter((doc) => {
        if (!doc.original_name.toLowerCase().includes(nameFilter.trim().toLowerCase())) return false;
        if (archiveFilter === 'ACTIVE') return !doc.archived;
        if (archiveFilter === 'ARCHIVED') return doc.archived;
        return true;
      }),
    [documents, nameFilter, archiveFilter],
  );
  const pages = usePagination(filtered);

  if (!currentProject) return <NoProjectState />;

  const year = cursor.getFullYear();
  const month = cursor.getMonth() + 1;

  const documentItem = (doc: DocumentRow, compact?: boolean) => (
    <div
      className={
        compact
          ? 'truncate rounded border border-ink-100 bg-white px-1.5 py-1 text-[11px] text-ink-700 dark:bg-ink-100'
          : 'flex items-center justify-between gap-3 rounded-md border border-ink-100 px-3 py-2'
      }
      title={compact ? doc.original_name : undefined}
    >
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-ink-800">{doc.original_name}</p>
        {!compact && (
          <p className="text-xs text-ink-400">
            {DOCUMENT_SOURCE_LABELS[doc.source_type] ?? '등록 자료'} · 버전 {doc.latest_version} · {formatDateTime(doc.created_at)}
          </p>
        )}
      </div>
      {!compact && (
        <div className="flex shrink-0 items-center gap-2">
          {doc.archived && <Badge variant="outline">보관됨</Badge>}
          {doc.has_original && (
            <Button variant="ghost" size="sm" disabled={download.isPending} onClick={() => download.mutate(doc)}>
              <Download size={13} /> 원본 다운로드
            </Button>
          )}
          {doc.source_type === 'MANUAL_TEXT' && !doc.archived && (
            <Button variant="ghost" size="sm" onClick={() => setRevising(doc)}>
              <Sparkles size={13} /> 회의 내용으로 수정
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={() => setCompareDocId(doc.id)}>
            <GitCompare size={13} /> 버전 비교
          </Button>
          {isAdmin && !doc.archived && (
            <Button variant="ghost" size="sm" disabled={archive.isPending} onClick={() => archive.mutate(doc.id)}>
              <Archive size={13} /> 보관
            </Button>
          )}
          {isAdmin && doc.archived && (
            <Button variant="ghost" size="sm" disabled={restore.isPending} onClick={() => restore.mutate(doc.id)}>
              <RotateCcw size={13} /> 복원
            </Button>
          )}
          {isAdmin && doc.archived && (
            <Button
              variant="ghost"
              size="sm"
              disabled={permanentDelete.isPending}
              className="text-red-600 hover:text-red-700"
              onClick={() => {
                const ok = window.confirm(
                  `'${doc.original_name}' 문서를 영구 삭제할까요?\n\n원본 파일과 모든 버전, 이 문서에 연결된 근거가 삭제됩니다. 이미 확정된 할 일·결정은 남지만 이 문서와의 연결은 제거됩니다. 이 작업은 되돌릴 수 없습니다.`,
                );
                if (ok) permanentDelete.mutate(doc.id);
              }}
            >
              <Trash2 size={13} /> 영구 삭제
            </Button>
          )}
        </div>
      )}
    </div>
  );

  return (
    <div>
      <PageHeader title="문서 요약" description="문서를 업로드하거나 직접 입력하면 AI가 내용을 요약하고 할 일·담당자·기한 후보를 정리합니다." />

      <div className="mb-5 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <UploadPanel projectId={currentProject.id} onJobStarted={setActiveJobId} />
        <ManualEntryPanel projectId={currentProject.id} onJobStarted={setActiveJobId} />
      </div>

      {activeJobId && (
        <div className="mb-5">
          <AnalysisResultPanel jobId={activeJobId} projectId={currentProject.id} />
        </div>
      )}

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <CardTitle>문서 목록</CardTitle>
            <div className="flex flex-wrap items-center gap-2">
              <div className="flex gap-1">
                {([
                  ['ACTIVE', '사용 중'],
                  ['ARCHIVED', '보관함'],
                  ['ALL', '전체'],
                ] as const).map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setArchiveFilter(value)}
                    className={
                      'rounded-full px-2.5 py-1 text-xs font-medium ' +
                      (archiveFilter === value ? 'bg-accent-600 text-white' : 'bg-ink-100 text-ink-500 hover:bg-ink-200')
                    }
                  >
                    {label}
                  </button>
                ))}
              </div>
              <Input
                value={nameFilter}
                onChange={(e) => setNameFilter(e.target.value)}
                placeholder="이름으로 찾기"
                className="w-40"
              />
              <ViewModeToggle value={viewMode} onChange={setViewMode} />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <LoadingBlock />
          ) : viewMode === 'calendar' ? (
            <div>
              <div className="mb-3 flex items-center justify-center gap-2">
                <Button variant="outline" size="sm" onClick={() => setCursor(new Date(year, month - 2, 1))}>
                  이전 달
                </Button>
                <span className="text-sm font-medium text-ink-700">
                  {year}년 {month}월
                </span>
                <Button variant="outline" size="sm" onClick={() => setCursor(new Date(year, month, 1))}>
                  다음 달
                </Button>
              </div>
              <CalendarGrid
                items={filtered}
                getDate={(doc) => doc.created_at.slice(0, 10)}
                renderItem={(doc) => documentItem(doc, true)}
                year={year}
                month={month}
              />
            </div>
          ) : filtered.length === 0 ? (
            <EmptyState title="등록된 문서가 없습니다." />
          ) : (
            <>
              <ul className={viewMode === 'grid' ? 'grid grid-cols-1 gap-3 sm:grid-cols-2' : 'flex flex-col gap-2'}>
                {pages.pageItems.map((doc) => (
                  <li key={doc.id}>{documentItem(doc)}</li>
                ))}
              </ul>
              <PaginationControls
                page={pages.page}
                totalPages={pages.totalPages}
                pageSize={pages.pageSize}
                onPageChange={pages.setPage}
                onPageSizeChange={pages.setPageSize}
                totalCount={filtered.length}
              />
            </>
          )}
        </CardContent>
      </Card>

      <div className="mt-4">
        <JobHistoryPanel projectId={currentProject.id} />
      </div>

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
