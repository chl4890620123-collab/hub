import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { MaterialResultList } from '@/features/materials/MaterialResultList';
import { useCurrentProject } from '@/hooks/useProjects';
import { adminSearchRuleApi } from '@/api/endpoints/admin';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

function NewRuleForm({ projectId, onCreated }: { projectId: number; onCreated: () => void }) {
  const [name, setName] = useState('');
  const [aliases, setAliases] = useState('');
  const [patterns, setPatterns] = useState('');

  const create = useMutation({
    mutationFn: () =>
      adminSearchRuleApi.create(projectId, {
        name,
        aliases: aliases.split(',').map((s) => s.trim()).filter(Boolean),
        patterns: patterns.split(',').map((s) => s.trim()).filter(Boolean),
      }),
    onSuccess: () => {
      toast.success('검색 규칙을 추가했습니다.');
      setName('');
      setAliases('');
      setPatterns('');
      onCreated();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div>
        <Label htmlFor="rule-name">규칙 이름</Label>
        <Input id="rule-name" value={name} onChange={(e) => setName(e.target.value)} className="w-40" />
      </div>
      <div>
        <Label htmlFor="rule-aliases">동의어 (쉼표 구분)</Label>
        <Input id="rule-aliases" value={aliases} onChange={(e) => setAliases(e.target.value)} className="w-48" />
      </div>
      <div>
        <Label htmlFor="rule-patterns">패턴 (쉼표 구분)</Label>
        <Input id="rule-patterns" value={patterns} onChange={(e) => setPatterns(e.target.value)} className="w-48" />
      </div>
      <Button disabled={!name.trim() || create.isPending} onClick={() => create.mutate()}>
        추가
      </Button>
    </div>
  );
}

export function AdminSearchRulesPage() {
  const { currentProject } = useCurrentProject();
  const queryClient = useQueryClient();
  const [testQuery, setTestQuery] = useState('');

  const { data: rules, isLoading } = useQuery({
    queryKey: ['admin-search-rules', currentProject?.id],
    queryFn: () => adminSearchRuleApi.list(currentProject!.id),
    enabled: !!currentProject,
  });
  const { data: embeddingStatus } = useQuery({
    queryKey: ['embedding-status', currentProject?.id],
    queryFn: () => adminSearchRuleApi.embeddingStatus(currentProject!.id),
    enabled: !!currentProject,
  });
  const testResult = useMutation({
    mutationFn: (q: string) => adminSearchRuleApi.test(currentProject!.id, q),
  });
  const retryEmbedding = useMutation({
    mutationFn: () => adminSearchRuleApi.embeddingRetry(currentProject!.id),
    onSuccess: (result) => toast.success(`${result.reindexed}건을 재색인했습니다.`),
  });
  const deleteRule = useMutation({
    mutationFn: (ruleId: number) => adminSearchRuleApi.delete(currentProject!.id, ruleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-search-rules', currentProject?.id] }),
  });

  if (!currentProject) return <NoProjectState />;

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>검색 규칙</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <NewRuleForm
            projectId={currentProject.id}
            onCreated={() => queryClient.invalidateQueries({ queryKey: ['admin-search-rules', currentProject.id] })}
          />
          {isLoading ? (
            <LoadingBlock />
          ) : !rules || rules.length === 0 ? (
            <EmptyState title="등록된 검색 규칙이 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {rules.map((rule) => (
                <li key={rule.id} className="flex items-center justify-between rounded-md border border-ink-100 px-3 py-2 text-sm">
                  <div>
                    <p className="font-medium text-ink-800">{rule.name}</p>
                    <p className="text-xs text-ink-400">동의어: {rule.aliases.join(', ') || '-'}</p>
                  </div>
                  <Button size="sm" variant="ghost" onClick={() => rule.id && deleteRule.mutate(rule.id)}>
                    삭제
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>임베딩 상태</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          <pre className="overflow-x-auto rounded-md bg-ink-50 p-3 text-xs text-ink-600">
            {JSON.stringify(embeddingStatus ?? {}, null, 2)}
          </pre>
          <Button size="sm" disabled={retryEmbedding.isPending} onClick={() => retryEmbedding.mutate()} className="self-start">
            재색인 재시도
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>검색 테스트</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex gap-2">
            <Input value={testQuery} onChange={(e) => setTestQuery(e.target.value)} placeholder="테스트 검색어" className="max-w-sm" />
            <Button disabled={!testQuery.trim() || testResult.isPending} onClick={() => testResult.mutate(testQuery.trim())}>
              테스트
            </Button>
          </div>
          {testResult.data && (
            <div>
              {testResult.data.matchedRule && (
                <p className="mb-2 text-xs text-accent-600">매칭된 규칙: {testResult.data.matchedRule.name}</p>
              )}
              <MaterialResultList hits={testResult.data.results} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
