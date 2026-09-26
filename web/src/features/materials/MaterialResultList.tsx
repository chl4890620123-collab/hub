import { ExternalLink, Pencil, Trash2 } from 'lucide-react';
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

export interface MaterialResultActions {
  canManageAttachment?: (hit: MaterialHit) => boolean;
  onEditAttachment?: (hit: MaterialHit) => void;
  onDeleteAttachment?: (hit: MaterialHit) => void;
}

function MaterialResultCard({ hit, actions }: { hit: MaterialHit; actions?: MaterialResultActions }) {
  const manageable = hit.sourceType === 'ATTACHMENT' && Boolean(actions?.canManageAttachment?.(hit));
  return (
    <li className="rounded-lg border border-ink-200 p-4 transition-shadow hover:shadow-sm">
      <div className="mb-1.5 flex flex-wrap items-center gap-2">
        <Badge variant="outline">{SOURCE_LABELS[hit.sourceType] ?? hit.sourceLabel}</Badge>
        <span className="text-xs text-ink-400">{ITEM_TYPE_LABELS[hit.itemType] ?? '자료'}</span>
        {hit.recommendationReason && <span className="text-xs text-accent-600">{hit.recommendationReason}</span>}
      </div>
      <p className="mb-1 text-sm font-semibold text-ink-900">{hit.title}</p>
      {hit.location && <p className="mb-1 text-xs text-ink-400">{hit.location}</p>}
      <p className="line-clamp-3 text-sm text-ink-600">{hit.snippet}</p>
      <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-ink-400">
        {hit.author && <span>{hit.author}</span>}
        {hit.sourceUrl && (
          <a href={hit.sourceUrl} target={hit.sourceType === 'ATTACHMENT' ? undefined : '_blank'}
            rel={hit.sourceType === 'ATTACHMENT' ? undefined : 'noreferrer'}
            className="flex items-center gap-1 text-accent-600 hover:underline">
            {hit.sourceType === 'ATTACHMENT' ? '파일 읽기/다운로드' : '원문 열기'} <ExternalLink size={12} />
          </a>
        )}
        {manageable && actions?.onEditAttachment && (
          <button type="button" onClick={() => actions.onEditAttachment?.(hit)} className="flex items-center gap-1 text-accent-600 hover:underline">
            <Pencil size={12} /> 수정
          </button>
        )}
        {manageable && actions?.onDeleteAttachment && (
          <button type="button" onClick={() => actions.onDeleteAttachment?.(hit)} className="flex items-center gap-1 text-red-600 hover:underline">
            <Trash2 size={12} /> 삭제
          </button>
        )}
      </div>
    </li>
  );
}

export function MaterialResultList({
  hits,
  emptyLabel = '검색 결과가 없습니다.',
  actions,
}: {
  hits: MaterialHit[];
  emptyLabel?: string;
  actions?: MaterialResultActions;
}) {
  if (hits.length === 0) return <EmptyState title={emptyLabel} />;
  return <ul className="flex flex-col gap-3">{hits.map((hit) => <MaterialResultCard key={`${hit.sourceType}-${hit.evidenceId}`} hit={hit} actions={actions} />)}</ul>;
}
