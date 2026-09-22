import { useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Mic, Square, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label, Textarea } from '@/components/ui/input';
import { JobStatusPanel } from '@/components/feedback/JobStatusPanel';
import { useCurrentProject } from '@/hooks/useProjects';
import { useMediaRecorder } from '@/features/meetings/useMediaRecorder';
import { meetingsApi } from '@/api/endpoints/meetings';
import { documentsApi } from '@/api/endpoints/documents';
import { localDateTimeWithOffset } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function formatElapsed(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  return `${String(Math.floor(totalSeconds / 60)).padStart(2, '0')}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

function RecordingPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const { isRecording, elapsedMs, start, stop } = useMediaRecorder();
  const [title, setTitle] = useState('');

  const upload = useMutation({
    mutationFn: (blob: Blob) => meetingsApi.upload(projectId, title || '녹음 회의', blob, localDateTimeWithOffset()),
    onSuccess: (result) => {
      toast.success('녹음을 업로드했습니다. STT/분석이 진행됩니다.');
      onJobStarted(result.jobId);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>실시간 녹음</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="회의 제목" disabled={isRecording} />
        <div className="flex items-center gap-3">
          {!isRecording ? (
            <Button onClick={start}>
              <Mic size={14} /> 녹음 시작
            </Button>
          ) : (
            <Button
              variant="danger"
              onClick={async () => {
                const blob = await stop();
                upload.mutate(blob);
              }}
            >
              <Square size={14} /> 녹음 종료 ({formatElapsed(elapsedMs)})
            </Button>
          )}
          {upload.isPending && <span className="text-sm text-ink-400">업로드 중...</span>}
        </div>
        <p className="text-xs text-ink-400">최대 25분까지 녹음되며, 시간이 지나면 자동으로 종료됩니다.</p>
      </CardContent>
    </Card>
  );
}

function AudioUploadPanel({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [title, setTitle] = useState('');

  const upload = useMutation({
    mutationFn: (file: File) => meetingsApi.upload(projectId, title || file.name, file),
    onSuccess: (result) => {
      toast.success('업로드했습니다. STT/분석이 진행됩니다.');
      onJobStarted(result.jobId);
      if (fileRef.current) fileRef.current.value = '';
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>오디오 파일 업로드</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-wrap items-end gap-3">
        <div>
          <Label htmlFor="meeting-title">회의 제목</Label>
          <Input id="meeting-title" value={title} onChange={(e) => setTitle(e.target.value)} className="w-56" />
        </div>
        <input ref={fileRef} type="file" accept="audio/*" className="text-sm" />
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

function ManualMeetingNoteForm({ projectId, onJobStarted }: { projectId: number; onJobStarted: (jobId: number) => void }) {
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');

  const submit = useMutation({
    mutationFn: () => documentsApi.manual(projectId, { title, text }),
    onSuccess: (result) => {
      toast.success('회의 노트를 저장했습니다.');
      onJobStarted(result.jobId);
      setTitle('');
      setText('');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>회의록 직접 입력</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="회의 제목" />
        <Textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} placeholder="회의 내용을 입력하세요" />
        <Button disabled={!title.trim() || !text.trim() || submit.isPending} onClick={() => submit.mutate()} className="self-start">
          저장 및 분석 요청
        </Button>
      </CardContent>
    </Card>
  );
}

export function MeetingsPage() {
  const { currentProject } = useCurrentProject();
  const [activeJobId, setActiveJobId] = useState<number | null>(null);

  if (!currentProject) return <NoProjectState />;

  return (
    <div>
      <PageHeader title="회의 녹음" description="음성을 녹음하거나 업로드하면 자동으로 텍스트 변환 및 요약이 진행됩니다." />

      <div className="mb-5 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <RecordingPanel projectId={currentProject.id} onJobStarted={setActiveJobId} />
        <AudioUploadPanel projectId={currentProject.id} onJobStarted={setActiveJobId} />
        <div className="lg:col-span-2">
          <ManualMeetingNoteForm projectId={currentProject.id} onJobStarted={setActiveJobId} />
        </div>
      </div>

      {activeJobId && <JobStatusPanel jobId={activeJobId} />}
    </div>
  );
}
