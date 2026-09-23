import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ArrowLeft, Download, Lock, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { sheetsApi, type SecurityInput } from '@/api/endpoints/sheets';
import type { SpreadsheetDataRow, SpreadsheetFileRow } from '@/api/types';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';
import { SecurityDialog } from './SecurityDialog';

/** The opened 자료표: an editable grid, no formulas - add/edit/delete on rows and columns, matching
 * what was actually asked for (a CRUD table, not a spreadsheet engine). */
export function SheetWorkspace({
  file,
  rows,
  password,
  onBack,
  onChanged,
  onDeleted,
}: {
  file: SpreadsheetFileRow;
  rows: SpreadsheetDataRow[];
  password: string | null;
  onBack: () => void;
  onChanged: (file: SpreadsheetFileRow, rows: SpreadsheetDataRow[], password: string | null) => void;
  onDeleted: () => void;
}) {
  const [securityOpen, setSecurityOpen] = useState(false);

  const refresh = async (nextPassword = password) => {
    const data = await sheetsApi.open(file.id, nextPassword);
    onChanged(data.file, data.rows, nextPassword);
  };

  const addRow = useMutation({
    mutationFn: () => {
      const cells: Record<string, string> = {};
      file.columns.forEach((c) => (cells[c.key] = ''));
      return sheetsApi.addRow(file.id, cells, password);
    },
    onSuccess: (row) => onChanged(file, [...rows, row], password),
    onError: (e) => toast.error(errorMessage(e)),
  });

  const saveRow = async (row: SpreadsheetDataRow, cells: Record<string, string>) => {
    try {
      await sheetsApi.updateRow(file.id, row.id, cells, password);
      onChanged(file, rows.map((r) => (r.id === row.id ? { ...r, cells } : r)), password);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const removeRow = async (row: SpreadsheetDataRow) => {
    if (!window.confirm('이 행을 삭제할까요?')) return;
    try {
      await sheetsApi.removeRow(file.id, row.id, password);
      onChanged(file, rows.filter((r) => r.id !== row.id), password);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const renameColumn = async (key: string, label: string) => {
    const columns = file.columns.map((c) => ({ key: c.key, label: c.key === key ? label : c.label }));
    try {
      await sheetsApi.setColumns(file.id, columns, password);
      await refresh();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const removeColumn = async (key: string, label: string) => {
    if (file.columns.length <= 1) {
      toast.error('열은 최소 1개는 있어야 합니다.');
      return;
    }
    if (!window.confirm(`'${label}' 열을 삭제할까요?\n이 열에 입력된 값도 함께 사라집니다.`)) return;
    const columns = file.columns.filter((c) => c.key !== key).map((c) => ({ key: c.key, label: c.label }));
    try {
      await sheetsApi.setColumns(file.id, columns, password);
      await refresh();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const addColumn = async () => {
    const label = window.prompt('새 열 이름');
    if (!label) return;
    const columns = [...file.columns.map((c) => ({ key: c.key, label: c.label })), { key: '', label }];
    try {
      await sheetsApi.setColumns(file.id, columns, password);
      await refresh();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const handleSecurity = async (input: SecurityInput) => {
    try {
      await sheetsApi.setSecurity(file.id, input);
      toast.success(input.newPassword ? '비밀번호를 설정했습니다.' : '비밀번호 잠금을 해제했습니다.');
      setSecurityOpen(false);
      await refresh(input.newPassword ?? null);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const handleExport = async () => {
    try {
      await sheetsApi.export(file.id, file.name, password);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`'${file.name}' 표를 삭제할까요?\n삭제하면 되돌릴 수 없습니다.`)) return;
    try {
      await sheetsApi.remove(file.id, password);
      toast.success('자료표를 삭제했습니다.');
      onDeleted();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={onBack}>
            <ArrowLeft size={14} /> 목록으로
          </Button>
          <h2 className="text-lg font-semibold text-ink-900">{file.name}</h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={addColumn}>
            <Plus size={14} /> 열 추가
          </Button>
          <Button variant="outline" size="sm" onClick={() => setSecurityOpen(true)}>
            <Lock size={14} /> 보안 설정
          </Button>
          <Button variant="outline" size="sm" onClick={handleExport}>
            <Download size={14} /> 엑셀로 내보내기
          </Button>
          <Button variant="danger" size="sm" onClick={handleDelete}>
            <Trash2 size={14} /> 표 삭제
          </Button>
        </div>
      </div>

      <div className="overflow-x-auto rounded-lg border border-ink-200 bg-white dark:bg-ink-100">
        <table className="w-full min-w-max border-collapse text-sm">
          <thead>
            <tr>
              {file.columns.map((col) => (
                <th key={col.key} className="border-b border-r border-ink-100 bg-ink-50 p-0">
                  <div className="flex items-center gap-1 px-1">
                    <input
                      className="min-w-0 flex-1 truncate bg-transparent px-1 py-2 text-left text-xs font-semibold text-ink-700 outline-none"
                      defaultValue={col.label}
                      key={col.label}
                      onBlur={(e) => {
                        const next = e.target.value.trim();
                        if (next && next !== col.label) renameColumn(col.key, next);
                        else e.target.value = col.label;
                      }}
                    />
                    <button
                      type="button"
                      className="shrink-0 rounded p-1 text-ink-400 hover:bg-red-50 hover:text-red-600"
                      title="이 열 삭제"
                      onClick={() => removeColumn(col.key, col.label)}
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                </th>
              ))}
              <th className="w-9 border-b border-ink-100 bg-ink-50" />
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                {file.columns.map((col) => (
                  <td key={col.key} className="border-b border-r border-ink-100 p-0">
                    <input
                      className="w-full min-w-[140px] bg-transparent px-2.5 py-2 text-sm text-ink-800 outline-none focus:bg-accent-50"
                      defaultValue={row.cells[col.key] ?? ''}
                      key={`${row.id}-${col.key}-${row.cells[col.key] ?? ''}`}
                      onBlur={(e) => {
                        const value = e.target.value;
                        if (value !== (row.cells[col.key] ?? '')) saveRow(row, { ...row.cells, [col.key]: value });
                      }}
                    />
                  </td>
                ))}
                <td className="border-b border-ink-100 text-center">
                  <button
                    type="button"
                    className="rounded p-1 text-ink-400 hover:bg-red-50 hover:text-red-600"
                    title="이 행 삭제"
                    onClick={() => removeRow(row)}
                  >
                    <Trash2 size={12} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Button variant="ghost" size="sm" className="mt-3" onClick={() => addRow.mutate()} disabled={addRow.isPending}>
        <Plus size={14} /> 행 추가
      </Button>

      <SecurityDialog
        open={securityOpen}
        hasPassword={file.passwordProtected}
        onSubmit={handleSecurity}
        onCancel={() => setSecurityOpen(false)}
      />
    </div>
  );
}
