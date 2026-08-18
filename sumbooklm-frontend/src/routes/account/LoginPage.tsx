import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';

import { AuthFailure } from '@/auth/authContext';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/button';
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { AuthCard } from '@/routes/account/AuthCard';
import {
  authErrorClasses,
  authInputClasses,
  authLabelClasses,
  authLinkClasses,
  authSubmitClasses,
} from '@/routes/account/authFormStyles';

/**
 * Signs an existing user in and stores the issued token pair.
 */
export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { login, status } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [failure, setFailure] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFailure(null);
    setSubmitting(true);
    try {
      await login(username, password);
      await navigate('/', { replace: true });
    } catch (error) {
      const reason = error instanceof AuthFailure ? error.reason : 'unexpected';
      setFailure(t(`account.errors.${reason}`));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title={t('account.login.title')}
      subtitle={t('account.login.subtitle')}
      footer={
        <>
          {t('account.login.noAccount')}{' '}
          <Link className={authLinkClasses} to="/account/register">
            {t('account.login.toRegister')}
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-6" onSubmit={(event) => void submit(event)} noValidate>
        <FieldGroup className="gap-4">
          <Field>
            <FieldLabel className={authLabelClasses} htmlFor="username">
              {t('account.fields.username')}
            </FieldLabel>
            <Input
              id="username"
              name="username"
              autoComplete="username"
              className={authInputClasses}
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel className={authLabelClasses} htmlFor="password">
              {t('account.fields.password')}
            </FieldLabel>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              className={authInputClasses}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>
          {failure ? (
            <p className={authErrorClasses} role="alert">
              {failure}
            </p>
          ) : null}
        </FieldGroup>
        <Button type="submit" className={authSubmitClasses} disabled={submitting}>
          {t('account.login.submit')}
        </Button>
      </form>
    </AuthCard>
  );
}
