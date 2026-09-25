import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Mic, Square, Trash2, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button, buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/cn';
import { Input, Label } from '@/components/ui/input';
import { AnalysisResultPanel } from '@/features/jobs/AnalysisResultPanel';
import { useCurrentProject } from '@/hooks/useProjects';
import { useMediaRecorder } from '@/features/meetings/useMediaRecorder';
import { meetingsApi } from '@/api/endpoints/meetings';
import { jobsApi } from '@/api/endpoints/jobs';
import { localDateTimeWithOffset } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function formatElapsed(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  return `${String(Math.floor(totalSeconds / 60)).padStart(2, '0')}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

function RecordingPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const [title, setTitle] = useState('');
  const upload = useMutation({
    mutationFn: (blob: Blob) => meetingsApi.upload(projectId, title || '녹음 회의', blob, localDateTimeWithOffset()),
    onSuccess: (result) => { toast.success('녹음을 업로드했습니다. 음성 변환과 AI 분석이 진행됩니다.'); onJobStarted(result.jobId); },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const { isRecording, elapsedMs, start, stop } = useMediaRecorder((blob) => { toast.success('최대 녹음 시간에 도달해 자동으로 업로드합니다.'); upload.mutate(blob); });
  return (
    <Card><CardHeader><CardTitle>실시간 녹음</CardTitle></CardHeader><CardContent className="flex flex-col gap-3">
      <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="회의 제목" disabled={isRecording} />
      <div className="flex items-center gap-3">{!isRecording ? (
        <Button onClick={async () => { try { await start(); } catch (error) { toast.error(errorMessage(error)); } }}><Mic size={14} /> 녹음 시작</Button>
      ) : (
        <Button variant="danger" onClick={async () => { const blob = await stop(); upload.mutate(blob); }}><Square size={14} /> 녹음 종료 ({formatElapsed(elapsedMs)})</Button>
      )}{upload.isPending && <span className="text-sm text-ink-400">업로드 중...</span>}</div>
      <p className="text-xs text-ink-400">최대 25분까지 녹음되며, 시간이 지나면 자동으로 종료됩니다.</p>
    </CardContent></Card>
  );
}

function AudioUploadPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [title, setTitle] = useState('');
  const upload = useMutation({
    mutationFn: (file: File) => meetingsApi.upload(projectId, title || file.name, file),
    onSuccess: (result) => { toast.success('업로드했습니다. 음성 변환과 AI 분석이 진행됩니다.'); onJobStarted(result.jobId); if (fileRef.current) fileRef.current.value = ''; },
    onError: (error) => toast.error(errorMessage(error)),
  });
  return (
    <Card><CardHeader><CardTitle>오디오 파일 업로드</CardTitle></CardHeader><CardContent className="flex flex-wrap items-end gap-3">
      <div><Label htmlFor="meeting-title">회의 제목</Label><Input id="meeting-title" value={title} onChange={(e) => setTitle(e.target.value)} className="w-56" /></div>
      <div><Label htmlFor="meeting-file">오디오 파일</Label><label htmlFor="meeting-file" className={cn(buttonVariants({ variant: 'outline', size: 'md' }), upload.isPending && 'pointer-events-none opacity-50')}><Upload size={14} /> 선택하면 바로 업로드</label>
        <input id="meeting-file" ref={fileRef} type="file" accept="audio/*" disabled={upload.isPending} className="sr-only" onChange={(e) => { const file = e.target.files?.[0]; if (file) upload.mutate(file); }} /></div>
      {upload.isPending && <span className="flex items-center gap-1 text-sm text-ink-400"><Upload size={13} className="animate-pulse" /> 업로드 중...</span>}
    </CardContent></Card>
  );
}

export function MeetingsPage() {
  const { currentProject } = useCurrentProject();
  const queryClient = useQueryClient();
  const [activeJobId, setActiveJobId] = useState<number | null>(null);
  const projectId = currentProject?.id ?? 0;
  const { data: recentJobs } = useQuery({ queryKey: ['recent-jobs', projectId], queryFn: () => jobsApi.recent(projectId), enabled: projectId > 0 });

  const removeMeeting = useMutation({
    mutationFn: ({ meetingId }: { meetingId: number; jobId: number }) => meetingsApi.deleteCompleted(projectId, meetingId),
    onSuccess: (_result, variables) => {
      if (activeJobId === variables.jobId) setActiveJobId(null);
      toast.success('완료된 회의록을 삭제했습니다.');
      queryClient.invalidateQueries({ queryKey: ['recent-jobs', projectId] });
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  useEffect(() => { setActiveJobId(null); }, [projectId]);
  useEffect(() => {
    if (!currentProject || activeJobId) return;
    const latestMeeting = recentJobs?.find((job) => job.jobType === 'MEETING_STT_ANALYSIS');
    if (latestMeeting) setActiveJobId(latestMeeting.id);
  }, [activeJobId, currentProject, recentJobs]);

  if (!currentProject) return <NoProjectState />;
  const meetingJobs = (recentJobs ?? []).filter((job) => job.jobType === 'MEETING_STT_ANALYSIS').slice(0, 5);

  return (
    <div>
      <PageHeader title="회의 녹음" description="녹음이나 오디오 파일을 올리면 음성을 텍스트로 바꾸고, 할 일·담당자·기한 후보를 정리합니다. 확인 후 확정하면 실제 할 일 목록에 등록됩니다." />
      <div className="mb-5 grid grid-cols-1 gap-4 lg:grid-cols-2"><RecordingPanel projectId={currentProject.id} onJobStarted={setActiveJobId} /><AudioUploadPanel projectId={currentProject.id} onJobStarted={setActiveJobId} /></div>
      {activeJobId && <AnalysisResultPanel jobId={activeJobId} projectId={currentProject.id} context="meeting" />}
      {meetingJobs.length > 0 && (
        <Card className="mt-5"><CardHeader><CardTitle>최근 회의 처리 내역</CardTitle></CardHeader><CardContent className="flex flex-col gap-2">
          <p className="text-xs text-ink-400">완료된 회의는 다시 열어 확인할 수 있고, 만든 사람 또는 관리자는 필요 없는 회의록을 삭제할 수 있습니다.</p>
          {meetingJobs.map((job) => (
            <div key={job.id} className={cn('flex items-center justify-between rounded-md border border-ink-200 px-3 py-2 text-sm hover:bg-ink-50 dark:hover:bg-ink-200', activeJobId === job.id && 'border-accent-400')}>
              <button type="button" onClick={() => setActiveJobId(job.id)} className="flex min-w-0 flex-1 items-center justify-between text-left">
                <span>회의 분석 #{job.id}</span><span className="mr-3 text-xs text-ink-400">{job.status === 'SUCCESS' ? '완료' : job.status === 'FAILED' ? '실패' : '처리 중'} · {job.progress}%</span>
              </button>
              {job.status === 'SUCCESS' && job.targetId != null && (
                <Button variant="ghost" size="sm" className="shrink-0 text-red-600 hover:text-red-700" disabled={removeMeeting.isPending} onClick={() => {
                  const ok = window.confirm('이 완료된 회의록을 삭제할까요?\n\n녹음 원본, 변환된 회의 내용과 회의에서 생성된 검색용 기록이 삭제됩니다. 이미 만들어진 할 일·결정의 내용은 유지되지만 회의 원문 근거 연결은 제거됩니다.\n\n이 작업은 되돌릴 수 없습니다.');
                  if (ok) removeMeeting.mutate({ meetingId: job.targetId!, jobId: job.id });
                }}><Trash2 size={13} /> 삭제</Button>
              )}
            </div>
          ))}
        </CardContent></Card>
      )}
    </div>
  );
}
