import type { ReactNode } from 'react';

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-start justify-between gap-4 sm:flex-nowrap">
      <div className="min-w-0 flex-1">
        <h1 className="break-words text-headline text-ink-900">{title}</h1>
        {description && <p className="mt-0.5 break-words text-sm text-ink-500">{description}</p>}
      </div>
      {action && <div className="w-full min-w-0 sm:w-auto sm:shrink-0">{action}</div>}
    </div>
  );
}
