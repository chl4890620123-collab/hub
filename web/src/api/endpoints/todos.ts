import { apiGet, apiPatch, apiPost } from '@/api/client';
import type { EvidenceView, TaskStatus, TodoItem } from '@/api/types';

export const todosApi = {
  month: (projectId: number, year: number, month: number) =>
    apiGet<TodoItem[]>(`/api/projects/${projectId}/todos?year=${year}&month=${month}`),
  undated: (projectId: number) => apiGet<TodoItem[]>(`/api/projects/${projectId}/todos/undated`),
  pendingReview: (projectId: number) => apiGet<TodoItem[]>(`/api/projects/${projectId}/review/todos`),
  evidence: (todoId: number) => apiGet<EvidenceView[]>(`/api/todos/${todoId}/evidence`),

  confirm: (todoId: number, assigneeId: number | null, dueDate: string | null) =>
    apiPost<{ status: string }>(`/api/todos/${todoId}/confirm`, { assigneeId, dueDate }),
  bulkConfirm: (projectId: number, todoIds: number[], assigneeId: number | null, dueDate: string | null) =>
    apiPost<{ results: Record<number, string> }>(`/api/projects/${projectId}/review/todos/bulk-confirm`, {
      todoIds,
      assigneeId,
      dueDate,
    }),
  reject: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/reject`),
  mergeDuplicate: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/merge-duplicate`),
  editCandidate: (todoId: number, title: string, description: string) =>
    apiPatch<{ status: string }>(`/api/todos/${todoId}`, { title, description }),
  updateStatus: (todoId: number, status: TaskStatus) =>
    apiPatch<{ status: TaskStatus }>(`/api/todos/${todoId}/status`, { status }),
};
