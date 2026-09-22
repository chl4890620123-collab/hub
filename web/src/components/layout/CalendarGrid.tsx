import { useMemo, type ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { localDate } from '@/lib/format';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const MAX_VISIBLE = 3;

export function CalendarGrid<T>({
  year,
  month,
  items,
  getDate,
  renderItem,
}: {
  year: number;
  month: number;
  items: T[];
  getDate: (item: T) => string | null;
  renderItem: (item: T) => ReactNode;
}) {
  const today = localDate();

  const byDate = useMemo(() => {
    const map = new Map<string, T[]>();
    for (const item of items) {
      const date = getDate(item);
      if (!date) continue;
      const list = map.get(date) ?? [];
      list.push(item);
      map.set(date, list);
    }
    return map;
  }, [items, getDate]);

  const cells = useMemo(() => {
    const firstOfMonth = new Date(year, month - 1, 1);
    const startWeekday = firstOfMonth.getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const list: { date: string | null }[] = [];
    for (let i = 0; i < startWeekday; i += 1) list.push({ date: null });
    for (let day = 1; day <= daysInMonth; day += 1) {
      list.push({ date: `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}` });
    }
    return list;
  }, [year, month]);

  const undated = items.filter((item) => !getDate(item));

  return (
    <div>
      <div className="grid grid-cols-7 overflow-hidden rounded-lg border border-ink-200">
        {WEEKDAYS.map((day) => (
          <div key={day} className="border-b border-ink-100 bg-ink-50 py-1.5 text-center text-xs font-medium text-ink-500">
            {day}
          </div>
        ))}
        {cells.map((cell, index) => {
          const dayItems = cell.date ? byDate.get(cell.date) ?? [] : [];
          return (
            <div
              key={index}
              className={cn(
                'min-h-[92px] border-b border-r border-ink-100 p-1.5 last:border-r-0',
                cell.date === today && 'bg-accent-50/50',
              )}
            >
              {cell.date && <p className="mb-1 text-xs text-ink-400">{Number(cell.date.slice(-2))}</p>}
              <div className="flex flex-col gap-1">
                {dayItems.slice(0, MAX_VISIBLE).map((item, i) => (
                  <div key={i}>{renderItem(item)}</div>
                ))}
                {dayItems.length > MAX_VISIBLE && (
                  <p className="text-[11px] text-ink-400">+{dayItems.length - MAX_VISIBLE}개 더보기</p>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {undated.length > 0 && (
        <div className="mt-4">
          <p className="mb-2 text-xs font-medium text-ink-500">날짜 미지정 ({undated.length})</p>
          <div className="flex flex-col gap-1.5">
            {undated.map((item, i) => (
              <div key={i}>{renderItem(item)}</div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
