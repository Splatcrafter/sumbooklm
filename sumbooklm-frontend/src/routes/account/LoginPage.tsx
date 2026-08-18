import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';

import { AuthFailure } from '@/auth/authContext';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/button';
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { AccountShell } from '@/routes/account/AccountShell';

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
    <AccountShell
      title={t('account.login.title')}
      subtitle={t('account.login.subtitle')}
      footer={
        <>
          {t('account.login.noAccount')}{' '}
          <Link className="underline underline-offset-4" to="/account/register">
            {t('account.login.toRegister')}
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-6" onSubmit={(event) => void submit(event)} noValidate>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="username">{t('account.fields.username')}</FieldLabel>
            <Input
              id="username"
              name="username"
              autoComplete="username"
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="password">{t('account.fields.password')}</FieldLabel>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>
          {failure ? <FieldError>{failure}</FieldError> : null}
        </FieldGroup>
        <Button type="submit" size="lg" disabled={submitting}>
          {t('account.login.submit')}
        </Button>
      </form>
    </AccountShell>
  );
}
