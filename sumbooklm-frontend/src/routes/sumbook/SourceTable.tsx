import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Source } from '@/sources/source';
import { SourceSheet } from '@/routes/sumbook/SourceSheet';
import type { SourcesStatus } from '@/sources/useSources';

/**
 * The panel that lists what a Sumbook is grounded in.
 *
 * Adding sits directly under the heading rather than below the list, so it stays reachable without
 * scrolling once a Sumbook holds many sources. The list itself is the only part that scrolls, which
 * keeps the heading and the action in place while it does.
 */
export function SourceTable({
  status,
  sources,
  onAdd,
  onRefresh,
  onRemove,
  onRetry,
}: {
  status: SourcesStatus;
  sources: Source[];
  onAdd: () => void;
  onRefresh: (sourceId: string) => void;
  onRemove: (sourceId: string) => void;
  onRetry: () => void;
}) {
  const { t } = useTranslation();

  return (
    <section
      aria-labelledby="sumbook-sources"
      className="flex min-h-0 flex-1 flex-col gap-4 rounded-nb-panel bg-nb-surface p-4"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="sumbook-sources" className="text-base leading-6 font-medium text-nb-text">
          {t('sumbook.sources.heading')}
        </h2>
        <span className="text-[0.8125rem] text-nb-muted">{sources.length}</span>
      </div>

      <Button
        variant="outline"
        onClick={onAdd}
        className="h-10 w-full rounded-full border-nb-line bg-transparent text-[0.8125rem] font-medium text-nb-text hover:bg-nb-hover"
      >
        <Plus />
        {t('sumbook.sources.add')}
      </Button>

      {status === 'loading' ? (
        <p className="text-[0.8125rem] text-nb-muted">{t('sumbook.sources.loading')}</p>
      ) : null}

      {status === 'failed' ? (
        <div className="flex flex-col items-start gap-2 rounded-nb-tile bg-nb-inset px-3 py-2.5">
          <p className="text-[0.8125rem] text-nb-danger" role="alert">
            {t('sumbook.errors.load')}
          </p>
          <Button
            variant="outline"
            size="sm"
            className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
            onClick={onRetry}
          >
            {t('sumbook.actions.retry')}
          </Button>
        </div>
      ) : null}

      {status === 'ready' && sources.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 rounded-nb-tile bg-nb-inset px-4 py-8 text-center">
          <p className="text-[0.8125rem] leading-5 text-nb-muted">{t('sumbook.sources.empty')}</p>
        </div>
      ) : null}

      {status === 'ready' && sources.length > 0 ? (
        <ul className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
          {sources.map((source, index) => (
            <SourceSheet
              key={source.id}
              source={source}
              index={index}
              onRefresh={() => onRefresh(source.id)}
              onRemove={() => onRemove(source.id)}
            />
          ))}
        </ul>
      ) : null}
    </section>
  );
}
