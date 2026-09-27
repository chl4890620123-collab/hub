import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, ExternalLink } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { useCurrentProject } from '@/hooks/useProjects';
import { notificationsApi } from '@/api/endpoints/notifications';
import { formatDateTime } from '@/lib/format';

const CATEGORY_LABELS: Record<string, string> = {
  GIT_CI: 'Git/CI',
  CONNECTOR: '연결 서비스',
  PROCESSING: '처리 작업',
};

export function NotificationsPage() {
  const { currentProject } = useCurrentProject();
  const projectId = currentProject?.id ?? 0;
  const { data, isLoading } = useQuery({
    queryKey: ['project-notifications', projectId],
    queryFn: () => notificationsApi.list(projectId),
    enabled: projectId > 0,
    refetchInterval: 60_000,
  });

  if (!currentProject) return <NoProjectState />;
  const rows = data ?? [];

  return (
    <div>
      <PageHeader
        title="알림"
        description="GitHub CI·배포, 연결 서비스, 백그라운드 처리 오류를 문서와 분리해서 확인합니다."
      />
      <Card>
        <CardHeader>
          <CardTitle>확인 필요한 항목</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <LoadingBlock />
          ) : rows.length === 0 ? (
            <EmptyState title="현재 확인할 오류 알림이 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {rows.map((item) => (
                <li key={item.id} className="rounded-md border border-ink-100 px-3 py-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="mb-1 flex flex-wrap items-center gap-2">
                        <AlertTriangle size={15} className={item.severity === 'ERROR' ? 'text-red-600' : 'text-amber-600'} />
                        <Badge variant={item.severity === 'ERROR' ? 'warning' : 'outline'}>
                          {CATEGORY_LABELS[item.category] ?? item.category}
                        </Badge>
                        <span className="text-xs text-ink-400">{item.source}</span>
                      </div>
                      <p className="text-sm font-medium text-ink-800">{item.title}</p>
                      {item.detail && <p className="mt-1 text-xs text-ink-500">{item.detail}</p>}
                      {item.occurredAt && <p className="mt-1 text-[11px] text-ink-400">{formatDateTime(item.occurredAt)}</p>}
                    </div>
                    {item.url && (
                      <a
                        href={item.url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex shrink-0 items-center gap-1 text-xs text-accent-600 hover:underline"
                      >
                        원문 보기 <ExternalLink size={12} />
                      </a>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
