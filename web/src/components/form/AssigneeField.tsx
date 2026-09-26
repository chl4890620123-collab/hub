import { useQuery } from '@tanstack/react-query';
import { projectsApi } from '@/api/endpoints/projects';
import { Label } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

/** Paired with a due-date input on every content-entry form that can also create a follow-up todo -
 * picking someone here pre-assigns that todo instead of leaving it unassigned. */
export function AssigneeField({
  projectId,
  value,
  onChange,
  className,
}: {
  projectId: number;
  value: string;
  onChange: (value: string) => void;
  className?: string;
}) {
  const { data: members } = useQuery({ queryKey: ['project-members', projectId], queryFn: () => projectsApi.members(projectId) });

  return (
    <div className={className}>
      <Label>후속 할 일 담당자 (선택)</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="w-40">
          <SelectValue placeholder="지정 안 함" />
        </SelectTrigger>
        <SelectContent>
          {members && members.length === 0 ? (
            <SelectItem value="__no-members" disabled>
              배정 가능한 팀원이 없습니다
            </SelectItem>
          ) : (
            members?.map((m) => (
              <SelectItem key={m.id} value={String(m.id)}>
                {m.displayName} · @{m.loginId}
              </SelectItem>
            ))
          )}
        </SelectContent>
      </Select>
    </div>
  );
}
