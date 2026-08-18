import { useContext } from 'react';

import { AuthContext, type AuthContextValue } from '@/auth/authContext';

/**
 * Returns the authentication state and actions of the surrounding provider.
 */
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return value;
}
