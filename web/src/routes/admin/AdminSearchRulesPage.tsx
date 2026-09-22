import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { Badge } from '@/components/ui/badge';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { MaterialResultList } from '@/features/materials/MaterialResultList';
import { useCurrentProject } from '@/hooks/useProjects';
import { adminSearchRuleApi } from '@/api/endpoints/admin';
import type { RuleInput, SearchRule } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const MODE_OPTIONS: { value: RuleInput['mode']; label: string }[] = [
  { value: 'SMART', label: '알아서 읽기' },
  { value: 'FULL', label: '가능하면 처음부터 끝까지 읽기' },
];
const PRIORITY_OPTIONS = [
  { value: 100, label: '보통' },
  { value: 300, label: '먼저' },
  { value: 500, label: '가장 먼저' },
];

const EMPTY_FORM = { name: '', targetFile: '', aliases: '', patterns: '', mode: 'SMART' as RuleInput['mode'], priority: 100, active: true };

function RuleForm({
  projectId,
  editingRule,
  onDone,
  onCancelEdit,
}: {
  projectId: number;
  editingRule: SearchRule | null;
  onDone: () => void;
  onCancelEdit: () => void;
}) {
  const [form, setForm] = useState(EMPTY_FORM);

  useEffect(() => {
    if (editingRule) {
      setForm({
        name: editingRule.name,
        targetFile: editingRule.targetFile ?? '',
        aliases: editingRule.aliases.join(', '),
        patterns: editingRule.patterns.join(', '),
        mode: editingRule.mode,
        priority: editingRule.priority,
        active: editingRule.active,
      });
    } else {
      setForm(EMPTY_FORM);
    }
  }, [editingRule]);

  const buildInput = (): RuleInput => ({
    name: form.name.trim(),
    targetFile: form.targetFile.trim() || null,
    aliases: form.aliases.split(/[,\n]/).map((s) => s.trim()).filter(Boolean),
    patterns: form.patterns.split(/[,\n]/).map((s) => s.trim()).filter(Boolean),
    mode: form.mode,
    priority: form.priority,
    active: form.active,
  });

  const save = useMutation({
    mutationFn: () =>
      editingRule?.id
        ? adminSearchRuleApi.update(projectId, editingRule.id, buildInput())
        : adminSearchRuleApi.create(projectId, buildInput()),
    onSuccess: () => {
      toast.success('대표 문서를 저장했습니다.');
      setForm(EMPTY_FORM);
      onDone();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="flex flex-col gap-3 rounded-md border border-ink-100 p-3">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div>
          <Label htmlFor="rule-name">화면에 보일 이름</Label>
          <Input id="rule-name" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
        </div>
        <div>
          <Label htmlFor="rule-target-file">대표 파일 선택</Label>
          <Input
            id="rule-target-file"
            placeholder="예: 주간업무보고_양식.xlsx"
            value={form.targetFile}
            onChange={(e) => setForm((f) => ({ ...f, targetFile: e.target.value }))}
          />
        </div>
        <div>
          <Label htmlFor="rule-aliases">다른 이름 (쉼표 구분, 선택)</Label>
          <Input id="rule-aliases" value={form.aliases} onChange={(e) => setForm((f) => ({ ...f, aliases: e.target.value }))} />
        </div>
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-ink-500">필요할 때만 세부 설정</summary>
        <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div>
            <Label htmlFor="rule-patterns">파일 이름 규칙 (쉼표 구분, 선택)</Label>
            <Input
              id="rule-patterns"
              placeholder="예: *주간보고*.xlsx"
              value={form.patterns}
              onChange={(e) => setForm((f) => ({ ...f, patterns: e.target.value }))}
            />
          </div>
          <div>
            <Label>자료 읽는 방법</Label>
            <Select value={form.mode} onValueChange={(v) => setForm((f) => ({ ...f, mode: v as RuleInput['mode'] }))}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MODE_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label>찾는 순서</Label>
            <Select value={String(form.priority)} onValueChange={(v) => setForm((f) => ({ ...f, priority: Number(v) }))}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PRIORITY_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={String(opt.value)}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </details>
      <label className="flex items-center gap-2 text-sm text-ink-600">
        <input type="checkbox" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
        이 대표 문서 사용하기
      </label>
      <div className="flex gap-2">
        <Button disabled={!form.name.trim() || save.isPending} onClick={() => save.mutate()}>
          {editingRule ? '수정 저장' : '대표 문서 저장'}
        </Button>
        {editingRule && (
          <Button variant="ghost" onClick={onCancelEdit}>
            취소
          </Button>
        )}
      </div>
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
  const [editingRule, setEditingRule] = useState<SearchRule | null>(null);

  if (!currentProject) return <NoProjectState />;

  const invalidateRules = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-search-rules', currentProject.id] });
    setEditingRule(null);
  };

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>대표 문서 등록</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <RuleForm
            projectId={currentProject.id}
            editingRule={editingRule}
            onDone={invalidateRules}
            onCancelEdit={() => setEditingRule(null)}
          />
          {isLoading ? (
            <LoadingBlock />
          ) : !rules || rules.length === 0 ? (
            <EmptyState title="등록된 대표 문서가 없습니다." />
          ) : (
            <ul className="flex flex-col gap-2">
              {rules.map((rule) => (
                <li key={rule.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2 text-sm">
                  <div>
                    <p className="font-medium text-ink-800">{rule.name}</p>
                    {rule.targetFile && <p className="text-xs text-ink-400">기준 원본 · {rule.targetFile}</p>}
                    <p className="text-xs text-ink-400">동의어: {rule.aliases.join(', ') || '-'}</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      <Badge variant={rule.active ? 'accent' : 'neutral'}>{rule.active ? '사용 중' : '사용 안 함'}</Badge>
                      <Badge variant="outline">{MODE_OPTIONS.find((m) => m.value === rule.mode)?.label ?? rule.mode}</Badge>
                      <Badge variant="outline">{PRIORITY_OPTIONS.find((p) => p.value === rule.priority)?.label ?? rule.priority}</Badge>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Button size="sm" variant="outline" onClick={() => setEditingRule(rule)}>
                      수정
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => rule.id && deleteRule.mutate(rule.id)}>
                      삭제
                    </Button>
                  </div>
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
