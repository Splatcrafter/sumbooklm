import { useEffect, useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

/**
 * Asks for the title of a notebook.
 *
 * Creating and renaming ask for exactly the same value under different wording, so both use this
 * dialog and differ only in the texts they hand in. The value is reset whenever the dialog opens,
 * because a dialog that reopens with what was typed the last time offers a title the user already
 * discarded.
 */
export function NotebookTitleDialog({
  open,
  onOpenChange,
  heading,
  description,
  submitLabel,
  initialTitle,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  heading: string;
  description: string;
  submitLabel: string;
  initialTitle: string;
  onSubmit: (title: string) => Promise<void>;
}) {
  const { t } = useTranslation();
  const inputId = useId();
  const [title, setTitle] = useState(initialTitle);
  const [failed, setFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setTitle(initialTitle);
      setFailed(false);
      setSubmitting(false);
    }
  }, [initialTitle, open]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = title.trim();
    if (trimmed === '') {
      return;
    }
    setFailed(false);
    setSubmitting(true);
    try {
      await onSubmit(trimmed);
      onOpenChange(false);
    } catch {
      setFailed(true);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-jb-card bg-jb-grey-95 text-jb-grey-10 ring-jb-grey-70/40">
        <DialogHeader>
          <DialogTitle className="text-jb-grey-5">{heading}</DialogTitle>
          <DialogDescription className="text-jb-grey-50">{description}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)} noValidate>
          <div className="flex flex-col gap-2">
            <Label htmlFor={inputId} className="text-[0.8125rem] font-medium text-jb-grey-30">
              {t('dashboard.fields.title')}
            </Label>
            <Input
              id={inputId}
              autoFocus
              maxLength={200}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              className="h-10 rounded-jb-card border-jb-grey-80 bg-jb-black/40 px-3 text-jb-grey-5 placeholder:text-jb-grey-60 focus-visible:border-jb-grey-50 focus-visible:ring-2 focus-visible:ring-jb-grey-30/15 dark:bg-jb-black/40"
              placeholder={t('dashboard.fields.titlePlaceholder')}
            />
            {failed ? (
              <p className="text-[0.8125rem] text-jb-danger" role="alert">
                {t('dashboard.errors.action')}
              </p>
            ) : null}
          </div>
          <DialogFooter className="border-jb-grey-80/60 bg-jb-black/30">
            <Button
              type="button"
              variant="outline"
              className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
              onClick={() => onOpenChange(false)}
            >
              {t('dashboard.actions.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={submitting || title.trim() === ''}
              className="rounded-jb-card bg-jb-grey-5 font-medium text-jb-black hover:bg-white disabled:opacity-45"
            >
              {submitLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
