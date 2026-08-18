import { MoreVertical, Pencil, Pin, PinOff, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
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

/**
 * One notebook of the overview.
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

  return (
    <Card
      role="link"
      tabIndex={0}
      onClick={() => void navigate(sumbookPath(notebook.id))}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          void navigate(sumbookPath(notebook.id));
        }
      }}
      className="min-h-40 cursor-pointer gap-3 rounded-jb-card bg-jb-grey-95/70 ring-jb-grey-70/25 transition-colors outline-none hover:bg-jb-grey-90/70 hover:ring-jb-grey-60/40 focus-visible:ring-2 focus-visible:ring-jb-grey-30/40"
    >
      <CardHeader>
        <TopicIcon topicIcon={notebook.topicIcon} />
        <CardAction onClick={(event) => event.stopPropagation()}>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('dashboard.menu.open', { title: notebook.title })}
                  className="text-jb-grey-50 hover:bg-jb-grey-80/50 hover:text-jb-grey-5"
                />
              }
            >
              <MoreVertical />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52 bg-jb-grey-90 text-jb-grey-10 ring-jb-grey-70/40">
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
        </CardAction>
      </CardHeader>
      <CardContent className="mt-auto flex flex-col gap-1">
        <CardTitle className="line-clamp-2 text-base leading-6 text-jb-grey-5">
          {notebook.title}
        </CardTitle>
        <p className="text-[0.8125rem] leading-5 text-jb-grey-50">
          {meta(notebook.lastActivityAt, notebook.sourceCount)}
        </p>
      </CardContent>
    </Card>
  );
}
