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
import { materialsApi } from '@/api/endpoints/materials';
import type { MaterialAskResponse } from '@/api/types';
import { errorMessage } from '@/lib/errors';

export function AskPage() {
  const { currentProject } = useCurrentProject();
  const [question, setQuestion] = useState('');
  const projectId = currentProject?.id;
  const storageKey = `hub.last-ask-question.${projectId ?? 'none'}`;

  useEffect(() => {
    if (!projectId) return;
    try { setQuestion(window.sessionStorage.getItem(storageKey) ?? ''); } catch { setQuestion(''); }
  }, [projectId, storageKey]);

  const mutationKey = ['material-ask', projectId] as const;
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

  if (!currentProject) return <NoProjectState />;

  const submit = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;
    try { window.sessionStorage.setItem(storageKey, trimmed); } catch { /* optional */ }
    ask.mutate(trimmed);
  };

  return (
    <div>
      <PageHeader
        title="AI에게 묻기"
        description="현재 프로젝트의 사용자 문서, 볼 수 있는 첨부파일과 연결 서비스 자료를 바탕으로 답하고 근거를 함께 보여줍니다. 다른 메뉴로 이동해도 진행 중인 요청은 계속됩니다."
      />
      <form className="mb-5 flex flex-col gap-2" onSubmit={(e) => { e.preventDefault(); submit(question); }}>
        <Textarea rows={3} value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="예: 지난주 결정된 API 스펙 변경 사항이 뭐였지?" />
        <Button type="submit" disabled={pending} className="self-start">
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
