import { AlertCircle, Check, FileText, Globe, Loader2, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Source } from '@/sources/source';

/**
 * One source in the sidebar of a Sumbook.
 *
 * The stage a source has reached is shown as an icon rather than as a word, because the list is
 * narrow and the four stages are distinguishable by shape alone. Each icon still carries its label
 * for anyone who is not reading the shape.
 */
export function SourceListItem({ source, onRemove }: { source: Source; onRemove: () => void }) {
  const { t } = useTranslation();
  const kindLabel = t(source.kind === 'WEB' ? 'sumbook.sources.kind.web' : 'sumbook.sources.kind.file');

  return (
    <li className="group/source flex items-center gap-3 rounded-jb-card bg-jb-grey-90/40 px-3 py-2.5 ring-1 ring-jb-grey-70/20 transition-colors hover:bg-jb-grey-90/70">
      <span
        aria-label={kindLabel}
        title={kindLabel}
        className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-jb-grey-80/60 text-jb-grey-30"
      >
        {source.kind === 'WEB' ? <Globe className="size-4" /> : <FileText className="size-4" />}
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[0.8125rem] leading-5 text-jb-grey-10" title={source.origin}>
          {source.displayName}
        </span>
        <span className="truncate text-xs leading-4 text-jb-grey-50">{statusLabel(source, t)}</span>
      </span>
      <StatusIcon status={source.status} label={statusLabel(source, t)} />
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t('sumbook.sources.remove', { name: source.displayName })}
        className="shrink-0 text-jb-grey-60 hover:bg-jb-grey-80/60 hover:text-jb-grey-5"
        onClick={onRemove}
      >
        <Trash2 />
      </Button>
    </li>
  );
}

function StatusIcon({ status, label }: { status: Source['status']; label: string }) {
  if (status === 'READY') {
    return <Check aria-label={label} className="size-4 shrink-0 text-jb-grey-30" />;
  }
  if (status === 'ERROR') {
    return <AlertCircle aria-label={label} className="size-4 shrink-0 text-jb-danger" />;
  }
  return <Loader2 aria-label={label} className="size-4 shrink-0 animate-spin text-jb-grey-50" />;
}

function statusLabel(source: Source, t: (key: string, options?: Record<string, unknown>) => string): string {
  if (source.status === 'READY') {
    return t('sumbook.sources.status.ready', { count: source.tokenCount });
  }
  if (source.status === 'ERROR') {
    return t('sumbook.sources.status.error');
  }
  if (source.status === 'INDEXING') {
    return t('sumbook.sources.status.indexing');
  }
  return t('sumbook.sources.status.uploaded');
}
