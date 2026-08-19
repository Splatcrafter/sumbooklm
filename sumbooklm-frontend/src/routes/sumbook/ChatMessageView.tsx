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
            className="text-jb-accent-bright underline underline-offset-2"
          >
            {children}
          </a>
        );
      }
      const source = sources?.find((candidate) => candidate.number === number);
      return (
        <sup
          title={source?.displayName}
          className="mx-0.5 rounded-full bg-jb-grey-80/80 px-1.5 py-0.5 text-[0.625rem] font-medium text-jb-grey-20"
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
      return <h3 className="mb-2 text-base font-semibold text-jb-grey-5">{children}</h3>;
    },
    h2({ children }) {
      return <h3 className="mb-2 text-sm font-semibold text-jb-grey-5">{children}</h3>;
    },
    h3({ children }) {
      return <h4 className="mb-1 text-sm font-semibold text-jb-grey-10">{children}</h4>;
    },
    strong({ children }) {
      return <strong className="font-semibold text-jb-grey-5">{children}</strong>;
    },
    blockquote({ children }) {
      return (
        <blockquote className="mb-2 border-l-2 border-jb-grey-70 pl-3 text-jb-grey-40 last:mb-0">
          {children}
        </blockquote>
      );
    },
    code({ children, className }) {
      const inline = !className;
      return inline ? (
        <code className="rounded bg-jb-black/60 px-1 py-0.5 font-mono text-[0.8125rem]">{children}</code>
      ) : (
        <code className="font-mono text-[0.8125rem]">{children}</code>
      );
    },
    pre({ children }) {
      return (
        <pre className="mb-2 overflow-x-auto rounded-jb-card bg-jb-black/60 p-3 last:mb-0">{children}</pre>
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
      return <th className="border-b border-jb-grey-70 px-2 py-1 font-semibold">{children}</th>;
    },
    td({ children }) {
      return <td className="border-b border-jb-grey-80/60 px-2 py-1">{children}</td>;
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
        <div className="max-w-[85%] rounded-jb-card bg-jb-accent px-3 py-2 text-sm leading-6 whitespace-pre-wrap text-white">
          {message.text}
        </div>
      </div>
    );
  }

  return (
    <div className="flex justify-start">
      <div className="max-w-[92%] rounded-jb-card bg-jb-grey-90/60 px-3 py-2 text-sm leading-6 text-jb-grey-20 ring-1 ring-jb-grey-80/60">
        {message.text === '' && message.streaming ? (
          <span className="text-jb-grey-50" role="status">
            {t('sumbook.chat.thinking')}
          </span>
        ) : (
          <Markdown remarkPlugins={[remarkGfm]} components={components}>
            {message.text}
          </Markdown>
        )}

        {message.sources && message.sources.length > 0 ? (
          <ul className="mt-2 flex flex-wrap gap-1.5 border-t border-jb-grey-80/60 pt-2">
            {message.sources.map((source) => (
              <li
                key={source.sourceDocumentId}
                className="max-w-56 truncate rounded-full bg-jb-black/40 px-2 py-0.5 text-[0.6875rem] text-jb-grey-40"
              >
                {source.number}. {source.displayName}
              </li>
            ))}
          </ul>
        ) : null}

        {message.limited ? (
          <p className="mt-2 text-xs text-jb-grey-40" role="status">
            {message.limitedForMinutes === undefined
              ? t('sumbook.chat.tooMany')
              : t('sumbook.chat.tooOften', { count: message.limitedForMinutes })}
          </p>
        ) : null}

        {message.failure !== undefined ? (
          <p className="mt-2 text-xs text-jb-danger" role="alert">
            {t('sumbook.chat.failed')}
            {message.failure !== '' ? ` ${message.failure}` : ''}
          </p>
        ) : null}
      </div>
    </div>
  );
}
