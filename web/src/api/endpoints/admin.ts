import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '@/api/client';
import type {
  AuditLogRow,
  GlobalRole,
  MaterialHit,
  ProjectMember,
  ReassignmentRequest,
  RevisionRow,
  RuleInput,
  SearchRule,
  SensitiveTerm,
  SignupApplication,
  User,
} from '@/api/types';

export const adminUsersApi = {
  list: () => apiGet<User[]>('/api/admin/users'),
  setStatus: (userId: number, status: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN', reason?: string) =>
    apiPatch<{ status: string; reassignmentCount: number }>(`/api/admin/users/${userId}/status`, { status, reason }),
  setRole: (userId: number, role: GlobalRole) =>
    apiPatch<{ status: string }>(`/api/admin/users/${userId}/role`, { role }),
  resetPassword: (userId: number, temporaryPassword: string) =>
    apiPost<{ status: string; mustChangePassword: boolean }>(`/api/admin/users/${userId}/reset-password`, { temporaryPassword }),
};

export const adminSignupApi = {
  list: () => apiGet<SignupApplication[]>('/api/admin/signup-applications'),
  approve: (userId: number, projectId?: number) =>
    apiPost<{ status: string; projectAssigned: boolean }>(`/api/admin/signup-applications/${userId}/approve`, { projectId }),
  reject: (userId: number, reason?: string) =>
    apiPost<{ status: string }>(`/api/admin/signup-applications/${userId}/reject`, { reason }),
};

export const adminProjectApi = {
  addMember: (projectId: number, userId: number) =>
    apiPut<{ status: string }>(`/api/admin/projects/${projectId}/members`, { userId }),
  removeMember: (projectId: number, userId: number) =>
    apiDelete<{ status: string; reassignmentCount: number }>(`/api/admin/projects/${projectId}/members/${userId}`),
  moveMember: (fromProjectId: number, userId: number, toProjectId: number) =>
    apiPost<{ status: string; reassignmentCount: number }>(`/api/admin/projects/${fromProjectId}/members/move`, {
      userId,
      toProjectId,
    }),
  setConfirmPermission: (projectId: number, userId: number, granted: boolean) =>
    apiPut<{ status: string }>(`/api/admin/projects/${projectId}/confirm-permission`, { userId, granted }),
  members: (projectId: number) => apiGet<ProjectMember[]>(`/api/projects/${projectId}/members`),
};

export const adminReassignmentApi = {
  pending: (projectId: number) => apiGet<ReassignmentRequest[]>(`/api/admin/projects/${projectId}/reassignments`),
  resolve: (requestId: number, newAssigneeId: number) =>
    apiPost<{ status: string }>(`/api/admin/reassignments/${requestId}/resolve`, { newAssigneeId }),
  bulkResolve: (projectId: number, requestIds: number[], newAssigneeId: number) =>
    apiPost<{ results: Record<number, string> }>(`/api/admin/projects/${projectId}/reassignments/bulk-resolve`, {
      requestIds,
      newAssigneeId,
    }),
};

export const adminSecurityApi = {
  sensitiveTerms: () => apiGet<SensitiveTerm[]>('/api/admin/sensitive-terms'),
  addSensitiveTerm: (term: string) => apiPost<{ status: string }>('/api/admin/sensitive-terms', { term }),
  removeSensitiveTerm: (id: number) => apiDelete<{ status: string }>(`/api/admin/sensitive-terms/${id}`),
};

export const adminSearchRuleApi = {
  list: (projectId: number) => apiGet<SearchRule[]>(`/api/admin/projects/${projectId}/search/rules`),
  create: (projectId: number, input: RuleInput) => apiPost<SearchRule>(`/api/admin/projects/${projectId}/search/rules`, input),
  update: (projectId: number, ruleId: number, input: RuleInput) =>
    apiPut<SearchRule>(`/api/admin/projects/${projectId}/search/rules/${ruleId}`, input),
  delete: (projectId: number, ruleId: number) =>
    apiDelete<{ status: string }>(`/api/admin/projects/${projectId}/search/rules/${ruleId}`),
  test: (projectId: number, q: string) =>
    apiGet<{ query: string; matchedRule: SearchRule | null; results: MaterialHit[] }>(
      `/api/admin/projects/${projectId}/search/test?q=${encodeURIComponent(q)}`,
    ),
  embeddingStatus: (projectId: number) =>
    apiGet<Record<string, unknown>>(`/api/admin/projects/${projectId}/search/embedding-status`),
  embeddingRetry: (projectId: number) =>
    apiPost<{ reindexed: number; status: string }>(`/api/admin/projects/${projectId}/search/embedding-retry`),
};

export const adminHistoryApi = {
  revisions: (projectId: number) => apiGet<RevisionRow[]>(`/api/projects/${projectId}/revisions`),
  audit: () => apiGet<AuditLogRow[]>('/api/admin/audit'),
};
