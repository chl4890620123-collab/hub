import { apiGet, apiPost, apiPut } from '@/api/client';
import type { Project, ProjectMember } from '@/api/types';

/** Raw shape of GET /api/projects/{id}/members - a JdbcTemplate Map<String,Object>, so Jackson
 * serializes the SQL column aliases as-is (snake_case) instead of the camelCase every other
 * endpoint uses. Thymeleaf reads this same endpoint through its own snake_case value() helper, so
 * the column names can't just be renamed server-side without breaking it - translate here instead. */
interface RawProjectMember {
  user_id: number;
  display_name: string;
  login_id: string;
  project_role?: string;
  can_confirm_todos: boolean;
}

function toProjectMember(row: RawProjectMember): ProjectMember {
  return {
    id: row.user_id,
    displayName: row.display_name,
    loginId: row.login_id,
    jobTitle: null,
    projectRole: row.project_role,
    canConfirm: row.can_confirm_todos,
  };
}

export const projectsApi = {
  list: () => apiGet<Project[]>('/api/projects'),
  create: (name: string, description?: string) => apiPost<Project>('/api/projects', { name, description }),
  rename: (projectId: number, name: string, description?: string) =>
    apiPut<{ status: string }>(`/api/projects/${projectId}`, { name, description }),
  members: (projectId: number) =>
    apiGet<RawProjectMember[]>(`/api/projects/${projectId}/members`).then((rows) => rows.map(toProjectMember)),
};
