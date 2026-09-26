import { useEffect, useState } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Search as SearchIcon } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { LoadingBlock } from '@/components/ui/spinner';
import { MaterialResultList } from '@/features/materials/MaterialResultList';
import { useCurrentProject } from '@/hooks/useProjects';
import { useCurrentUser } from '@/hooks/useAuth';
import { materialsApi } from '@/api/endpoints/materials';
import { useLocalStorage } from '@/hooks/useLocalStorage';

const MAX_RECENT = 8;
const SEARCH_PAGE_SIZE = 30;
const SOURCE_FILTERS = [
  { value: 'ALL', label: '전체 자료' },
  { value: 'HUB', label: 'Hub 문서·주고받은 파일' },
  { value: 'EXTERNAL', label: 'GitHub·Google Drive·Slack·Notion' },
] as const;

export function SearchPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [sourceFilter, setSourceFilter] = useState<(typeof SOURCE_FILTERS)[number]['value']>('ALL');
  const [recentQueries, setRecentQueries] = useLocalStorage<string[]>(`hub.recent-searches.${user?.id ?? 'anon'}`, []);

  const storageKey = `hub.last-material-search.${user?.id ?? 'anon'}.${currentProject?.id ?? 'none'}`;
  useEffect(() => {
    if (!currentProject) return;
    try {
      const previous = window.sessionStorage.getItem(storageKey) ?? '';
      setQuery(previous);
      setSubmittedQuery(previous);
    } catch {
      setQuery('');
      setSubmittedQuery('');
    }
  }, [currentProject?.id, storageKey]);

  const search = useInfiniteQuery({
    queryKey: ['material-search', currentProject?.id, submittedQuery],
    queryFn: ({ pageParam }) => materialsApi.search(currentProject!.id, submittedQuery, pageParam),
    initialPageParam: 0,
    enabled: !!currentProject && !!submittedQuery,
    getNextPageParam: (lastPage, pages) => {
      const primaryCount = lastPage.filter((hit) => hit.sourceType !== 'ATTACHMENT').length;
      if (primaryCount < SEARCH_PAGE_SIZE) return undefined;
      return pages.flat().filter((hit) => hit.sourceType !== 'ATTACHMENT').length;
    },
    staleTime: 60_000,
  });

  const { data: topSearches } = useQuery({
    queryKey: ['top-searches', currentProject?.id, user?.id],
    queryFn: () => materialsApi.topSearches(currentProject!.id),
    enabled: !!currentProject,
  });

  if (!currentProject) return <NoProjectState />;

  const hits = search.data?.pages.flat() ?? [];
  const filteredHits = hits.filter((hit) => {
    if (sourceFilter === 'ALL') return true;
    if (sourceFilter === 'HUB') return hit.sourceType === 'HUB' || hit.sourceType === 'ATTACHMENT';
    return hit.sourceType !== 'HUB' && hit.sourceType !== 'ATTACHMENT';
  });

  function runSearch(q: string) {
    const trimmed = q.trim();
    if (!trimmed) return;
    setSubmittedQuery(trimmed);
    try {
      window.sessionStorage.setItem(storageKey, trimmed);
    } catch {
      // Search still works when session storage is unavailable.
    }
    setRecentQueries([trimmed, ...recentQueries.filter((r) => r !== trimmed)].slice(0, MAX_RECENT));
  }

  return (
    <div>
      <PageHeader
        title="자료 찾기"
        description="현재 프로젝트의 사용자 문서, 볼 수 있는 주고받은 파일과 GitHub·Google Drive·Slack·Notion 자료를 한 번에 검색합니다."
      />
      <p className="mb-3 text-xs text-ink-500">
        회의 STT 내부 기록은 검색 결과에 표시하지 않습니다. 주고받은 파일은 현재 사용자가 볼 권한이 있는 파일만 표시됩니다.
      </p>

      <form className="mb-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); runSearch(query); }}>
        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="예: 계약서, 전달받은 파일 이름, API 변경" className="max-w-lg" />
        <Button type="submit"><SearchIcon size={14} /> 검색</Button>
      </form>

      <div className="mb-5 flex flex-wrap gap-1.5">
        {SOURCE_FILTERS.map((filter) => (
          <button key={filter.value} type="button" onClick={() => setSourceFilter(filter.value)}
            className={'rounded-full px-3 py-1.5 text-xs font-medium ' + (sourceFilter === filter.value ? 'bg-accent-600 text-white' : 'bg-ink-100 text-ink-600 hover:bg-ink-200')}>
            {filter.label}
          </button>
        ))}
      </div>

      {(recentQueries.length > 0 || (topSearches && topSearches.length > 0)) && (
        <div className="mb-5 flex flex-wrap gap-4 text-sm">
          {recentQueries.length > 0 && <div><span className="mr-2 text-ink-400">최근 검색</span>{recentQueries.map((q) => (
            <button key={q} onClick={() => { setQuery(q); runSearch(q); }} className="mr-1.5 rounded-full bg-ink-100 px-2.5 py-1 text-xs text-ink-600 hover:bg-ink-200">{q}</button>
          ))}</div>}
          {topSearches && topSearches.length > 0 && <div><span className="mr-2 text-ink-400">내 자주 찾은 검색</span>{topSearches.map((t) => (
            <button key={t.query_text} onClick={() => { setQuery(t.query_text); runSearch(t.query_text); }} className="mr-1.5 rounded-full bg-accent-50 px-2.5 py-1 text-xs text-accent-700 hover:bg-accent-100">{t.query_text}</button>
          ))}</div>}
        </div>
      )}

      {search.isFetching && hits.length === 0 ? <LoadingBlock label="검색 중..." /> : submittedQuery ? (
        <>
          <MaterialResultList hits={filteredHits} emptyLabel="선택한 범위에서 검색 결과가 없습니다." />
          {search.hasNextPage && <div className="mt-3 flex justify-center">
            <Button variant="outline" disabled={search.isFetchingNextPage} onClick={() => search.fetchNextPage()}>
              {search.isFetchingNextPage ? '불러오는 중...' : '더 보기'}
            </Button>
          </div>}
        </>
      ) : <Card><CardContent className="py-10 text-center text-sm text-ink-400">검색어를 입력해 자료를 찾아보세요.</CardContent></Card>}
    </div>
  );
}
