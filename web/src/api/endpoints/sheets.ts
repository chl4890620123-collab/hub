import { apiDownload, apiFetch, apiGet, apiUpload } from '@/api/client';
import type { SpreadsheetColumn, SpreadsheetDataRow, SpreadsheetFileRow, SpreadsheetUnlocked } from '@/api/types';

const PASSWORD_HEADER = 'X-Sheet-Password';
const pwHeaders = (password?: string | null): Record<string, string> =>
  password ? { [PASSWORD_HEADER]: password } : {};

export interface CreateSheetInput {
  name: string;
  columns: string[];
  password?: string | null;
  hint?: string | null;
}

export interface ImportSheetInput {
  name?: string;
  password?: string | null;
  hint?: string | null;
}

export interface SecurityInput {
  currentPassword?: string | null;
  newPassword?: string | null;
  hint?: string | null;
}

export const sheetsApi = {
  list: (projectId: number) => apiGet<SpreadsheetFileRow[]>(`/api/projects/${projectId}/sheets`),

  create: (projectId: number, input: CreateSheetInput) =>
    apiFetch<{ id: number }>(`/api/projects/${projectId}/sheets`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  import: (projectId: number, file: File, input: ImportSheetInput) => {
    const form = new FormData();
    form.append('file', file);
    const params = new URLSearchParams();
    if (input.name) params.set('name', input.name);
    if (input.password) params.set('password', input.password);
    if (input.hint) params.set('hint', input.hint);
    const query = params.toString();
    return apiUpload<{ id: number }>(`/api/projects/${projectId}/sheets/import${query ? `?${query}` : ''}`, form);
  },

  open: (id: number, password?: string | null) =>
    apiFetch<SpreadsheetUnlocked>(`/api/sheets/${id}`, { headers: pwHeaders(password) }),

  rename: (id: number, name: string, password?: string | null) =>
    apiFetch<{ status: string }>(`/api/sheets/${id}`, { method: 'PUT', body: JSON.stringify({ name, password }) }),

  setColumns: (id: number, columns: SpreadsheetColumn[], password?: string | null) =>
    apiFetch<{ status: string }>(`/api/sheets/${id}/columns`, {
      method: 'PUT',
      body: JSON.stringify({ columns, password }),
    }),

  setSecurity: (id: number, input: SecurityInput) =>
    apiFetch<{ status: string }>(`/api/sheets/${id}/security`, { method: 'PUT', body: JSON.stringify(input) }),

  remove: (id: number, password?: string | null) =>
    apiFetch<{ status: string }>(`/api/sheets/${id}`, { method: 'DELETE', headers: pwHeaders(password) }),

  addRow: (id: number, cells: Record<string, string>, password?: string | null) =>
    apiFetch<SpreadsheetDataRow>(`/api/sheets/${id}/rows`, {
      method: 'POST',
      body: JSON.stringify({ cells }),
      headers: pwHeaders(password),
    }),

  updateRow: (id: number, rowId: number, cells: Record<string, string>, password?: string | null) =>
    apiFetch<{ status: string }>(`/api/sheets/${id}/rows/${rowId}`, {
      method: 'PUT',
      body: JSON.stringify({ cells }),
      headers: pwHeaders(password),
    }),

  removeRow: (id: number, rowId: number, password?: string | null) =>
    apiFetch<{ status: string }>(`/api/sheets/${id}/rows/${rowId}`, {
      method: 'DELETE',
      headers: pwHeaders(password),
    }),

  export: (id: number, name: string, password?: string | null) =>
    apiDownload(`/api/sheets/${id}/export`, pwHeaders(password)).then(({ blob }) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${name}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }),
};
