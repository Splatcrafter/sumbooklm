import { useEffect, useState } from 'react';
import { Check, Copy, RefreshCw, Sparkles } from 'lucide-react';
import Markdown, { type Components } from 'react-markdown';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import { useNotebookSummary } from '@/notebooks/useNotebookSummary';

/**
 * How long the copy button says that it copied, before it offers to copy again.
 */
const COPIED_FOR_MS = 2000;

/**
 * Elements the summary is rendered with.
 *
 * A summary is asked for as flowing text, so the map is short on purpose: paragraphs, emphasis and
 * the occasional link a source contributed. Anything else a model produces anyway falls back to what
 * the renderer does with it, which is more readable than the raw markup would be.
 */
const COMPONENTS: Components = {
  p({ children }) {
    return <p className="mb-2 last:mb-0">{children}</p>;
  },
  strong({ children }) {
    return <strong className="font-semibold text-nb-text">{children}</strong>;
  },
  a({ href, children }) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer noopener"
        className="text-nb-accent underline underline-offset-2"
      >
        {children}
      </a>
    );
  },
};

/**
 * The summary of one Sumbook, in the space an empty conversation leaves.
 *
 * What stands here is the one thing a Sumbook can say about itself before anything has been asked, so
 * the space is never left saying nothing: it carries the summary, the reason there is none yet, or
 * the fact that one is being written. The reasons are worth telling apart, because they lead
 * somewhere different: adding a source, waiting, or choosing a model.
 *
 * Writing a summary again is offered rather than done. The sources having changed is a fact this
 * screen can state for free, while rewriting the text costs the reader a request to their own
 * provider, and only they know whether the change was worth it.
 */
export function SummaryView({
  notebookId,
  sourceCount,
  summarisable,
}: {
  notebookId: string;
  sourceCount: number;
  summarisable: boolean;
}) {
  const { t } = useTranslation();
  const { status, summary, failure, writable, write } = useNotebookSummary(notebookId, summarisable);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) {
      return undefined;
    }
    const timer = window.setTimeout(() => setCopied(false), COPIED_FOR_MS);
    return () => window.clearTimeout(timer);
  }, [copied]);

  const text = summary?.text ?? '';
  const writing = status === 'writing';

  /**
   * Returns the sentence that stands where the summary would, while there is none.
   */
  const placeholder = (): string => {
    if (sourceCount === 0) {
      return t('sumbook.summary.noSources');
    }
    if (writing) {
      return t('sumbook.summary.writing');
    }
    if (status === 'failed') {
      return t('sumbook.summary.loadFailed');
    }
    if (failure === 'unread' || !summarisable) {
      return t('sumbook.summary.unread');
    }
    if (failure === 'limited') {
      return t('sumbook.summary.limited');
    }
    if (failure !== null) {
      return t('sumbook.summary.failed');
    }
    return writable ? t('sumbook.summary.pending') : t('sumbook.summary.needsModel');
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="text-[0.9375rem] leading-7 text-nb-body">
        {text !== '' ? (
          <Markdown components={COMPONENTS}>{text}</Markdown>
        ) : (
          <p className={failure === null ? undefined : 'text-nb-muted'} role={writing ? 'status' : undefined}>
            {placeholder()}
          </p>
        )}
      </div>

      {text !== '' && summary?.stale ? (
        <p className="text-xs text-nb-muted">{t('sumbook.summary.stale')}</p>
      ) : null}

      {text !== '' && failure !== null ? (
        <p className="text-xs text-nb-danger" role="alert">
          {t(failure === 'limited' ? 'sumbook.summary.limited' : 'sumbook.summary.failed')}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={text === ''}
          onClick={() => void copy()}
          className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text disabled:opacity-45"
        >
          {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
          {copied ? t('sumbook.summary.copied') : t('sumbook.summary.copy')}
        </Button>
        {writable ? (
          <Button
            variant="outline"
            size="sm"
            disabled={writing}
            onClick={() => void write()}
            className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text disabled:opacity-45"
          >
            {text === '' ? <Sparkles aria-hidden /> : <RefreshCw aria-hidden />}
            {t(text === '' ? 'sumbook.summary.write' : 'sumbook.summary.again')}
          </Button>
        ) : null}
      </div>
    </div>
  );
}
