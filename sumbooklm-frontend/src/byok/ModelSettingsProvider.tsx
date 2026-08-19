import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { useAuth } from '@/auth/useAuth';
import { EMPTY_MODEL_SETTINGS, isConfigured, type ModelSettings } from '@/byok/modelSettings';
import { ModelSettingsContext, type ModelSettingsContextValue } from '@/byok/modelSettingsContext';
import { clearModelSettings, readModelSettings, writeModelSettings } from '@/byok/modelSettingsStore';

/**
 * Holds the model the visitor answers their questions with.
 *
 * The settings are encrypted under the key of the current session, so they can only be read once the
 * session has been restored. Reading them is therefore tied to the authentication state rather than
 * to mounting, and signing out puts them out of reach along with everything else this browser stored.
 */
export function ModelSettingsProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const [settings, setSettings] = useState<ModelSettings>(EMPTY_MODEL_SETTINGS);
  const [restored, setRestored] = useState(false);

  useEffect(() => {
    if (status !== 'authenticated') {
      setSettings(EMPTY_MODEL_SETTINGS);
      setRestored(status === 'anonymous');
      return undefined;
    }

    let cancelled = false;
    void (async () => {
      const stored = await readModelSettings();
      if (cancelled) {
        return;
      }
      setSettings(stored ?? EMPTY_MODEL_SETTINGS);
      setRestored(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [status]);

  const save = useCallback(async (next: ModelSettings) => {
    setSettings(next);
    await writeModelSettings(next);
  }, []);

  const forget = useCallback(async () => {
    setSettings(EMPTY_MODEL_SETTINGS);
    await clearModelSettings();
  }, []);

  const value = useMemo<ModelSettingsContextValue>(
    () => ({ settings, configured: isConfigured(settings), restored, save, forget }),
    [forget, restored, save, settings],
  );

  return <ModelSettingsContext.Provider value={value}>{children}</ModelSettingsContext.Provider>;
}
