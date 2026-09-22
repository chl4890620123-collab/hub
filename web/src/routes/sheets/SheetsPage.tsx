import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Lock, Table as TableIcon, Upload } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { useCurrentProject, useProjects } from '@/hooks/useProjects';
import { sheetsApi } from '@/api/endpoints/sheets';
import type { SpreadsheetDataRow, SpreadsheetFileRow } from '@/api/types';
import { ApiError, errorMessage } from '@/lib/errors';
import { toast } from '@/stores/toastStore';
import { PasswordPromptDialog } from '@/features/sheets/PasswordPromptDialog';
import { SheetWorkspace } from '@/features/sheets/SheetWorkspace';

function ProjectPicker({
  value,
  onChange,
  projects,
}: {
  value: number | null;
  onChange: (id: number) => void;
  projects: { id: number; name: string }[];
}) {
  return (
    <div>
      <Label>저장할 프로젝트</Label>
      <Select value={value ? String(value) : undefined} onValueChange={(v) => onChange(Number(v))}>
        <SelectTrigger>
          <SelectValue placeholder="프로젝트 선택" />
        </SelectTrigger>
        <SelectContent>
          {projects.map((p) => (
            <SelectItem key={p.id} value={String(p.id)}>
              {p.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <p className="mt-1 text-xs text-ink-400">이 프로젝트 팀원과 함께 보고 편집합니다.</p>
    </div>
  );
}

function CreateSheetCard({ projects, defaultProjectId, onCreated }: {
  projects: { id: number; name: string }[];
  defaultProjectId: number | null;
  onCreated: (projectId: number, id: number) => void;
}) {
  const [projectId, setProjectId] = useState<number | null>(defaultProjectId);
  const [name, setName] = useState('');
  const [columns, setColumns] = useState('');
  const [password, setPassword] = useState('');
  const [hint, setHint] = useState('');
  const [showSecurity, setShowSecurity] = useState(false);

  const create = useMutation({
    mutationFn: () => {
      if (!projectId) throw new Error('저장할 프로젝트를 선택해 주세요.');
      const columnList = columns.split(',').map((s) => s.trim()).filter(Boolean);
      if (columnList.length === 0) throw new Error('열 이름을 하나 이상 입력해 주세요.');
      return sheetsApi.create(projectId, { name, columns: columnList, password: password || null, hint: hint || null });
    },
    onSuccess: (result) => {
      toast.success('자료표를 만들었습니다.');
      setName('');
      setColumns('');
      setPassword('');
      setHint('');
      onCreated(projectId!, result.id);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>새 자료표 만들기</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <ProjectPicker value={projectId} onChange={setProjectId} projects={projects} />
        <div>
          <Label>표 이름</Label>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 거래처 연락처" />
        </div>
        <div>
          <Label>열 이름 (쉼표로 구분)</Label>
          <Input value={columns} onChange={(e) => setColumns(e.target.value)} placeholder="예: 이름, 연락처, 비고" />
        </div>
        <button
          type="button"
          className="self-start text-xs font-medium text-accent-600 hover:underline"
          onClick={() => setShowSecurity((v) => !v)}
        >
          <Lock size={12} className="mr-1 inline" /> 비밀번호로 잠그기 (선택)
        </button>
        {showSecurity && (
          <div className="grid grid-cols-1 gap-3 rounded-md bg-ink-50 p-3 sm:grid-cols-2">
            <div>
              <Label>비밀번호</Label>
              <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="4자 이상" autoComplete="new-password" />
            </div>
            <div>
              <Label>힌트</Label>
              <Input value={hint} onChange={(e) => setHint(e.target.value)} maxLength={300} />
            </div>
            <p className="col-span-2 text-xs text-ink-400">
              힌트는 비밀번호를 모르는 사람에게도 그대로 보이니, 답이 바로 드러나는 문장은 피하세요.
            </p>
          </div>
        )}
        <Button
          className="self-start"
          disabled={!name.trim() || !columns.trim() || create.isPending}
          onClick={() => create.mutate()}
        >
          표 만들기
        </Button>
      </CardContent>
    </Card>
  );
}

function ImportSheetCard({ projects, defaultProjectId, onCreated }: {
  projects: { id: number; name: string }[];
  defaultProjectId: number | null;
  onCreated: (projectId: number, id: number) => void;
}) {
  const [projectId, setProjectId] = useState<number | null>(defaultProjectId);
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [hint, setHint] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  const importSheet = useMutation({
    mutationFn: () => {
      const file = fileRef.current?.files?.[0];
      if (!projectId) throw new Error('저장할 프로젝트를 선택해 주세요.');
      if (!file) throw new Error('가져올 엑셀 파일을 선택해 주세요.');
      return sheetsApi.import(projectId, file, { name: name || undefined, password: password || undefined, hint: hint || undefined });
    },
    onSuccess: (result) => {
      toast.success('엑셀 파일을 가져왔습니다.');
      setName('');
      setPassword('');
      setHint('');
      if (fileRef.current) fileRef.current.value = '';
      onCreated(projectId!, result.id);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>엑셀 파일 가져오기</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="text-xs text-ink-500">.xlsx 파일의 첫 줄을 열 이름으로 사용합니다. 가져온 뒤에도 계속 수정할 수 있습니다.</p>
        <ProjectPicker value={projectId} onChange={setProjectId} projects={projects} />
        <input ref={fileRef} type="file" accept=".xlsx" className="text-sm" />
        <div>
          <Label>표 이름 (선택, 비우면 파일 이름 사용)</Label>
          <Input value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label>비밀번호 (선택)</Label>
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="4자 이상" autoComplete="new-password" />
          </div>
          <div>
            <Label>힌트 (선택)</Label>
            <Input value={hint} onChange={(e) => setHint(e.target.value)} maxLength={300} />
          </div>
        </div>
        <Button className="self-start" disabled={importSheet.isPending} onClick={() => importSheet.mutate()}>
          <Upload size={14} /> 가져오기
        </Button>
      </CardContent>
    </Card>
  );
}

export function SheetsPage() {
  const { currentProject } = useCurrentProject();
  const { data: projects } = useProjects();
  const queryClient = useQueryClient();

  const [opened, setOpened] = useState<{ file: SpreadsheetFileRow; rows: SpreadsheetDataRow[]; password: string | null } | null>(null);
  const [pending, setPending] = useState<{ id: number; hint?: string | null } | null>(null);

  const { data: sheets, isLoading } = useQuery({
    queryKey: ['sheets', currentProject?.id],
    queryFn: () => sheetsApi.list(currentProject!.id),
    enabled: !!currentProject,
  });

  const openSheet = async (id: number, password?: string | null) => {
    try {
      const data = await sheetsApi.open(id, password);
      setOpened({ file: data.file, rows: data.rows, password: password ?? null });
      setPending(null);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'SHEET_LOCKED') {
        setPending({ id, hint: e.hint });
        return;
      }
      toast.error(errorMessage(e));
    }
  };

  const handleCreated = (projectId: number, id: number) => {
    if (currentProject && projectId === currentProject.id) {
      queryClient.invalidateQueries({ queryKey: ['sheets', projectId] });
      void openSheet(id);
    } else {
      toast.info('다른 프로젝트에 저장했습니다. 그 프로젝트를 선택하면 목록에 보입니다.');
    }
  };

  if (!currentProject) {
    return (
      <div>
        <PageHeader title="자료표" description="엑셀처럼 표로 정리하기" />
        <EmptyState title="소속된 프로젝트가 없습니다." description="관리자에게 프로젝트 배정을 요청해 주세요." />
      </div>
    );
  }

  if (opened) {
    return (
      <div>
        <PageHeader title="자료표" description="엑셀처럼 표로 정리하기" />
        <SheetWorkspace
          file={opened.file}
          rows={opened.rows}
          password={opened.password}
          onBack={() => {
            setOpened(null);
            queryClient.invalidateQueries({ queryKey: ['sheets', currentProject.id] });
          }}
          onChanged={(file, rows, password) => setOpened({ file, rows, password })}
          onDeleted={() => {
            setOpened(null);
            queryClient.invalidateQueries({ queryKey: ['sheets', currentProject.id] });
          }}
        />
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="자료표" description="표를 만들어 팀과 함께 채우고, 필요하면 비밀번호로 잠급니다. 엑셀 파일을 그대로 가져오거나 내보낼 수 있습니다." />

      <div className="mb-5 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <CreateSheetCard projects={projects ?? []} defaultProjectId={currentProject.id} onCreated={handleCreated} />
        <ImportSheetCard projects={projects ?? []} defaultProjectId={currentProject.id} onCreated={handleCreated} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>이 프로젝트의 자료표</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <LoadingBlock />
          ) : !sheets || sheets.length === 0 ? (
            <EmptyState title="아직 만든 자료표가 없습니다." description="위에서 새로 만들거나 엑셀 파일을 가져와 보세요." />
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {sheets.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => openSheet(s.id)}
                  className="flex flex-col gap-2 rounded-lg border border-ink-200 p-4 text-left transition-shadow hover:shadow-md dark:bg-ink-100"
                >
                  <div className="flex items-center gap-2 font-semibold text-ink-900">
                    <TableIcon size={16} className="text-accent-600" />
                    {s.name}
                    {s.passwordProtected && <Lock size={13} className="text-amber-600" />}
                  </div>
                  <p className="text-xs text-ink-400">
                    열 {s.columns.length}개 · 행 {s.rowCount}개 · {s.updatedAt.slice(0, 10)} 업데이트
                  </p>
                </button>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <PasswordPromptDialog
        open={!!pending}
        hint={pending?.hint}
        onSubmit={(password) => pending && openSheet(pending.id, password)}
        onCancel={() => setPending(null)}
      />
    </div>
  );
}
