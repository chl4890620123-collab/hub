import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { useEvidenceStore } from '@/stores/evidenceStore';
import { EmptyState } from '@/components/ui/spinner';

function formatTimestampMs(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export function EvidenceViewerDialog() {
  const { isOpen, title, items, close } = useEvidenceStore();

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && close()}>
      <DialogContent className="max-h-[80vh] max-w-2xl overflow-y-auto">
        <DialogTitle>{title ? `${title} · 근거 자료` : '근거 자료'}</DialogTitle>
        {items.length === 0 ? (
          <EmptyState title="근거 자료가 없습니다." />
        ) : (
          <ul className="flex flex-col gap-3">
            {items.map((item) => (
              <li key={item.id} className="rounded-md border border-ink-200 p-3">
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
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );
}
