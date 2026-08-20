/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
      <DialogContent className="rounded-3xl border border-nb-line bg-nb-surface text-nb-text ring-0 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-nb-text">{t('sumbook.add.heading')}</DialogTitle>
          <DialogDescription className="text-nb-muted">{t('sumbook.add.description')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)} noValidate>
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList className="w-full bg-nb-ground/40">
              <TabsTrigger value="file" className="data-active:bg-nb-hover data-active:text-nb-text">
                {t('sumbook.add.tabs.file')}
              </TabsTrigger>
              <TabsTrigger value="url" className="data-active:bg-nb-hover data-active:text-nb-text">
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
                className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-nb-tile border border-dashed px-4 py-8 text-center transition-colors ${
                  dragging
                    ? 'border-nb-muted bg-nb-surface/60'
                    : 'border-nb-line/70 bg-nb-ground/25 hover:border-nb-muted'
                }`}
              >
                <span className="flex size-10 items-center justify-center rounded-full bg-nb-surface ring-1 ring-nb-line/50">
                  <Upload className="size-4 text-nb-body" aria-hidden />
                </span>
                <span className="text-sm font-medium text-nb-body">
                  {file ? file.name : t('sumbook.add.dropzone')}
                </span>
                <span className="text-xs text-nb-muted">{t('sumbook.add.dropzoneHint')}</span>
              </Label>
            </TabsContent>

            <TabsContent value="url" className="flex flex-col gap-2 pt-2">
              <Label htmlFor={urlInputId} className="text-[0.8125rem] font-medium text-nb-body">
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
                className="h-11 rounded-nb-tile border-nb-line bg-nb-inset px-3.5 text-nb-text placeholder:text-nb-muted focus-visible:border-nb-accent focus-visible:ring-0 dark:bg-nb-inset"
              />
            </TabsContent>
          </Tabs>

          {error !== 'none' ? (
            <p className="text-[0.8125rem] text-nb-danger" role="alert">
              {t(error === 'duplicate' ? 'sumbook.errors.duplicate' : 'sumbook.errors.add')}
            </p>
          ) : null}

          <DialogFooter className="border-nb-hover/60 bg-nb-ground/30">
            <Button
              type="button"
              variant="outline"
              className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
              onClick={() => onOpenChange(false)}
            >
              {t('sumbook.actions.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={submitting || !ready}
              className="rounded-full bg-nb-primary font-medium text-nb-on-primary hover:brightness-90 disabled:opacity-45"
            >
              {t('sumbook.add.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
