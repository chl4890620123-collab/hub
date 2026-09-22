import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { adminSecurityApi } from '@/api/endpoints/admin';
import { connectorsApi } from '@/api/endpoints/connectors';
import type { ConnectorType } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const CONNECTOR_LABELS: Record<ConnectorType, string> = {
  GITHUB: 'GitHub',
  GOOGLE_DRIVE: 'Google Drive',
  SLACK: 'Slack',
  NOTION: 'Notion',
};

function ConnectorPolicyPanel() {
  const queryClient = useQueryClient();
  const { data: policy, isLoading } = useQuery({ queryKey: ['connector-policy'], queryFn: connectorsApi.policy });

  const setPolicy = useMutation({
    mutationFn: ({ type, enabled }: { type: ConnectorType; enabled: boolean }) => connectorsApi.setPolicy(type, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['connector-policy'] }),
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>커넥터 사용 정책</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : (
          <ul className="flex flex-col gap-2">
            {(Object.keys(CONNECTOR_LABELS) as ConnectorType[]).map((type) => (
              <li key={type} className="flex items-center justify-between rounded-md border border-ink-100 px-3 py-2 text-sm">
                <span>{CONNECTOR_LABELS[type]}</span>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={policy?.[type] ?? false}
                    onChange={(e) => setPolicy.mutate({ type, enabled: e.target.checked })}
                  />
                  사용 허용
                </label>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function SensitiveTermsPanel() {
  const queryClient = useQueryClient();
  const [term, setTerm] = useState('');
  const { data: terms, isLoading } = useQuery({ queryKey: ['sensitive-terms'], queryFn: adminSecurityApi.sensitiveTerms });

  const add = useMutation({
    mutationFn: () => adminSecurityApi.addSensitiveTerm(term),
    onSuccess: () => {
      setTerm('');
      queryClient.invalidateQueries({ queryKey: ['sensitive-terms'] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const remove = useMutation({
    mutationFn: (id: number) => adminSecurityApi.removeSensitiveTerm(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sensitive-terms'] }),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>민감 정보 해시 처리 단어</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="text-xs text-ink-500">
          여기 등록한 단어/값은 회의 STT 텍스트에서 저장·검색되기 전에 해시로 가려집니다.
        </p>
        <div className="flex gap-2">
          <Input value={term} onChange={(e) => setTerm(e.target.value)} placeholder="가릴 단어나 값" className="max-w-xs" />
          <Button disabled={!term.trim() || add.isPending} onClick={() => add.mutate()}>
            추가
          </Button>
        </div>
        {isLoading ? (
          <LoadingBlock />
        ) : !terms || terms.length === 0 ? (
          <EmptyState title="등록된 항목이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-1.5">
            {terms.map((t) => (
              <li key={t.id} className="flex items-center justify-between rounded-md border border-ink-100 px-3 py-1.5 text-sm">
                {t.term}
                <Button size="sm" variant="ghost" onClick={() => remove.mutate(t.id)}>
                  삭제
                </Button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

export function AdminSecurityPage() {
  return (
    <div className="flex flex-col gap-4">
      <ConnectorPolicyPanel />
      <SensitiveTermsPanel />
    </div>
  );
}
