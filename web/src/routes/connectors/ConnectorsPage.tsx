import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Github, HardDrive, MessageSquare, NotebookText } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button, buttonVariants } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { LoadingBlock } from '@/components/ui/spinner';
import { cn } from '@/lib/cn';
import { ConnectorBrowserDialog } from '@/features/connectors/ConnectorBrowserDialog';
import { useCurrentProject } from '@/hooks/useProjects';
import { connectorsApi } from '@/api/endpoints/connectors';
import type { ConnectorType } from '@/api/types';
import { formatDateTime } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';
import { mockMode } from '@/api/mockApi';

const PROVIDERS: { type: ConnectorType; label: string; icon: typeof Github; note?: string }[] = [
  { type: 'GITHUB', label: 'GitHub', icon: Github },
  {
    type: 'GOOGLE_DRIVE',
    label: 'Google Drive',
    icon: HardDrive,
    note: '같은 연결로 할 일에 기한을 정하면 내 구글 캘린더에도 자동으로 등록됩니다.',
  },
  { type: 'SLACK', label: 'Slack', icon: MessageSquare },
  { type: 'NOTION', label: 'Notion', icon: NotebookText },
];

/** The OAuth callback lands back here with its outcome in the query string; without this the
 * operator is dropped on a silent page and can't tell whether the connection actually worked -
 * and the status/linked-account badges kept showing the pre-connect state until a manual refresh,
 * since nothing invalidated those queries after a successful connect. */
function useConnectorCallbackToast(projectId: number | undefined) {
  const queryClient = useQueryClient();
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const status = params.get('status');
    const type = params.get('connector');
    if (!status || !type) return;
    const label = PROVIDERS.find((p) => p.type === type)?.label ?? type;
    const reason = params.get('reason');
    if (status === 'connected') {
      toast.success(`${label} 연결이 완료되었습니다.`);
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: ['connector-status', projectId] });
        queryClient.invalidateQueries({ queryKey: ['connector-targets', projectId] });
      }
    } else if (reason === 'access_denied') toast.error(`${label} 연결을 취소했습니다.`);
    else if (reason === 'not_configured') toast.error(`${label} 개인 연결은 아직 설정되지 않았습니다. 관리자가 앱 정보를 등록해야 합니다.`);
    else toast.error(`${label} 연결에 실패했습니다${reason ? ` (${reason})` : ''}. 관리자 설정을 확인한 뒤 다시 시도해 주세요.`);
    window.history.replaceState(null, '', location.pathname);
  }, [projectId, queryClient]);
}

function LinkedAccountBadge({ projectId, type }: { projectId: number; type: ConnectorType }) {
  const { data } = useQuery({
    queryKey: ['connector-targets', projectId, type],
    queryFn: () => connectorsApi.targets(projectId, type),
  });
  if (!data) return <Badge variant="neutral">상태 확인 중</Badge>;
  if (data.linkedByUser) return <Badge variant="accent">연동됨 · {data.account || '내 계정'}</Badge>;
  if (data.connected) return <Badge variant="accent">서버 계정 사용 중</Badge>;
  return <Badge variant="neutral">연동 안 됨</Badge>;
}

export function ConnectorsPage() {
  const { currentProject } = useCurrentProject();
  const queryClient = useQueryClient();
  const [browsing, setBrowsing] = useState<ConnectorType | null>(null);
  useConnectorCallbackToast(currentProject?.id);

  const { data: policy } = useQuery({ queryKey: ['connector-policy'], queryFn: connectorsApi.policy });
  const { data: statuses, isLoading } = useQuery({
    queryKey: ['connector-status', currentProject?.id],
    queryFn: () => connectorsApi.status(currentProject!.id),
    enabled: !!currentProject,
  });

  const disconnect = useMutation({
    mutationFn: (type: ConnectorType) => connectorsApi.disconnect(currentProject!.id, type),
    onSuccess: () => {
      toast.success('연결을 해제했습니다.');
      queryClient.invalidateQueries({ queryKey: ['connector-status', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const connect = useMutation({
    mutationFn: (type: ConnectorType) => connectorsApi.connect(currentProject!.id, type),
    onSuccess: () => {
      toast.success('개발용 계정 연결을 완료했습니다.');
      queryClient.invalidateQueries({ queryKey: ['connector-status', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!currentProject) return <NoProjectState />;
  if (isLoading) return <LoadingBlock />;

  return (
    <div>
      <PageHeader title="연결 서비스" description="GitHub, Google Drive, Slack, Notion 계정을 연결해 자료를 가져옵니다." />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {PROVIDERS.filter((p) => policy?.[p.type] !== false).map((provider) => {
          const state = statuses?.find((s) => s.connectorType === provider.type);
          return (
            <Card key={provider.type}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <provider.icon size={18} /> {provider.label}
                </CardTitle>
                <div className="flex flex-wrap items-center gap-1.5">
                  <LinkedAccountBadge projectId={currentProject.id} type={provider.type} />
                  {state?.lastStatus && (
                    <Badge variant={state.lastStatus === 'SUCCESS' ? 'accent' : 'danger'}>{state.lastStatus}</Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-2">
                <p className="text-xs text-ink-400">
                  {state?.lastSyncedAt ? `마지막 동기화: ${formatDateTime(state.lastSyncedAt)}` : '아직 가져온 자료가 없습니다.'}
                </p>
                {provider.note && <p className="text-xs text-ink-400">{provider.note}</p>}
                <div className="flex gap-2">
                  {mockMode ? (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={connect.isPending}
                      onClick={() => connect.mutate(provider.type)}
                    >
                      연결하기
                    </Button>
                  ) : (
                    <a
                      href={connectorsApi.authorizeUrl(currentProject.id, provider.type)}
                      className={cn(buttonVariants({ variant: 'outline', size: 'sm' }))}
                    >
                      연결하기
                    </a>
                  )}
                  <Button size="sm" onClick={() => setBrowsing(provider.type)}>
                    가져올 항목 보기
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => disconnect.mutate(provider.type)}>
                    연결 해제
                  </Button>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <ConnectorBrowserDialog
        projectId={currentProject.id}
        type={browsing}
        open={browsing != null}
        onOpenChange={(open) => !open && setBrowsing(null)}
      />
    </div>
  );
}
