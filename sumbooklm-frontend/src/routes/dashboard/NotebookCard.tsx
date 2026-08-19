import { MoreVertical, Pencil, Pin, PinOff, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { Notebook } from '@/notebooks/notebook';
import { sumbookPath } from '@/notebooks/notebookRoutes';
import { TopicIcon } from '@/notebooks/TopicIcon';
import { useNotebookMeta } from '@/routes/dashboard/NotebookMeta';
import { notebookTint } from '@/routes/dashboard/notebookTint';

/**
 * One Sumbook of the overview.
 *
 * The card carries its symbol at the top and its title at the bottom, with the space between them
 * left empty on purpose: every card is the same height, so a one line title and a three line title
 * produce the same object and the shelf stays even.
 *
 * The tint is derived from the identifier of the Sumbook, which gives a shelf of them variety
 * without any of them being louder than the others.
 *
 * The whole card opens the Sumbook, so the target is the size of the card rather than of its title.
 * The menu inside it opens on its own and must not open the Sumbook as well, which is why it stops
 * the click from travelling further up.
 */
export function NotebookCard({
  notebook,
  onEditTitle,
  onTogglePin,
  onDelete,
}: {
  notebook: Notebook;
  onEditTitle: () => void;
  onTogglePin: () => void;
  onDelete: () => void;
}) {
  const { t } = useTranslation();
  const meta = useNotebookMeta();
  const navigate = useNavigate();
  const { date, sources } = meta(notebook.lastActivityAt, notebook.sourceCount);

  return (
    <div
      role="link"
      tabIndex={0}
      onClick={() => void navigate(sumbookPath(notebook.id))}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          void navigate(sumbookPath(notebook.id));
        }
      }}
      className={`group/card flex min-h-56 cursor-pointer flex-col gap-4 rounded-nb-card p-4 transition-[filter,box-shadow] outline-none hover:brightness-115 focus-visible:ring-2 focus-visible:ring-nb-accent ${notebookTint(notebook.id)}`}
    >
      <div className="flex items-start justify-between gap-2">
        <TopicIcon topicIcon={notebook.topicIcon} />
        <span className="flex items-center gap-0.5" onClick={(event) => event.stopPropagation()}>
          {notebook.pinned ? (
            <Pin className="size-4 text-nb-body" aria-hidden />
          ) : null}
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('dashboard.menu.open', { title: notebook.title })}
                  className="rounded-full text-nb-body hover:bg-white/10 hover:text-nb-text"
                />
              }
            >
              <MoreVertical />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52 rounded-nb-tile bg-nb-raised text-nb-text">
              <DropdownMenuItem onClick={onEditTitle}>
                <Pencil />
                {t('dashboard.menu.editTitle')}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onTogglePin}>
                {notebook.pinned ? <PinOff /> : <Pin />}
                {notebook.pinned ? t('dashboard.menu.unpin') : t('dashboard.menu.pin')}
              </DropdownMenuItem>
              <DropdownMenuItem variant="destructive" onClick={onDelete}>
                <Trash2 />
                {t('dashboard.menu.delete')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </span>
      </div>

      <div className="mt-auto flex flex-col gap-2">
        <h3 className="line-clamp-3 text-xl leading-7 font-medium text-nb-text">{notebook.title}</h3>
        <p className="text-[0.8125rem] leading-5 text-nb-body">
          {date} &middot; {sources}
        </p>
      </div>
    </div>
  );
}
