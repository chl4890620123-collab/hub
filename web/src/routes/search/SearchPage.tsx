import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
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
import type { MaterialHit } from '@/api/types';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const MAX_RECENT = 8;
const SEARCH_PAGE_SIZE = 30;
const SOURCE_FILTERS = [
  { value: 'ALL', label: '전체 자료' },
  { value: 'HUB', label: 'Hub 문서·회의록·첨부파일' },
  { value: 'EXTERNAL', label: '연결 서비스' },
] as const;

export function SearchPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [sourceFilter, setSourceFilter] = useState<(typeof SOURCE_FILTERS)[number]['value']>('ALL');
  const [recentQueries, setRecentQueries] = useLocalStorage<string[]>(`hub.recent-searches.${user?.id ?? 'anon'}`, []);
  const [hits, setHits] = useState<MaterialHit[]>([]);
  const [isFetching, setIsFetching] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);

  useEffect(() => {
    if (!currentProject || !submittedQuery) return;
    let cancelled = false;
    setIsFetching(true);
    materialsApi
      .search(currentProject.id, submittedQuery, 0)
      .then((results) => {
        if (cancelled) return;
        setHits(results);
        setHasMore(results.length >= SEARCH_PAGE_SIZE);
      })
      .catch((error) => !cancelled && toast.error(errorMessage(error)))
      .finally(() => !cancelled && setIsFetching(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentProject?.id, submittedQuery]);

  async function loadMore() {
    if (!currentProject || !submittedQuery) return;
    setLoadingMore(true);
    try {
      const results = await materialsApi.search(currentProject.id, submittedQuery, hits.length);
      setHits((prev) => [...prev, ...results]);
      setHasMore(results.length >= SEARCH_PAGE_SIZE);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setLoadingMore(false);
    }
  }

  const { data: topSearches } = useQuery({
    queryKey: ['top-searches', currentProject?.id],
    queryFn: () => materialsApi.topSearches(currentProject!.id),
    enabled: !!currentProject,
  });

  const filteredHits = (hits ?? []).filter((hit) => {
    if (sourceFilter === 'ALL') return true;
    if (sourceFilter === 'HUB') return hit.sourceType === 'HUB' || hit.sourceType === 'ATTACHMENT';
    return hit.sourceType !== 'HUB' && hit.sourceType !== 'ATTACHMENT';
  });

  if (!currentProject) return <NoProjectState />;

  function runSearch(q: string) {
    const trimmed = q.trim();
    if (!trimmed) return;
    setSubmittedQuery(trimmed);
    setRecentQueries([trimmed, ...recentQueries.filter((r) => r !== trimmed)].slice(0, MAX_RECENT));
  }

  return (
    <div>
      <PageHeader title="자료 찾기" description="내 문서·회의록과 권한이 있는 Slack, Notion, Drive 자료를 한 번에 검색합니다." />

      <p className="mb-3 text-xs text-ink-500">
        검색 범위: 현재 프로젝트에서 볼 수 있는 문서, 회의록, 업무 첨부파일과 연결 서비스 자료입니다.
      </p>

      <form
        className="mb-4 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          runSearch(query);
        }}
      >
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="예: 주간회의, 계약서, 전달받은 파일 이름"
          className="max-w-lg"
        />
        <Button type="submit">
          <SearchIcon size={14} /> 검색
        </Button>
      </form>

      <div className="mb-5 flex flex-wrap gap-1.5">
        {SOURCE_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setSourceFilter(filter.value)}
            className={
              'rounded-full px-3 py-1.5 text-xs font-medium ' +
              (sourceFilter === filter.value ? 'bg-accent-600 text-white' : 'bg-ink-100 text-ink-600 hover:bg-ink-200')
            }
          >
            {filter.label}
          </button>
        ))}
      </div>

      {(recentQueries.length > 0 || (topSearches && topSearches.length > 0)) && (
        <div className="mb-5 flex flex-wrap gap-4 text-sm">
          {recentQueries.length > 0 && (
            <div>
              <span className="mr-2 text-ink-400">최근 검색</span>
              {recentQueries.map((q) => (
                <button
                  key={q}
                  onClick={() => {
                    setQuery(q);
                    runSearch(q);
                  }}
                  className="mr-1.5 rounded-full bg-ink-100 px-2.5 py-1 text-xs text-ink-600 hover:bg-ink-200"
                >
                  {q}
                </button>
              ))}
            </div>
          )}
          {topSearches && topSearches.length > 0 && (
            <div>
              <span className="mr-2 text-ink-400">인기 검색어</span>
              {topSearches.map((t) => (
                <button
                  key={t.query_text}
                  onClick={() => {
                    setQuery(t.query_text);
                    runSearch(t.query_text);
                  }}
                  className="mr-1.5 rounded-full bg-accent-50 px-2.5 py-1 text-xs text-accent-700 hover:bg-accent-100"
                >
                  {t.query_text}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {isFetching ? (
        <LoadingBlock label="검색 중..." />
      ) : submittedQuery ? (
        <>
          <MaterialResultList hits={filteredHits} emptyLabel="선택한 범위에서 검색 결과가 없습니다." />
          {hasMore && (
            <div className="mt-3 flex justify-center">
              <Button variant="outline" disabled={loadingMore} onClick={loadMore}>
                {loadingMore ? '불러오는 중...' : '더 보기'}
              </Button>
            </div>
          )}
        </>
      ) : (
        <Card>
          <CardContent className="py-10 text-center text-sm text-ink-400">검색어를 입력해 자료를 찾아보세요.</CardContent>
        </Card>
      )}
    </div>
  );
}
