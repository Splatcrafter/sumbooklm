import { useContext } from 'react';

import { ModelSettingsContext, type ModelSettingsContextValue } from '@/byok/modelSettingsContext';

/**
 * Returns the model settings and actions of the surrounding provider.
 */
export function useModelSettings(): ModelSettingsContextValue {
  const value = useContext(ModelSettingsContext);
  if (!value) {
    throw new Error('useModelSettings must be used inside a ModelSettingsProvider');
  }
  return value;
}
