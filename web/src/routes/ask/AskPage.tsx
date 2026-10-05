import { useEffect, useState } from 'react';
import { useMutation, useMutationState } from '@tanstack/react-query';
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
import { materialsApi } from '@/api/endpoints/materials';
import type { MaterialAskResponse } from '@/api/types';
import { errorMessage } from '@/lib/errors';

export function AskPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  if (!currentProject || !user) return <NoProjectState />;
  return <ProjectAskPage key={`${user.id}-${currentProject.id}`} projectId={currentProject.id} userId={user.id} />;
}

function ProjectAskPage({ projectId, userId }: { projectId: number; userId: number }) {
  const [question, setQuestion] = useState('');
  const storageKey = `hub.last-ask-question.${userId}.${projectId}`;

  useEffect(() => {
    if (!projectId) return;
    try { setQuestion(window.sessionStorage.getItem(storageKey) ?? ''); } catch { setQuestion(''); }
  }, [projectId, storageKey]);

  const mutationKey = ['material-ask', projectId, userId] as const;
  const ask = useMutation({
    mutationKey,
    mutationFn: (q: string) => materialsApi.ask(projectId!, q),
  });
  const remembered = useMutationState({
    filters: { mutationKey },
    select: (mutation) => ({
      status: mutation.state.status,
      data: mutation.state.data as MaterialAskResponse | undefined,
      error: mutation.state.error,
    }),
  });
  const latest = remembered.at(-1);
  const pending = ask.isPending || latest?.status === 'pending';
  const data = ask.data ?? latest?.data;
  const error = ask.error ?? latest?.error;

  const submit = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed || trimmed.length > 1000 || pending) return;
    try { window.sessionStorage.setItem(storageKey, trimmed); } catch { /* optional */ }
    ask.mutate(trimmed);
  };

  return (
    <div>
      <PageHeader
        title="AI에게 묻기"
        description="현재 프로젝트에서 볼 수 있는 문서와 가져온 연결 서비스 자료를 근거로 답합니다. 첨부파일은 파일명·메모만 참고하며 본문은 자동으로 읽지 않습니다."
      />
      <form className="mb-5 flex flex-col gap-2" onSubmit={(e) => { e.preventDefault(); submit(question); }}>
        <Textarea aria-label="프로젝트 자료에 대한 질문" aria-describedby="ask-input-help" rows={3} maxLength={1000} value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="예: 첫 베타 대상과 검색 결과 표시 기준을 근거와 함께 알려줘." />
        <p id="ask-input-help" className="text-xs text-ink-500">{question.length}/1,000자 · 질문마다 자료를 새로 찾습니다. 이전 대화를 기억하거나 업무 생성·담당자 변경을 실행하지 않습니다. 다른 메뉴로 이동해도 진행 중인 요청은 계속됩니다.</p>
        <Button type="submit" disabled={pending || !question.trim() || question.trim().length > 1000} className="self-start">
          <Sparkles size={14} /> {pending ? '생각 중...' : '질문하기'}
        </Button>
      </form>

      {pending && <LoadingBlock label="답변을 생성하는 중... 다른 화면을 봐도 계속 진행됩니다." />}
      {error && !pending && <p className="text-sm text-red-600">{errorMessage(error)}</p>}
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
