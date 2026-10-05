import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, Github, HardDrive, MessageSquare, NotebookText, Save } from 'lucide-react';
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

const CONNECTOR_STATUS_LABELS: Record<string, string> = {
  SUCCESS: '가져오기 완료',
  FAILED: '가져오기 실패',
  PENDING: '대기 중',
  PROCESSING: '가져오는 중',
  RUNNING: '가져오는 중',
};

const PROVIDERS: { type: ConnectorType; label: string; targetNoun: string; icon: typeof Github; note?: string }[] = [
  { type: 'GITHUB', label: 'GitHub', targetNoun: '저장소', icon: Github },
  {
    type: 'GOOGLE_DRIVE',
    label: 'Google Drive',
    targetNoun: '폴더',
    icon: HardDrive,
    note: '같은 연결로 할 일에 기한을 정하면 내 구글 캘린더에도 자동으로 등록됩니다.',
  },
  { type: 'SLACK', label: 'Slack', targetNoun: '채널', icon: MessageSquare },
  { type: 'NOTION', label: 'Notion', targetNoun: '페이지', icon: NotebookText },
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
        queryClient.invalidateQueries({ queryKey: ['connector-connection', projectId] });
        queryClient.invalidateQueries({ queryKey: ['connector-targets', projectId] });
      }
    } else if (reason === 'access_denied') toast.error(`${label} 연결을 취소했습니다.`);
    else if (reason === 'not_configured') toast.error(`${label} 개인 연결은 아직 설정되지 않았습니다. 관리자가 앱 정보를 등록해야 합니다.`);
    else toast.error(`${label} 연결에 실패했습니다. 관리자 설정을 확인한 뒤 다시 시도해 주세요.`);
    window.history.replaceState(null, '', location.pathname);
  }, [projectId, queryClient]);
}

function LinkedAccountBadge({ projectId, type }: { projectId: number; type: ConnectorType }) {
  const { data, isError, error } = useQuery({
    queryKey: ['connector-connection', projectId, type],
    queryFn: () => connectorsApi.connection(projectId, type),
  });
  if (isError) return <span className="text-xs text-red-600">연결 상태 확인 실패: {errorMessage(error)}</span>;
  if (!data) return <Badge variant="neutral">상태 확인 중</Badge>;
  if (data.linkedByUser) return <Badge variant="accent">계정 등록됨 · {data.account || '내 계정'}</Badge>;
  if (data.connected) return <Badge variant="accent">공용 연결 설정됨</Badge>;
  return <Badge variant="neutral">연결 안 됨</Badge>;
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

  const { data: importedItems } = useQuery({
    queryKey: ['connector-items', currentProject?.id],
    queryFn: () => connectorsApi.items(currentProject!.id),
    enabled: !!currentProject,
  });

  const saveCopy = useMutation({
    mutationFn: (itemId: number) => connectorsApi.saveCopy(currentProject!.id, itemId),
    onSuccess: (result) => {
      toast.success(`'${result.title}'을 Hub 문서로 저장했습니다.`);
      queryClient.invalidateQueries({ queryKey: ['documents', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const disconnect = useMutation({
    mutationFn: (type: ConnectorType) => connectorsApi.disconnect(currentProject!.id, type),
    onSuccess: () => {
      toast.success('내 계정 연결을 해제했습니다. 회사 공용 연결이 허용된 경우 공용 연결로 전환될 수 있습니다.');
      queryClient.invalidateQueries({ queryKey: ['connector-status', currentProject?.id] });
      queryClient.invalidateQueries({ queryKey: ['connector-connection', currentProject?.id] });
      queryClient.invalidateQueries({ queryKey: ['connector-targets', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const connect = useMutation({
    mutationFn: (type: ConnectorType) => connectorsApi.connect(currentProject!.id, type),
    onSuccess: () => {
      toast.success('연결을 완료했습니다.');
      queryClient.invalidateQueries({ queryKey: ['connector-status', currentProject?.id] });
      queryClient.invalidateQueries({ queryKey: ['connector-connection', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (!currentProject) return <NoProjectState />;
  if (isLoading) return <LoadingBlock />;

  return (
    <div>
      <PageHeader
        title="연결 서비스"
        description="GitHub 저장소, Google Drive 폴더, Slack 채널, Notion 페이지를 연결해 현재 프로젝트의 검색 자료로 가져옵니다. 계정 등록 여부와 실제 접근 가능 여부는 다릅니다. 목록을 열어 접근을 확인해 주세요."
      />

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
                    <Badge variant={state.lastStatus === 'FAILED' ? 'danger' : state.lastStatus === 'SUCCESS' ? 'accent' : 'neutral'}>
                      {CONNECTOR_STATUS_LABELS[state.lastStatus] ?? state.lastStatus}
                    </Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-2">
                <p className="text-xs text-ink-400">
                  {state?.lastSyncedAt ? `마지막 가져오기: ${formatDateTime(state.lastSyncedAt)}` : '아직 가져온 자료가 없습니다.'}
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
                    {provider.label} {provider.targetNoun} 보기
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

      <Card className="mt-5">
        <CardHeader>
          <CardTitle>가져온 자료 관리</CardTitle>
          <p className="text-xs text-ink-500">
            외부 원본은 읽기 전용입니다. 저장소 위치와 원문 링크를 확인한 뒤 수정이 필요한 자료만 Hub 문서로 저장해 버전·보관·삭제로 관리합니다.
          </p>
        </CardHeader>
        <CardContent>
          {(statuses ?? []).filter((state) => state.lastStatus === 'SUCCESS').length > 0 && (
            <div className="mb-4 flex flex-wrap gap-2">
              {(statuses ?? []).filter((state) => state.lastStatus === 'SUCCESS').map((state) => (
                <Badge key={`${state.connectorType}-${state.externalScope}`} variant="outline">
                  {state.connectorType} · {state.externalScope} · 최근 {state.lastImportedCount}건
                </Badge>
              ))}
            </div>
          )}
          {!importedItems?.length ? (
            <p className="py-6 text-center text-sm text-ink-400">아직 저장된 연결 자료가 없습니다.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {importedItems.slice(0, 20).map((item) => (
                <li key={item.id} className="rounded-lg border border-ink-100 p-3">
                  <div className="mb-1 flex flex-wrap items-center gap-2">
                    <Badge variant="outline">{item.sourceType}</Badge>
                    <span className="text-xs text-ink-400">{item.itemType}</span>
                    <span className="text-xs text-ink-400">버전 {item.versionNo}</span>
                  </div>
                  <p className="text-sm font-semibold text-ink-800">{item.title}</p>
                  {item.location && <p className="mt-1 text-xs text-ink-500">저장소 위치: {item.location}</p>}
                  {item.snippet && <p className="mt-1 line-clamp-2 text-xs text-ink-600">{item.snippet}</p>}
                  <div className="mt-2 flex flex-wrap items-center gap-3">
                    {item.sourceUrl && (
                      <a href={item.sourceUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-xs text-accent-600 hover:underline">
                        원문 열기 <ExternalLink size={12} />
                      </a>
                    )}
                    <Button size="sm" variant="outline" disabled={saveCopy.isPending || item.sourceDeleted} onClick={() => saveCopy.mutate(item.id)}>
                      <Save size={13} /> Hub 문서로 저장
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <ConnectorBrowserDialog
        projectId={currentProject.id}
        type={browsing}
        open={browsing != null}
        onOpenChange={(open) => !open && setBrowsing(null)}
        providerLabel={PROVIDERS.find((p) => p.type === browsing)?.label ?? '연결 서비스'}
        targetNoun={PROVIDERS.find((p) => p.type === browsing)?.targetNoun ?? '항목'}
      />
    </div>
  );
}
