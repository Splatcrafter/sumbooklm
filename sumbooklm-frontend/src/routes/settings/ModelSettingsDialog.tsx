import { useEffect, useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import {
  AI_PROVIDERS,
  EMPTY_MODEL_SETTINGS,
  PROVIDER_HINTS,
  isConfigured,
  requiresApiKey,
  type AiProvider,
  type ModelSettings,
} from '@/byok/modelSettings';
import { useModelSettings } from '@/byok/useModelSettings';
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
 * Asks which model answers the questions of this browser.
 *
 * The three providers are a row of buttons rather than a list to open, because there are three of
 * them and the choice decides which fields below are even shown. A local provider needs no key, so
 * the field for one disappears instead of being offered and ignored.
 *
 * The key is typed into a password field and is never read back out of the form: the dialog is filled
 * from the stored settings when it opens, and a user who wants to change providers replaces it rather
 * than editing it.
 */
export function ModelSettingsDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation();
  const { settings, save, forget } = useModelSettings();
  const modelId = useId();
  const apiKeyId = useId();
  const baseUrlId = useId();

  const [draft, setDraft] = useState<ModelSettings>(settings);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setDraft(settings);
      setSaving(false);
    }
  }, [open, settings]);

  function change(part: Partial<ModelSettings>) {
    setDraft((current) => ({ ...current, ...part }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    try {
      await save({
        provider: draft.provider,
        model: draft.model.trim(),
        apiKey: draft.apiKey.trim(),
        baseUrl: draft.baseUrl.trim(),
      });
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  }

  async function reset() {
    setSaving(true);
    try {
      await forget();
      setDraft(EMPTY_MODEL_SETTINGS);
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  }

  const hints = PROVIDER_HINTS[draft.provider];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-jb-card bg-jb-grey-95 text-jb-grey-10 ring-jb-grey-70/40 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-jb-grey-5">{t('settings.model.heading')}</DialogTitle>
          <DialogDescription className="text-jb-grey-50">
            {t('settings.model.description')}
          </DialogDescription>
        </DialogHeader>

        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)} noValidate>
          <fieldset className="flex flex-col gap-2">
            <legend className="pb-2 text-[0.8125rem] font-medium text-jb-grey-30">
              {t('settings.model.fields.provider')}
            </legend>
            <div className="grid grid-cols-3 gap-2">
              {AI_PROVIDERS.map((provider: AiProvider) => (
                <button
                  key={provider}
                  type="button"
                  aria-pressed={draft.provider === provider}
                  onClick={() => change({ provider })}
                  className={`rounded-jb-card px-3 py-2 text-[0.8125rem] font-medium transition-colors outline-none focus-visible:ring-2 focus-visible:ring-jb-grey-30/40 ${
                    draft.provider === provider
                      ? 'bg-jb-accent text-white'
                      : 'bg-jb-black/40 text-jb-grey-30 ring-1 ring-jb-grey-80 hover:bg-jb-grey-90'
                  }`}
                >
                  {t(`settings.model.providers.${provider}`)}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="flex flex-col gap-2">
            <Label htmlFor={modelId} className="text-[0.8125rem] font-medium text-jb-grey-30">
              {t('settings.model.fields.model')}
            </Label>
            <Input
              id={modelId}
              value={draft.model}
              onChange={(event) => change({ model: event.target.value })}
              placeholder={hints.model}
              autoComplete="off"
              className="h-10 rounded-jb-card border-jb-grey-80 bg-jb-black/40 px-3 text-jb-grey-5 placeholder:text-jb-grey-60 focus-visible:border-jb-grey-50 focus-visible:ring-2 focus-visible:ring-jb-grey-30/15 dark:bg-jb-black/40"
            />
          </div>

          {requiresApiKey(draft.provider) ? (
            <div className="flex flex-col gap-2">
              <Label htmlFor={apiKeyId} className="text-[0.8125rem] font-medium text-jb-grey-30">
                {t('settings.model.fields.apiKey')}
              </Label>
              <Input
                id={apiKeyId}
                type="password"
                value={draft.apiKey}
                onChange={(event) => change({ apiKey: event.target.value })}
                autoComplete="off"
                className="h-10 rounded-jb-card border-jb-grey-80 bg-jb-black/40 px-3 text-jb-grey-5 placeholder:text-jb-grey-60 focus-visible:border-jb-grey-50 focus-visible:ring-2 focus-visible:ring-jb-grey-30/15 dark:bg-jb-black/40"
              />
              <p className="text-xs leading-5 text-jb-grey-50">{t('settings.model.hints.apiKey')}</p>
            </div>
          ) : null}

          <div className="flex flex-col gap-2">
            <Label htmlFor={baseUrlId} className="text-[0.8125rem] font-medium text-jb-grey-30">
              {t('settings.model.fields.baseUrl')}
            </Label>
            <Input
              id={baseUrlId}
              type="url"
              inputMode="url"
              value={draft.baseUrl}
              onChange={(event) => change({ baseUrl: event.target.value })}
              placeholder={hints.baseUrl}
              autoComplete="off"
              className="h-10 rounded-jb-card border-jb-grey-80 bg-jb-black/40 px-3 text-jb-grey-5 placeholder:text-jb-grey-60 focus-visible:border-jb-grey-50 focus-visible:ring-2 focus-visible:ring-jb-grey-30/15 dark:bg-jb-black/40"
            />
          </div>

          <DialogFooter className="border-jb-grey-80/60 bg-jb-black/30">
            <Button
              type="button"
              variant="outline"
              className="mr-auto rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
              onClick={() => void reset()}
            >
              {t('settings.model.forget')}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
              onClick={() => onOpenChange(false)}
            >
              {t('settings.model.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={saving || !isConfigured(draft)}
              className="rounded-jb-card bg-jb-grey-5 font-medium text-jb-black hover:bg-white disabled:opacity-45"
            >
              {t('settings.model.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
