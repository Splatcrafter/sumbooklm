import { createContext } from 'react';

import type { ModelSettings } from '@/byok/modelSettings';

/**
 * The model settings of the current visitor and the actions that change them.
 */
export interface ModelSettingsContextValue {
  settings: ModelSettings;
  /** Whether questions can be asked with the current settings. */
  configured: boolean;
  /** Whether the stored settings have been read yet. */
  restored: boolean;
  save: (settings: ModelSettings) => Promise<void>;
  forget: () => Promise<void>;
}

export const ModelSettingsContext = createContext<ModelSettingsContextValue | null>(null);
