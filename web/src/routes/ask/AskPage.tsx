import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
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
import { errorMessage } from '@/lib/errors';

export function AskPage() {
  const { currentProject } = useCurrentProject();
  const [question, setQuestion] = useState('');

  const ask = useMutation({
    mutationFn: (q: string) => materialsApi.ask(currentProject!.id, q),
  });

  if (!currentProject) return <NoProjectState />;

  return (
    <div>
      <PageHeader title="AI에게 묻기" description="자연어로 질문하면 근거 자료와 함께 답변합니다." />

      <form
        className="mb-5 flex flex-col gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (question.trim()) ask.mutate(question.trim());
        }}
      >
        <Textarea
          rows={3}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="예: 지난주 결정된 API 스펙 변경 사항이 뭐였지?"
        />
        <Button type="submit" disabled={ask.isPending} className="self-start">
          <Sparkles size={14} /> {ask.isPending ? '생각 중...' : '질문하기'}
        </Button>
      </form>

      {ask.isPending && <LoadingBlock label="답변을 생성하는 중..." />}
      {ask.isError && <p className="text-sm text-red-600">{errorMessage(ask.error)}</p>}
      {ask.data && (
        <div className="flex flex-col gap-4">
          <Card>
            <CardContent className="whitespace-pre-wrap p-4 text-sm text-ink-800">{ask.data.answer}</CardContent>
          </Card>
          <div>
            <p className="mb-2 text-sm font-medium text-ink-600">근거 자료</p>
            <MaterialResultList hits={ask.data.sources} emptyLabel="근거 자료가 없습니다." />
          </div>
        </div>
      )}
    </div>
  );
}
