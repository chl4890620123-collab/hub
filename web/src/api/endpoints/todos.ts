import { apiDelete, apiGet, apiPatch, apiPost } from '@/api/client';
import type { EvidenceView, TaskStatus, TodoItem } from '@/api/types';

export const todosApi = {
  month: (projectId: number, year: number, month: number) =>
    apiGet<TodoItem[]>(`/api/projects/${projectId}/todos?year=${year}&month=${month}`),
  undated: (projectId: number) => apiGet<TodoItem[]>(`/api/projects/${projectId}/todos/undated`),
  dueThrough: (projectId: number, date: string) =>
    apiGet<TodoItem[]>(`/api/projects/${projectId}/todos/due-through?date=${encodeURIComponent(date)}`),
  trash: (projectId: number) => apiGet<TodoItem[]>(`/api/projects/${projectId}/todos/trash`),
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
  softDelete: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/delete`),
  restore: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/restore`),
  permanentDelete: (todoId: number) => apiDelete<{ status: string }>(`/api/todos/${todoId}/permanent`),
  editCandidate: (todoId: number, title: string, description: string) =>
    apiPatch<{ status: string }>(`/api/todos/${todoId}`, { title, description }),
  updateStatus: (todoId: number, status: TaskStatus) =>
    apiPatch<{ status: TaskStatus }>(`/api/todos/${todoId}/status`, { status }),

  requestCompletion: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/request-completion`),
  approveCompletion: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/approve-completion`),
  rejectCompletion: (todoId: number, reason?: string) =>
    apiPost<{ status: string }>(`/api/todos/${todoId}/reject-completion`, { reason }),
  requestHelp: (todoId: number, note: string) => apiPost<{ status: string }>(`/api/todos/${todoId}/request-help`, { note }),
  resolveHelp: (todoId: number) => apiPost<{ status: string }>(`/api/todos/${todoId}/resolve-help`),
};
