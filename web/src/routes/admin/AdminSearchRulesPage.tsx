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

function statusNumber(status: Record<string, unknown> | undefined, ...keys: string[]): number {
  if (!status) return 0;
  for (const key of keys) {
    const value = status[key];
    if (typeof value === 'number') return value;
    if (typeof value === 'string' && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }
  return 0;
}

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
          <Label htmlFor="rule-target-file">기준이 될 파일 이름</Label>
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
    onSuccess: (result) => {
      toast.success(`${result.reindexed}건의 검색 데이터를 다시 만들었습니다.`);
      queryClient.invalidateQueries({ queryKey: ['embedding-status', currentProject?.id] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const deleteRule = useMutation({
    mutationFn: (ruleId: number) => adminSearchRuleApi.delete(currentProject!.id, ruleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-search-rules', currentProject?.id] }),
    onError: (error) => toast.error(errorMessage(error)),
  });
  const [editingRule, setEditingRule] = useState<SearchRule | null>(null);

  if (!currentProject) return <NoProjectState />;

  const invalidateRules = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-search-rules', currentProject.id] });
    setEditingRule(null);
  };
  const totalVersions = statusNumber(embeddingStatus, 'total_versions', 'TOTAL_VERSIONS', 'indexed');
  const readyVersions = statusNumber(embeddingStatus, 'ready_versions', 'READY_VERSIONS', 'indexed');
  const failedVersions = statusNumber(embeddingStatus, 'failed_versions', 'FAILED_VERSIONS');
  const pendingVersions = statusNumber(embeddingStatus, 'pending_versions', 'PENDING_VERSIONS', 'pending');

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>대표 문서 등록</CardTitle>
          <p className="text-xs text-ink-400">자주 쓰는 기준 파일과 다른 이름을 등록하면 파일명이 조금 달라도 관련 자료를 더 쉽게 찾습니다.</p>
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
                    <p className="text-xs text-ink-400">다른 이름: {rule.aliases.join(', ') || '등록 없음'}</p>
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
          <CardTitle>검색 준비 상태</CardTitle>
          <p className="text-xs text-ink-400">등록된 문서가 내용 검색에 사용할 수 있도록 준비됐는지 확인합니다.</p>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {totalVersions === 0 ? (
            <p className="text-sm text-ink-500">아직 검색용으로 준비할 문서가 없습니다.</p>
          ) : (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge variant={failedVersions > 0 ? 'warning' : pendingVersions > 0 ? 'neutral' : 'accent'}>
                {readyVersions}/{totalVersions}개 준비 완료
              </Badge>
              {pendingVersions > 0 && <span className="text-ink-500">준비 중 {pendingVersions}개</span>}
              {failedVersions > 0 && <span className="text-amber-700">다시 처리 필요 {failedVersions}개</span>}
            </div>
          )}
          <Button size="sm" disabled={retryEmbedding.isPending} onClick={() => retryEmbedding.mutate()} className="self-start">
            {retryEmbedding.isPending ? '검색 데이터 다시 만드는 중...' : '검색 데이터 다시 만들기'}
          </Button>
          <details className="text-xs text-ink-400">
            <summary className="cursor-pointer">기술 정보 보기</summary>
            <pre className="mt-2 overflow-x-auto rounded-md bg-ink-50 p-3 text-ink-600">
              {JSON.stringify(embeddingStatus ?? {}, null, 2)}
            </pre>
          </details>
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
                <p className="mb-2 text-xs text-accent-600">적용된 대표 문서 설정: {testResult.data.matchedRule.name}</p>
              )}
              <MaterialResultList hits={testResult.data.results} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
