import { useState } from 'react';
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

const PROVIDERS: { type: ConnectorType; label: string; icon: typeof Github }[] = [
  { type: 'GITHUB', label: 'GitHub', icon: Github },
  { type: 'GOOGLE_DRIVE', label: 'Google Drive', icon: HardDrive },
  { type: 'SLACK', label: 'Slack', icon: MessageSquare },
  { type: 'NOTION', label: 'Notion', icon: NotebookText },
];

export function ConnectorsPage() {
  const { currentProject } = useCurrentProject();
  const queryClient = useQueryClient();
  const [browsing, setBrowsing] = useState<ConnectorType | null>(null);

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
                {state?.lastStatus && <Badge variant={state.lastStatus === 'SUCCESS' ? 'accent' : 'danger'}>{state.lastStatus}</Badge>}
              </CardHeader>
              <CardContent className="flex flex-col gap-2">
                <p className="text-xs text-ink-400">
                  {state?.lastSyncedAt ? `마지막 동기화: ${formatDateTime(state.lastSyncedAt)}` : '아직 가져온 자료가 없습니다.'}
                </p>
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
