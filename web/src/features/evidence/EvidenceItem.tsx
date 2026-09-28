import type { EvidenceView } from '@/api/types';

export function formatTimestampMs(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export function EvidenceItem({ item }: { item: EvidenceView }) {
  return (
    <li className="rounded-md border border-ink-200 p-3">
      <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-ink-400">
        {item.documentName && <span className="font-medium text-ink-600">{item.documentName}</span>}
        {item.paragraphRef && <span>· {item.paragraphRef}</span>}
        {item.pageNo != null && <span>· {item.pageNo}p</span>}
        {item.meetingTitle && <span className="font-medium text-ink-600">{item.meetingTitle}</span>}
        {item.speaker && <span>· {item.speaker}</span>}
        {item.startMs != null && <span>· {formatTimestampMs(item.startMs)}</span>}
      </div>
      <p className="whitespace-pre-wrap text-sm text-ink-800">"{item.quote}"</p>
    </li>
  );
}
