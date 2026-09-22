import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { useCurrentProject } from '@/hooks/useProjects';
import { adminHistoryApi } from '@/api/endpoints/admin';

function JsonRow({ row }: { row: Record<string, unknown> }) {
  return (
    <li className="rounded-md border border-ink-100 px-3 py-2 text-xs">
      <pre className="overflow-x-auto whitespace-pre-wrap text-ink-600">{JSON.stringify(row, null, 2)}</pre>
    </li>
  );
}

export function AdminHistoryPage() {
  const { currentProject } = useCurrentProject();
  const { data: revisions, isLoading: loadingRevisions } = useQuery({
    queryKey: ['revisions', currentProject?.id],
    queryFn: () => adminHistoryApi.revisions(currentProject!.id),
    enabled: !!currentProject,
  });
  const { data: auditLog, isLoading: loadingAudit } = useQuery({ queryKey: ['audit-log'], queryFn: adminHistoryApi.audit });

  if (!currentProject) return <NoProjectState />;

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>AI 수정 이력 (Revisions)</CardTitle>
        </CardHeader>
        <CardContent>
          {loadingRevisions ? (
            <LoadingBlock />
          ) : !revisions || revisions.length === 0 ? (
            <EmptyState title="이력이 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {revisions.map((row, i) => (
                <JsonRow key={i} row={row} />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>감사 로그 (전체 회사)</CardTitle>
        </CardHeader>
        <CardContent>
          {loadingAudit ? (
            <LoadingBlock />
          ) : !auditLog || auditLog.length === 0 ? (
            <EmptyState title="감사 로그가 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {auditLog.map((row, i) => (
                <JsonRow key={i} row={row} />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
