import { CalendarDays, LayoutGrid, List } from 'lucide-react';
import { cn } from '@/lib/cn';

export type ViewMode = 'list' | 'grid' | 'calendar';

const OPTIONS: { mode: ViewMode; icon: typeof List; label: string }[] = [
  { mode: 'list', icon: List, label: '목록' },
  { mode: 'grid', icon: LayoutGrid, label: '카드' },
  { mode: 'calendar', icon: CalendarDays, label: '달력' },
];

export function ViewModeToggle({ value, onChange }: { value: ViewMode; onChange: (mode: ViewMode) => void }) {
  return (
    <div className="inline-flex items-center gap-0.5 rounded-md bg-ink-100 p-1">
      {OPTIONS.map((opt) => (
        <button
          key={opt.mode}
          onClick={() => onChange(opt.mode)}
          className={cn(
            'flex items-center gap-1.5 rounded-sm px-2.5 py-1 text-xs font-medium transition-colors',
            value === opt.mode ? 'bg-white text-ink-900 shadow-sm dark:bg-ink-200' : 'text-ink-500 hover:text-ink-700',
          )}
        >
          <opt.icon size={13} />
          {opt.label}
        </button>
      ))}
    </div>
  );
}
