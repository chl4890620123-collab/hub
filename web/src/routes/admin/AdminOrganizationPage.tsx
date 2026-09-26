import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { LoadingBlock } from '@/components/ui/spinner';
import { adminOrganizationApi } from '@/api/endpoints/admin';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

export function AdminOrganizationPage() {
  const queryClient = useQueryClient();
  const [departmentName, setDepartmentName] = useState('');
  const [teamName, setTeamName] = useState('');
  const [teamDepartmentId, setTeamDepartmentId] = useState('');
  const { data, isLoading } = useQuery({ queryKey: ['admin-organization'], queryFn: adminOrganizationApi.get });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin-organization'] });

  const createDepartment = useMutation({
    mutationFn: () => adminOrganizationApi.createDepartment(departmentName),
    onSuccess: () => { setDepartmentName(''); refresh(); toast.success('부서를 만들었습니다.'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const createTeam = useMutation({
    mutationFn: () => adminOrganizationApi.createTeam(Number(teamDepartmentId), teamName),
    onSuccess: () => { setTeamName(''); refresh(); toast.success('팀을 만들었습니다.'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const renameDepartment = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => adminOrganizationApi.renameDepartment(id, name),
    onSuccess: refresh,
    onError: (e) => toast.error(errorMessage(e)),
  });
  const renameTeam = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => adminOrganizationApi.renameTeam(id, name),
    onSuccess: refresh,
    onError: (e) => toast.error(errorMessage(e)),
  });
  const departmentActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => adminOrganizationApi.setDepartmentActive(id, active),
    onSuccess: refresh,
    onError: (e) => toast.error(errorMessage(e)),
  });
  const teamActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => adminOrganizationApi.setTeamActive(id, active),
    onSuccess: refresh,
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (isLoading) return <LoadingBlock />;
  const departments = data?.departments ?? [];
  const teams = data?.teams ?? [];

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader><CardTitle>부서 관리</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex gap-2"><Input value={departmentName} onChange={(e) => setDepartmentName(e.target.value)} placeholder="새 부서 이름" /><Button disabled={!departmentName.trim() || createDepartment.isPending} onClick={() => createDepartment.mutate()}>추가</Button></div>
          <ul className="flex flex-col gap-2">
            {departments.map((department) => (
              <li key={department.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2">
                <div><p className="text-sm font-medium">{department.name}</p><Badge variant={department.active ? 'accent' : 'neutral'}>{department.active ? '사용 중' : '비활성'}</Badge></div>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => { const name=window.prompt('부서 이름', department.name); if (name?.trim()) renameDepartment.mutate({id:department.id,name:name.trim()}); }}>이름 수정</Button>
                  <Button size="sm" variant="ghost" onClick={() => departmentActive.mutate({id:department.id,active:!department.active})}>{department.active ? '비활성화' : '활성화'}</Button>
                </div>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>팀 관리</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div>
            <Label>부서</Label>
            <Select value={teamDepartmentId} onValueChange={setTeamDepartmentId}>
              <SelectTrigger><SelectValue placeholder="부서 선택" /></SelectTrigger>
              <SelectContent>{departments.filter((d)=>d.active).map((d)=><SelectItem key={d.id} value={String(d.id)}>{d.name}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="flex gap-2"><Input value={teamName} onChange={(e) => setTeamName(e.target.value)} placeholder="새 팀 이름" /><Button disabled={!teamDepartmentId || !teamName.trim() || createTeam.isPending} onClick={() => createTeam.mutate()}>추가</Button></div>
          <ul className="flex flex-col gap-2">
            {teams.map((team) => {
              const department=departments.find((d)=>d.id===team.departmentId);
              return <li key={team.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2">
                <div><p className="text-sm font-medium">{team.name}</p><p className="text-xs text-ink-400">{department?.name ?? '부서 없음'}</p><Badge variant={team.active ? 'accent' : 'neutral'}>{team.active ? '사용 중' : '비활성'}</Badge></div>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => { const name=window.prompt('팀 이름', team.name); if (name?.trim()) renameTeam.mutate({id:team.id,name:name.trim()}); }}>이름 수정</Button>
                  <Button size="sm" variant="ghost" disabled={!department?.active && !team.active} onClick={() => teamActive.mutate({id:team.id,active:!team.active})}>{team.active ? '비활성화' : '활성화'}</Button>
                </div>
              </li>;
            })}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
