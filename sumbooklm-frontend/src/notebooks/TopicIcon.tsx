import { NotebookText } from 'lucide-react';

import { cn } from '@/lib/utils';

/**
 * The symbol standing for the subject of a Sumbook.
 *
 * It is rendered as the characters the backend stored, which is the one place where a symbol reaches
 * the interface as data. It stands on its own without a plate behind it, at a size where an emoji
 * reads as an illustration rather than as a glyph in a line of text.
 *
 * Until the backend has derived one, a neutral outline icon is shown instead of a placeholder
 * character, so an unlabelled Sumbook does not look like a labelled one.
 */
export function TopicIcon({
  topicIcon,
  className,
  iconClassName,
}: {
  topicIcon: string;
  className?: string;
  iconClassName?: string;
}) {
  return (
    <span
      aria-hidden
      className={cn('flex items-center justify-center text-4xl leading-none select-none', className)}
    >
      {topicIcon === '' ? (
        <NotebookText className={cn('size-8 text-nb-muted', iconClassName)} />
      ) : (
        topicIcon
      )}
    </span>
  );
}
