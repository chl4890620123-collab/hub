import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';

export function BulkSelectionBar({
  count,
  onClear,
  children,
}: {
  count: number;
  onClear: () => void;
  children: ReactNode;
}) {
  if (count === 0) return null;
  return (
    <div className="mb-3 flex items-center gap-3 rounded-md border border-accent-200 bg-accent-50 px-3 py-2 text-sm">
      <span className="font-medium text-accent-700">{count}개 선택됨</span>
      <div className="flex items-center gap-2">{children}</div>
      <Button variant="ghost" size="sm" className="ml-auto" onClick={onClear}>
        선택 해제
      </Button>
    </div>
  );
}
