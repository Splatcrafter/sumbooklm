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
      <DialogContent className="rounded-3xl border border-nb-line bg-nb-surface text-nb-text ring-0">
        <DialogHeader>
          <DialogTitle className="text-nb-text">{heading}</DialogTitle>
          <DialogDescription className="text-nb-muted">{description}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)} noValidate>
          <div className="flex flex-col gap-2">
            <Label htmlFor={inputId} className="text-[0.8125rem] font-medium text-nb-body">
              {t('dashboard.fields.title')}
            </Label>
            <Input
              id={inputId}
              autoFocus
              maxLength={200}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              className="h-11 rounded-nb-tile border-nb-line bg-nb-inset px-3.5 text-nb-text placeholder:text-nb-muted focus-visible:border-nb-accent focus-visible:ring-0 dark:bg-nb-inset"
              placeholder={t('dashboard.fields.titlePlaceholder')}
            />
            {failed ? (
              <p className="text-[0.8125rem] text-nb-danger" role="alert">
                {t('dashboard.errors.action')}
              </p>
            ) : null}
          </div>
          <DialogFooter className="border-nb-hover/60 bg-nb-ground/30">
            <Button
              type="button"
              variant="outline"
              className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
              onClick={() => onOpenChange(false)}
            >
              {t('dashboard.actions.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={submitting || title.trim() === ''}
              className="rounded-full bg-nb-primary font-medium text-nb-on-primary hover:brightness-90 disabled:opacity-45"
            >
              {submitLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
