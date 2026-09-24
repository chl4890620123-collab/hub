import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ListChecks } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { LoadingBlock, EmptyState } from '@/components/ui/spinner';
import { MaterialResultList } from '@/features/materials/MaterialResultList';
import { useCurrentProject } from '@/hooks/useProjects';
import { materialsApi } from '@/api/endpoints/materials';
import { formatDate, formatDateTime } from '@/lib/format';
import { errorMessage } from '@/lib/errors';

const TASK_STATUS_LABELS: Record<string, string> = {
  TODO: '시작 전',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
  BLOCKED: '도움 필요',
};

const REVIEW_STATUS_LABELS: Record<string, string> = {
  AI_GENERATED: 'AI 제안',
  REVIEWING: '검토 중',
  CONFIRMED: '확정',
  REJECTED: '제외',
};

const CHANGE_CATEGORY_LABELS: Record<string, string> = {
  SCHEDULE: '일정 변경',
  BUDGET: '예산 변경',
  ASSIGNEE: '담당자 변경',
  FEATURE: '기능 변경',
  CONTRACT: '계약 변경',
  CONTENT: '내용 변경',
};

export function ContextPage() {
  const { currentProject } = useCurrentProject();
  const [query, setQuery] = useState('');

  const search = useMutation({
    mutationFn: (q: string) => materialsApi.context(currentProject!.id, q),
  });

  if (!currentProject) return <NoProjectState />;

  const bundle = search.data;

  return (
    <div>
      <PageHeader
        title="관련 업무 모아보기"
        description="키워드 하나로 문서·회의록·첨부파일·GitHub·Google Drive·Slack·Notion 자료와 관련 할 일, 결정, 변경 이력을 함께 확인합니다."
      />

      <form
        className="mb-5 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (query.trim()) search.mutate(query.trim());
        }}
      >
        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="예: 결제 모듈, 신규 거래처, 9월 회의" className="max-w-lg" />
        <Button type="submit" disabled={search.isPending}>
          <ListChecks size={14} /> 관련 업무 찾기
        </Button>
      </form>

      {search.isPending && <LoadingBlock />}
      {search.isError && <p className="text-sm text-red-600">{errorMessage(search.error)}</p>}

      {bundle && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {bundle.summary && (
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>요약</CardTitle>
              </CardHeader>
              <CardContent className="whitespace-pre-wrap text-sm text-ink-800">{bundle.summary}</CardContent>
            </Card>
          )}

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>관련 자료</CardTitle>
            </CardHeader>
            <CardContent>
              <MaterialResultList hits={bundle.sources} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>관련 할 일 ({bundle.todos.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {bundle.todos.length === 0 ? (
                <EmptyState title="관련 할 일이 없습니다." />
              ) : (
                <ul className="flex flex-col gap-2">
                  {bundle.todos.map((todo) => (
                    <li key={todo.id} className="rounded-md border border-ink-100 px-3 py-2 text-sm">
                      <p className="font-medium text-ink-800">{todo.title}</p>
                      <p className="text-xs text-ink-400">{formatDate(todo.dueDate)} · {TASK_STATUS_LABELS[todo.taskStatus] ?? todo.taskStatus}</p>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>관련 결정 ({bundle.decisions.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {bundle.decisions.length === 0 ? (
                <EmptyState title="관련 결정 사항이 없습니다." />
              ) : (
                <ul className="flex flex-col gap-2">
                  {bundle.decisions.map((d) => (
                    <li key={d.id} className="rounded-md border border-ink-100 px-3 py-2 text-sm">
                      <p className="text-ink-800">{d.statement}</p>
                      <Badge variant="outline" className="mt-1">
                        {REVIEW_STATUS_LABELS[d.review_status] ?? d.review_status}
                      </Badge>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>관련 변경 이력 ({bundle.changes.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {bundle.changes.length === 0 ? (
                <EmptyState title="관련 변경 이력이 없습니다." />
              ) : (
                <ul className="flex flex-col gap-2">
                  {bundle.changes.map((c) => (
                    <li key={c.id} className="rounded-md border border-ink-100 px-3 py-2 text-sm">
                      <p className="font-medium text-ink-800">{CHANGE_CATEGORY_LABELS[c.category] ?? '변경 사항'}</p>
                      <p className="text-xs text-ink-500">{c.reason}</p>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>관련 활동 기록 ({bundle.timeline.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {bundle.timeline.length === 0 ? (
                <EmptyState title="관련 활동이 없습니다." />
              ) : (
                <ul className="flex flex-col gap-2">
                  {bundle.timeline.map((event) => (
                    <li key={event.id} className="rounded-md border border-ink-100 px-3 py-2 text-sm">
                      <p className="text-ink-800">{event.title}</p>
                      <p className="text-xs text-ink-400">{formatDateTime(event.happenedAt)}</p>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
