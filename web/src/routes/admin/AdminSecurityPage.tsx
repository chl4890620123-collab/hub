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
        <CardTitle>외부 서비스 사용 설정</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="mb-3 text-xs text-ink-500">회사에서 사용할 외부 서비스를 켜거나 끕니다. 꺼진 서비스는 연결 화면에도 표시되지 않습니다.</p>
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
        <CardTitle>회의에서 가릴 민감 정보</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="text-xs text-ink-500">
          여기에 등록한 단어나 값은 회의 음성을 글로 바꾼 뒤 저장·검색하기 전에 알아볼 수 없는 값으로 바꿉니다.
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
