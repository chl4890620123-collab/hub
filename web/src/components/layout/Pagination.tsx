import { useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

/** Client-side pagination over an already-fetched array. Large lists (documents, audit log,
 * revision history) get slower to render the more data accumulates, so cap what's on screen
 * at once instead of dumping every row into the DOM. */
export function usePagination<T>(items: T[], defaultPageSize = 20) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(defaultPageSize);

  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const clampedPage = Math.min(page, totalPages);
  const pageItems = useMemo(
    () => items.slice((clampedPage - 1) * pageSize, clampedPage * pageSize),
    [items, clampedPage, pageSize],
  );

  return {
    pageItems,
    page: clampedPage,
    totalPages,
    pageSize,
    setPage,
    setPageSize: (size: number) => {
      setPageSize(size);
      setPage(1);
    },
  };
}

export function PaginationControls({
  page,
  totalPages,
  pageSize,
  onPageChange,
  onPageSizeChange,
  totalCount,
}: {
  page: number;
  totalPages: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  totalCount: number;
}) {
  if (totalCount <= PAGE_SIZE_OPTIONS[0]) return null;

  return (
    <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-ink-500">
      <div className="flex items-center gap-1.5">
        <span>한 번에</span>
        <Select value={String(pageSize)} onValueChange={(v) => onPageSizeChange(Number(v))}>
          <SelectTrigger className="h-7 w-20">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PAGE_SIZE_OPTIONS.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size}개
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <span>씩 보기 · 총 {totalCount}건</span>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="icon" className="h-7 w-7" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft size={13} />
        </Button>
        <span>
          {page} / {totalPages}
        </span>
        <Button
          variant="outline"
          size="icon"
          className="h-7 w-7"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          <ChevronRight size={13} />
        </Button>
      </div>
    </div>
  );
}
