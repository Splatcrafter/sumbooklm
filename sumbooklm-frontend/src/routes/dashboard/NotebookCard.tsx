import { MoreVertical, NotebookText, Pencil, Pin, PinOff, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { Notebook } from '@/notebooks/notebook';
import { useNotebookMeta } from '@/routes/dashboard/NotebookMeta';

/**
 * One notebook of the overview.
 *
 * The topic icon is rendered as the characters the backend stored, which is the one place where a
 * symbol reaches the interface as data. It is empty until the backend has derived it, and the card
 * shows a neutral icon of its own in that case instead of a placeholder character, so an unlabelled
 * notebook does not look like a labelled one.
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

  return (
    <Card className="min-h-40 gap-3 rounded-jb-card bg-jb-grey-95/70 ring-jb-grey-70/25 transition-colors hover:bg-jb-grey-90/70 hover:ring-jb-grey-60/40">
      <CardHeader>
        <div
          aria-hidden={notebook.topicIcon === ''}
          className="flex size-10 items-center justify-center rounded-xl bg-jb-grey-90 text-xl leading-none ring-1 ring-jb-grey-70/40"
        >
          {notebook.topicIcon === '' ? (
            <NotebookText className="size-5 text-jb-grey-40" />
          ) : (
            notebook.topicIcon
          )}
        </div>
        <CardAction>
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
