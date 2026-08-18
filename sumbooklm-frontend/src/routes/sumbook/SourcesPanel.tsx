import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Source } from '@/sources/source';
import { SourceListItem } from '@/routes/sumbook/SourceListItem';
import type { SourcesStatus } from '@/sources/useSources';

/**
 * The sidebar that lists what a Sumbook is grounded in.
 *
 * Adding sits above the list rather than below it, so it stays reachable without scrolling once a
 * Sumbook holds many sources. The list itself is the only part that scrolls, which keeps the heading
 * and the action in place while it does.
 */
export function SourcesPanel({
  status,
  sources,
  onAdd,
  onRemove,
  onRetry,
}: {
  status: SourcesStatus;
  sources: Source[];
  onAdd: () => void;
  onRemove: (sourceId: string) => void;
  onRetry: () => void;
}) {
  const { t } = useTranslation();

  return (
    <section
      aria-labelledby="sumbook-sources"
      className="flex min-h-0 flex-col gap-4 rounded-jb-block bg-jb-grey-95/60 p-4 ring-1 ring-jb-grey-70/25"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="sumbook-sources" className="text-sm font-semibold tracking-wide text-jb-grey-20">
          {t('sumbook.sources.heading')}
        </h2>
        <span className="text-xs text-jb-grey-50">{t('sumbook.sources.count', { count: sources.length })}</span>
      </div>

      <Button
        variant="outline"
        className="w-full rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
        onClick={onAdd}
      >
        <Plus />
        {t('sumbook.sources.add')}
      </Button>

      {status === 'loading' ? (
        <p className="text-[0.8125rem] text-jb-grey-50">{t('sumbook.sources.loading')}</p>
      ) : null}

      {status === 'failed' ? (
        <div className="flex flex-col items-start gap-2">
          <p className="text-[0.8125rem] text-jb-danger" role="alert">
            {t('sumbook.errors.load')}
          </p>
          <Button
            variant="outline"
            size="sm"
            className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
            onClick={onRetry}
          >
            {t('sumbook.actions.retry')}
          </Button>
        </div>
      ) : null}

      {status === 'ready' && sources.length === 0 ? (
        <p className="text-[0.8125rem] leading-5 text-jb-grey-50">{t('sumbook.sources.empty')}</p>
      ) : null}

      {status === 'ready' && sources.length > 0 ? (
        <ul className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
          {sources.map((source) => (
            <SourceListItem key={source.id} source={source} onRemove={() => onRemove(source.id)} />
          ))}
        </ul>
      ) : null}
    </section>
  );
}
