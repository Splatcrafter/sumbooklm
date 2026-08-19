import { useMemo } from 'react';
import Markdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useTranslation } from 'react-i18next';

import type { ChatMessage, ChatSource } from '@/chat/chatMessage';

/**
 * Prefix of the link target a citation uses, followed by the number of the source it refers to.
 */
const CITATION_PREFIX = '#source-';

/**
 * Reads the number a citation link refers to, or null when the link is an ordinary one.
 */
function citedNumber(href: string | undefined): number | null {
  if (!href || !href.startsWith(CITATION_PREFIX)) {
    return null;
  }
  const number = Number.parseInt(href.slice(CITATION_PREFIX.length), 10);
  return Number.isInteger(number) ? number : null;
}

/**
 * Builds the elements an answer is rendered with.
 *
 * A citation is a Markdown link, so it arrives here as an anchor and is turned into a chip carrying
 * the name of the document it stands for. Everything the model cited that was not offered to it stays
 * a plain number, because inventing a name for it would hide exactly the mistake worth seeing.
 *
 * Every other link is rendered as one that leaves the application, which is what a link inside a
 * generated answer is.
 */
function markdownComponents(sources: ChatSource[] | undefined): Components {
  return {
    a({ href, children }) {
      const number = citedNumber(href);
      if (number === null) {
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
      }
      const source = sources?.find((candidate) => candidate.number === number);
      return (
        <sup
          title={source?.displayName}
          className="mx-0.5 rounded-full bg-nb-raised px-1.5 py-0.5 text-[0.625rem] font-medium text-nb-body"
        >
          {number}
        </sup>
      );
    },
    p({ children }) {
      return <p className="mb-2 last:mb-0">{children}</p>;
    },
    ul({ children }) {
      return <ul className="mb-2 list-disc space-y-1 pl-5 last:mb-0">{children}</ul>;
    },
    ol({ children }) {
      return <ol className="mb-2 list-decimal space-y-1 pl-5 last:mb-0">{children}</ol>;
    },
    h1({ children }) {
      return <h3 className="mt-3 mb-1.5 text-[0.9375rem] font-semibold text-nb-text first:mt-0">{children}</h3>;
    },
    h2({ children }) {
      return <h3 className="mt-3 mb-1.5 text-sm font-semibold text-nb-text first:mt-0">{children}</h3>;
    },
    h3({ children }) {
      return <h4 className="mt-2 mb-1 text-sm font-semibold text-nb-body">{children}</h4>;
    },
    strong({ children }) {
      return <strong className="font-semibold text-nb-text">{children}</strong>;
    },
    blockquote({ children }) {
      return (
        <blockquote className="mb-2 border-l-2 border-nb-line pl-3 text-nb-muted last:mb-0">
          {children}
        </blockquote>
      );
    },
    code({ children, className }) {
      const inline = !className;
      return inline ? (
        <code className="rounded bg-nb-inset px-1.5 py-0.5 font-mono text-[0.8125rem]">{children}</code>
      ) : (
        <code className="font-mono text-[0.8125rem]">{children}</code>
      );
    },
    pre({ children }) {
      return (
        <pre className="mb-2 overflow-x-auto rounded-nb-tile bg-nb-inset p-3 last:mb-0">{children}</pre>
      );
    },
    table({ children }) {
      return (
        <div className="mb-2 overflow-x-auto last:mb-0">
          <table className="w-full border-collapse text-left">{children}</table>
        </div>
      );
    },
    th({ children }) {
      return <th className="border-b border-nb-line px-2 py-1 font-semibold">{children}</th>;
    },
    td({ children }) {
      return <td className="border-b border-nb-hover/60 px-2 py-1">{children}</td>;
    },
  };
}

/**
 * One message of the conversation.
 *
 * A question is shown as written, on the right, without Markdown: it is the text the user typed, and
 * rendering it as markup would change what they wrote. An answer is Markdown, on the left, because
 * that is what the model was asked to produce.
 */
export function ChatMessageView({ message }: { message: ChatMessage }) {
  const { t } = useTranslation();
  const components = useMemo(() => markdownComponents(message.sources), [message.sources]);

  if (message.role === 'USER') {
    return (
      <div className="flex justify-end">
        <p className="max-w-[85%] rounded-[1.25rem] bg-nb-raised px-4 py-2.5 text-[0.9375rem] leading-6 whitespace-pre-wrap text-nb-text">
          {message.text}
        </p>
      </div>
    );
  }

  return (
    <div className="flex justify-start">
      <div className="w-full text-[0.9375rem] leading-7 text-nb-body">
        {message.text === '' && message.streaming ? (
          <span className="text-nb-muted" role="status">
            {t('sumbook.chat.thinking')}
          </span>
        ) : (
          <Markdown remarkPlugins={[remarkGfm]} components={components}>
            {message.text}
          </Markdown>
        )}

        {message.sources && message.sources.length > 0 ? (
          <ul className="nb-muted-hand mt-3 flex flex-col gap-0.5 border-t border-nb-line/70 pt-2">
            {message.sources.map((source) => (
              <li key={source.sourceDocumentId} className="flex min-w-0 gap-1.5 text-[0.6875rem] leading-4">
                <span className="shrink-0 text-nb-accent/70">{source.number}</span>
                <span className="truncate text-nb-muted">{source.displayName}</span>
              </li>
            ))}
          </ul>
        ) : null}

        {message.unanswered ? (
          <p className="mt-2 text-xs text-nb-muted" role="status">
            {t('sumbook.chat.noSources')}
          </p>
        ) : null}

        {message.limited ? (
          <p className="mt-2 text-xs text-nb-muted" role="status">
            {message.limitedForMinutes === undefined
              ? t('sumbook.chat.tooMany')
              : t('sumbook.chat.tooOften', { count: message.limitedForMinutes })}
          </p>
        ) : null}

        {message.failure !== undefined ? (
          <p className="mt-2 text-xs text-nb-danger" role="alert">
            {t('sumbook.chat.failed')}
            {message.failure !== '' ? ` ${message.failure}` : ''}
          </p>
        ) : null}
      </div>
    </div>
  );
}
