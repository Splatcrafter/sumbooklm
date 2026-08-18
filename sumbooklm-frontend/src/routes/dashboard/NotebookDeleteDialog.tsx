import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

/**
 * Confirms the removal of a notebook.
 *
 * Removal takes the sources and the conversations of the notebook with it and cannot be undone, so
 * it is the one action of the overview that asks before it runs. The name of the notebook is part of
 * the question, because a confirmation that does not say what is being removed confirms nothing.
 */
export function NotebookDeleteDialog({
  open,
  onOpenChange,
  title,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  onConfirm: () => Promise<void>;
}) {
  const { t } = useTranslation();
  const [failed, setFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function confirm() {
    setFailed(false);
    setSubmitting(true);
    try {
      await onConfirm();
      onOpenChange(false);
    } catch {
      setFailed(true);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="rounded-jb-card bg-jb-grey-95 text-jb-grey-10 ring-jb-grey-70/40">
        <AlertDialogHeader>
          <AlertDialogTitle className="text-jb-grey-5">{t('dashboard.delete.heading')}</AlertDialogTitle>
          <AlertDialogDescription className="text-jb-grey-50">
            {t('dashboard.delete.description', { title })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {failed ? (
          <p className="text-[0.8125rem] text-jb-danger" role="alert">
            {t('dashboard.errors.action')}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5">
            {t('dashboard.actions.cancel')}
          </AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={submitting}
            className="rounded-jb-card"
            onClick={() => void confirm()}
          >
            {t('dashboard.delete.confirm')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
