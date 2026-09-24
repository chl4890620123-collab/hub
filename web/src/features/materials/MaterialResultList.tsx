import { ExternalLink } from 'lucide-react';
import type { MaterialHit } from '@/api/types';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/spinner';

const SOURCE_LABELS: Record<string, string> = {
  HUB: 'Hub',
  GITHUB: 'GitHub',
  GOOGLE_DRIVE: 'Google Drive',
  SLACK: 'Slack',
  NOTION: 'Notion',
  ATTACHMENT: '업무 첨부파일',
};

const ITEM_TYPE_LABELS: Record<string, string> = {
  DOCUMENT: '문서',
  ATTACHMENT: '첨부파일',
  DRIVE_FILE: 'Google Drive 파일',
  SLACK_MESSAGE: 'Slack 메시지',
  GIT_ISSUE: 'GitHub 이슈',
  GIT_PR: 'GitHub Pull Request',
  GIT_COMMIT: 'GitHub 커밋',
  NOTION_PAGE: 'Notion 페이지',
};

function MaterialResultCard({ hit }: { hit: MaterialHit }) {
  return (
    <li className="rounded-lg border border-ink-200 p-4 transition-shadow hover:shadow-sm">
      <div className="mb-1.5 flex flex-wrap items-center gap-2">
        <Badge variant="outline">{SOURCE_LABELS[hit.sourceType] ?? hit.sourceLabel}</Badge>
        <span className="text-xs text-ink-400">{ITEM_TYPE_LABELS[hit.itemType] ?? hit.itemType}</span>
        {hit.recommendationReason && (
          <span className="text-xs text-accent-600">{hit.recommendationReason}</span>
        )}
      </div>
      <p className="mb-1 text-sm font-semibold text-ink-900">{hit.title}</p>
      {hit.location && <p className="mb-1 text-xs text-ink-400">{hit.location}</p>}
      <p className="line-clamp-3 text-sm text-ink-600">{hit.snippet}</p>
      <div className="mt-2 flex items-center gap-3 text-xs text-ink-400">
        {hit.author && <span>{hit.author}</span>}
        {hit.sourceUrl && (
          <a
            href={hit.sourceUrl}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1 text-accent-600 hover:underline"
          >
            원문 열기 <ExternalLink size={12} />
          </a>
        )}
      </div>
    </li>
  );
}

export function MaterialResultList({ hits, emptyLabel = '검색 결과가 없습니다.' }: { hits: MaterialHit[]; emptyLabel?: string }) {
  if (hits.length === 0) return <EmptyState title={emptyLabel} />;
  return (
    <ul className="flex flex-col gap-3">
      {hits.map((hit) => (
        <MaterialResultCard key={`${hit.sourceType}-${hit.evidenceId}`} hit={hit} />
      ))}
    </ul>
  );
}
