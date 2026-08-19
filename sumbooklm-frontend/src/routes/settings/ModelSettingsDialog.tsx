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
      <DialogContent className="rounded-3xl border border-nb-line bg-nb-surface text-nb-text ring-0 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-nb-text">{t('settings.model.heading')}</DialogTitle>
          <DialogDescription className="text-nb-muted">
            {t('settings.model.description')}
          </DialogDescription>
        </DialogHeader>

        <form className="flex flex-col gap-4" onSubmit={(event) => void submit(event)} noValidate>
          <fieldset className="flex flex-col gap-2">
            <legend className="pb-2 text-[0.8125rem] font-medium text-nb-body">
              {t('settings.model.fields.provider')}
            </legend>
            <div className="grid grid-cols-3 gap-2">
              {AI_PROVIDERS.map((provider: AiProvider) => (
                <button
                  key={provider}
                  type="button"
                  aria-pressed={draft.provider === provider}
                  onClick={() => change({ provider })}
                  className={`rounded-full px-3 py-2 text-[0.8125rem] font-medium transition-colors outline-none focus-visible:ring-2 focus-visible:ring-nb-body/40 ${
                    draft.provider === provider
                      ? 'bg-nb-accent-container text-nb-accent'
                      : 'bg-nb-ground/40 text-nb-body ring-1 ring-nb-hover hover:bg-nb-surface'
                  }`}
                >
                  {t(`settings.model.providers.${provider}`)}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="flex flex-col gap-2">
            <Label htmlFor={modelId} className="text-[0.8125rem] font-medium text-nb-body">
              {t('settings.model.fields.model')}
            </Label>
            <Input
              id={modelId}
              value={draft.model}
              onChange={(event) => change({ model: event.target.value })}
              placeholder={hints.model}
              autoComplete="off"
              className="h-11 rounded-nb-tile border-nb-line bg-nb-inset px-3.5 text-nb-text placeholder:text-nb-muted focus-visible:border-nb-accent focus-visible:ring-0 dark:bg-nb-inset"
            />
          </div>

          {requiresApiKey(draft.provider) ? (
            <div className="flex flex-col gap-2">
              <Label htmlFor={apiKeyId} className="text-[0.8125rem] font-medium text-nb-body">
                {t('settings.model.fields.apiKey')}
              </Label>
              <Input
                id={apiKeyId}
                type="password"
                value={draft.apiKey}
                onChange={(event) => change({ apiKey: event.target.value })}
                autoComplete="off"
                className="h-11 rounded-nb-tile border-nb-line bg-nb-inset px-3.5 text-nb-text placeholder:text-nb-muted focus-visible:border-nb-accent focus-visible:ring-0 dark:bg-nb-inset"
              />
              <p className="text-xs leading-5 text-nb-muted">{t('settings.model.hints.apiKey')}</p>
            </div>
          ) : null}

          <div className="flex flex-col gap-2">
            <Label htmlFor={baseUrlId} className="text-[0.8125rem] font-medium text-nb-body">
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
              className="h-11 rounded-nb-tile border-nb-line bg-nb-inset px-3.5 text-nb-text placeholder:text-nb-muted focus-visible:border-nb-accent focus-visible:ring-0 dark:bg-nb-inset"
            />
          </div>

          <DialogFooter className="border-nb-hover/60 bg-nb-ground/30">
            <Button
              type="button"
              variant="outline"
              className="mr-auto rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
              onClick={() => void reset()}
            >
              {t('settings.model.forget')}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
              onClick={() => onOpenChange(false)}
            >
              {t('settings.model.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={saving || !isConfigured(draft)}
              className="rounded-full bg-nb-primary font-medium text-nb-on-primary hover:brightness-90 disabled:opacity-45"
            >
              {t('settings.model.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
