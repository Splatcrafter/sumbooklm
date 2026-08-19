import { useMemo } from 'react';
import { AlertCircle, Check, FileText, Globe, Loader2, RotateCw, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Source } from '@/sources/source';

/**
 * One source in the panel of a Sumbook.
 *
 * The stage a source has reached is shown as an icon beside its own words. The icon is what the eye
 * finds while running down the list; the words are what anyone not reading the shape gets instead.
 *
 * Reading a source again is offered on a web page and on anything that failed. A page can say
 * something different tomorrow, so its reading is the thing that goes stale; an uploaded file cannot,
 * because its bytes are stored, and the only reason to read one again is that reading it did not work.
 *
 * The two controls are drawn faintly at rest and resolve as soon as the row is touched or focused.
 * They are always present and always reachable; only their weight changes.
 */
export function SourceSheet({
  source,
  index,
  onRefresh,
  onRemove,
}: {
  source: Source;
  index: number;
  onRefresh: () => void;
  onRemove: () => void;
}) {
  const { t, i18n } = useTranslation();
  const kindLabel = t(source.kind === 'WEB' ? 'sumbook.sources.kind.web' : 'sumbook.sources.kind.file');
  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'short', day: 'numeric' }),
    [i18n.language],
  );
  const label = statusLabel(source, t, dateFormat);
  const rereadable = source.status === 'ERROR' || source.kind === 'WEB';

  return (
    <li
      className="sheet-deal group/source flex items-center gap-3 rounded-nb-tile px-2.5 py-2 transition-colors hover:bg-nb-hover focus-within:bg-nb-hover"
      style={{ animationDelay: `${Math.min(index, 11) * 35}ms` }}
    >
      <span aria-label={kindLabel} title={kindLabel} className="shrink-0 text-nb-muted">
        {source.kind === 'WEB' ? <Globe className="size-4" /> : <FileText className="size-4" />}
      </span>

      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[0.8125rem] leading-5 text-nb-text" title={source.origin}>
          {source.displayName}
        </span>
        <span className="flex min-w-0 items-center gap-1.5">
          <StatusIcon status={source.status} label={label} />
          <span
            className={`truncate text-xs leading-4 ${
              source.status === 'ERROR' ? 'text-nb-danger' : 'text-nb-muted'
            }`}
          >
            {label}
          </span>
        </span>
      </span>

      <span className="flex shrink-0 items-center opacity-0 transition-opacity group-hover/source:opacity-100 group-focus-within/source:opacity-100">
        {rereadable ? (
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={source.status === 'UPLOADED' || source.status === 'INDEXING'}
            aria-label={t('sumbook.sources.reindex', { name: source.displayName })}
            className="rounded-full text-nb-muted hover:bg-white/10 hover:text-nb-text"
            onClick={onRefresh}
          >
            <RotateCw />
          </Button>
        ) : null}
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t('sumbook.sources.remove', { name: source.displayName })}
          className="rounded-full text-nb-muted hover:bg-white/10 hover:text-nb-text"
          onClick={onRemove}
        >
          <Trash2 />
        </Button>
      </span>
    </li>
  );
}

function StatusIcon({ status, label }: { status: Source['status']; label: string }) {
  if (status === 'READY') {
    return <Check aria-label={label} className="size-3 shrink-0 text-nb-muted" />;
  }
  if (status === 'ERROR') {
    return <AlertCircle aria-label={label} className="size-3 shrink-0 text-nb-danger" />;
  }
  return <Loader2 aria-label={label} className="size-3 shrink-0 animate-spin text-nb-muted" />;
}

function statusLabel(
  source: Source,
  t: (key: string, options?: Record<string, unknown>) => string,
  dateFormat: Intl.DateTimeFormat,
): string {
  if (source.status === 'READY') {
    if (source.indexedAt !== null) {
      return t('sumbook.sources.status.read', { date: dateFormat.format(new Date(source.indexedAt)) });
    }
    return t('sumbook.sources.status.ready');
  }
  if (source.status === 'ERROR') {
    return t(`sumbook.sources.failure.${source.failure}`);
  }
  if (source.status === 'INDEXING') {
    return t('sumbook.sources.status.indexing');
  }
  return t('sumbook.sources.status.uploaded');
}
