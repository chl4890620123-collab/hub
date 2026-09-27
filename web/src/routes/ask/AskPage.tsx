import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Textarea } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { LoadingBlock } from '@/components/ui/spinner';
import { MaterialResultList } from '@/features/materials/MaterialResultList';
import { useCurrentProject } from '@/hooks/useProjects';
import { useCurrentUser } from '@/hooks/useAuth';
import { useJobPolling } from '@/hooks/useJobPolling';
import { materialsApi } from '@/api/endpoints/materials';
import type { MaterialAskResponse } from '@/api/types';
import { errorMessage } from '@/lib/errors';

export function AskPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const [question, setQuestion] = useState('');
  const [jobId, setJobId] = useState<number | null>(null);
  const projectId = currentProject?.id;
  const questionKey = `hub.last-ask-question.${user?.id ?? 'anon'}.${projectId ?? 'none'}`;
  const jobKey = `hub.last-ask-job.${user?.id ?? 'anon'}.${projectId ?? 'none'}`;

  useEffect(() => {
    if (!projectId || !user) return;
    try {
      setQuestion(window.localStorage.getItem(questionKey) ?? '');
      const rawJob = window.localStorage.getItem(jobKey);
      setJobId(rawJob ? Number(rawJob) || null : null);
    } catch {
      setQuestion('');
      setJobId(null);
    }
  }, [projectId, user?.id, questionKey, jobKey]);

  const queue = useMutation({
    mutationFn: (q: string) => materialsApi.askJob(projectId!, q),
    onSuccess: (result) => {
      setJobId(result.jobId);
      try { window.localStorage.setItem(jobKey, String(result.jobId)); } catch { /* optional persistence */ }
      queryClient.invalidateQueries({ queryKey: ['top-searches', projectId, user?.id] });
      queryClient.invalidateQueries({ queryKey: ['jobs-recent', projectId] });
    },
  });
  const { data: job } = useJobPolling(jobId);

  const data = useMemo<MaterialAskResponse | undefined>(() => {
    if (job?.status !== 'SUCCESS' || !job.resultJson) return undefined;
    try { return JSON.parse(job.resultJson) as MaterialAskResponse; } catch { return undefined; }
  }, [job?.status, job?.resultJson]);

  if (!currentProject) return <NoProjectState />;

  const pending = queue.isPending || job?.status === 'PENDING' || job?.status === 'RUNNING';
  const submit = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;
    try { window.localStorage.setItem(questionKey, trimmed); } catch { /* optional persistence */ }
    queue.mutate(trimmed);
  };

  return (
    <div>
      <PageHeader
        title="AI에게 묻기"
        description="현재 프로젝트에서 볼 수 있는 자료를 바탕으로 서버가 답변을 생성합니다. 메뉴 이동이나 새로고침 후에도 같은 작업 상태와 결과를 다시 확인할 수 있습니다."
      />
      <form className="mb-5 flex flex-col gap-2" onSubmit={(e) => { e.preventDefault(); submit(question); }}>
        <Textarea rows={3} value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="예: 지난주 결정된 API 스펙 변경 사항이 뭐였지?" />
        <Button type="submit" disabled={pending} className="self-start">
          <Sparkles size={14} /> {pending ? '생각 중...' : '질문하기'}
        </Button>
      </form>

      {pending && <LoadingBlock label="서버에서 답변을 생성하고 있습니다. 다른 화면으로 이동해도 계속 진행됩니다." />}
      {queue.error && !pending && <p className="text-sm text-red-600">{errorMessage(queue.error)}</p>}
      {job?.status === 'FAILED' && <p className="text-sm text-red-600">{job.errorMessage || 'AI 답변 생성에 실패했습니다.'}</p>}
      {job?.status === 'SUCCESS' && !data && <p className="text-sm text-red-600">완료된 답변을 읽지 못했습니다. 다시 질문해 주세요.</p>}
      {data && !pending && (
        <div className="flex flex-col gap-4">
          <Card><CardContent className="whitespace-pre-wrap p-4 text-sm text-ink-800">{data.answer}</CardContent></Card>
          <div>
            <p className="mb-2 text-sm font-medium text-ink-600">근거 자료</p>
            <MaterialResultList hits={data.sources} emptyLabel="근거 자료가 없습니다." />
          </div>
        </div>
      )}
    </div>
  );
}
