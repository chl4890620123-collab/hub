import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { useGlobalSearchStore } from '@/stores/globalSearchStore';
import { useCurrentProject } from '@/hooks/useProjects';
import { materialsApi } from '@/api/endpoints/materials';
import { MaterialResultList } from '@/features/materials/MaterialResultList';
import { LoadingBlock } from '@/components/ui/spinner';

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

export function CommandPaletteSearch() {
  const isOpen = useGlobalSearchStore((s) => s.isOpen);
  const close = useGlobalSearchStore((s) => s.close);
  const toggle = useGlobalSearchStore((s) => s.toggle);
  const { currentProject } = useCurrentProject();
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebouncedValue(query, 300);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        toggle();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [toggle]);

  useEffect(() => {
    if (!isOpen) setQuery('');
  }, [isOpen]);

  const { data: hits, isFetching } = useQuery({
    queryKey: ['global-search', currentProject?.id, debouncedQuery],
    queryFn: () => materialsApi.search(currentProject!.id, debouncedQuery),
    enabled: isOpen && !!currentProject && debouncedQuery.trim().length > 0,
  });

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && close()}>
      <DialogContent className="top-24 max-h-[70vh] max-w-xl translate-y-0 overflow-y-auto p-0">
        <DialogTitle className="sr-only">전체 검색</DialogTitle>
        <div className="flex items-center gap-2 border-b border-ink-200 px-4 py-3">
          <Search size={16} className="text-ink-400" />
          <Input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="문서, 회의록, 첨부파일, GitHub·Drive·Slack·Notion 자료를 검색하세요..."
            className="h-9 border-0 px-0 focus:ring-0"
          />
        </div>
        <div className="p-4">
          {isFetching ? (
            <LoadingBlock label="검색 중..." />
          ) : hits && hits.length > 0 ? (
            <MaterialResultList hits={hits} />
          ) : debouncedQuery.trim().length > 0 ? (
            <p className="py-8 text-center text-sm text-ink-400">검색 결과가 없습니다.</p>
          ) : (
            <p className="py-8 text-center text-sm text-ink-400">검색어를 입력하세요.</p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
