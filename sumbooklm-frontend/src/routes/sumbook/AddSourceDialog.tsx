import { useEffect, useId, useState, type DragEvent, type FormEvent } from 'react';
import { Upload } from 'lucide-react';
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { SourceRequestError } from '@/sources/sourcesApi';

type Tab = 'file' | 'url';

/**
 * Asks for the source that is to be added.
 *
 * The two ways in are tabs rather than two dialogs, because a user who opens the wrong one has to
 * change a tab instead of closing and reopening. Whichever tab is in front decides what the submit
 * button sends, so there is never a question which of the two values is being used.
 *
 * A rejected duplicate is reported as its own message. It is the one failure the user can act on:
 * the source is already in the Sumbook, so nothing is missing and nothing needs retrying.
 */
export function AddSourceDialog({
  open,
  onOpenChange,
  onAddFile,
  onAddLink,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onAddFile: (file: File) => Promise<void>;
  onAddLink: (url: string) => Promise<void>;
}) {
  const { t } = useTranslation();
  const fileInputId = useId();
  const urlInputId = useId();

  const [tab, setTab] = useState<Tab>('file');
  const [file, setFile] = useState<File | null>(null);
  const [url, setUrl] = useState('');
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<'none' | 'duplicate' | 'failed'>('none');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setTab('file');
      setFile(null);
      setUrl('');
      setDragging(false);
      setError('none');
      setSubmitting(false);
    }
  }, [open]);

  const ready = tab === 'file' ? file !== null : url.trim() !== '';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready) {
      return;
    }
    setError('none');
    setSubmitting(true);
    try {
      if (tab === 'file' && file) {
        await onAddFile(file);
      } else {
        await onAddLink(url.trim());
      }
      onOpenChange(false);
    } catch (failure) {
      setError(failure instanceof SourceRequestError && failure.status === 409 ? 'duplicate' : 'failed');
    } finally {
      setSubmitting(false);
    }
  }

  function drop(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    setDragging(false);
    const dropped = event.dataTransfer.files.item(0);
    if (dropped) {
      setFile(dropped);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-jb-card bg-jb-grey-95 text-jb-grey-10 ring-jb-grey-70/40 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-jb-grey-5">{t('sumbook.add.heading')}</DialogTitle>
          <DialogDescription className="text-jb-grey-50">{t('sumbook.add.description')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)} noValidate>
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList className="w-full bg-jb-black/40">
              <TabsTrigger value="file" className="data-active:bg-jb-grey-80 data-active:text-jb-grey-5">
                {t('sumbook.add.tabs.file')}
              </TabsTrigger>
              <TabsTrigger value="url" className="data-active:bg-jb-grey-80 data-active:text-jb-grey-5">
                {t('sumbook.add.tabs.url')}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="file" className="pt-2">
              <input
                id={fileInputId}
                type="file"
                className="sr-only"
                accept=".pdf,.txt,.md,.markdown,.html,.htm,text/plain,text/markdown,application/pdf"
                onChange={(event) => setFile(event.target.files?.item(0) ?? null)}
              />
              <Label
                htmlFor={fileInputId}
                onDragOver={(event) => {
                  event.preventDefault();
                  setDragging(true);
                }}
                onDragLeave={() => setDragging(false)}
                onDrop={drop}
                className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-jb-card border border-dashed px-4 py-8 text-center transition-colors ${
                  dragging
                    ? 'border-jb-grey-40 bg-jb-grey-90/60'
                    : 'border-jb-grey-70/70 bg-jb-black/25 hover:border-jb-grey-50'
                }`}
              >
                <span className="flex size-10 items-center justify-center rounded-full bg-jb-grey-90 ring-1 ring-jb-grey-70/50">
                  <Upload className="size-4 text-jb-grey-20" aria-hidden />
                </span>
                <span className="text-sm font-medium text-jb-grey-20">
                  {file ? file.name : t('sumbook.add.dropzone')}
                </span>
                <span className="text-xs text-jb-grey-50">{t('sumbook.add.dropzoneHint')}</span>
              </Label>
            </TabsContent>

            <TabsContent value="url" className="flex flex-col gap-2 pt-2">
              <Label htmlFor={urlInputId} className="text-[0.8125rem] font-medium text-jb-grey-30">
                {t('sumbook.add.fields.url')}
              </Label>
              <Input
                id={urlInputId}
                type="url"
                inputMode="url"
                maxLength={2000}
                value={url}
                onChange={(event) => setUrl(event.target.value)}
                placeholder={t('sumbook.add.fields.urlPlaceholder')}
                className="h-10 rounded-jb-card border-jb-grey-80 bg-jb-black/40 px-3 text-jb-grey-5 placeholder:text-jb-grey-60 focus-visible:border-jb-grey-50 focus-visible:ring-2 focus-visible:ring-jb-grey-30/15 dark:bg-jb-black/40"
              />
            </TabsContent>
          </Tabs>

          {error !== 'none' ? (
            <p className="text-[0.8125rem] text-jb-danger" role="alert">
              {t(error === 'duplicate' ? 'sumbook.errors.duplicate' : 'sumbook.errors.add')}
            </p>
          ) : null}

          <DialogFooter className="border-jb-grey-80/60 bg-jb-black/30">
            <Button
              type="button"
              variant="outline"
              className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
              onClick={() => onOpenChange(false)}
            >
              {t('sumbook.actions.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={submitting || !ready}
              className="rounded-jb-card bg-jb-grey-5 font-medium text-jb-black hover:bg-white disabled:opacity-45"
            >
              {t('sumbook.add.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
