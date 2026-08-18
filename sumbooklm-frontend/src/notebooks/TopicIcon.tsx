import { NotebookText } from 'lucide-react';

import { cn } from '@/lib/utils';

/**
 * The square that stands for the subject of a Sumbook.
 *
 * The icon is rendered as the characters the backend stored, which is the one place where a symbol
 * reaches the interface as data. It is empty until the backend has derived it, and a neutral icon is
 * shown in that case rather than a placeholder character, so an unlabelled Sumbook does not look
 * like a labelled one.
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
    <div
      aria-hidden={topicIcon === ''}
      className={cn(
        'flex size-10 items-center justify-center rounded-xl bg-jb-grey-90 text-xl leading-none ring-1 ring-jb-grey-70/40',
        className,
      )}
    >
      {topicIcon === '' ? (
        <NotebookText className={cn('size-5 text-jb-grey-40', iconClassName)} />
      ) : (
        topicIcon
      )}
    </div>
  );
}
