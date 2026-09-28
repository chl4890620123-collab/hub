import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { useEvidenceStore } from '@/stores/evidenceStore';
import { EmptyState } from '@/components/ui/spinner';
import { EvidenceItem } from '@/features/evidence/EvidenceItem';

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
              <EvidenceItem key={item.id} item={item} />
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );
}
