import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';

import { AuthFailure } from '@/auth/authContext';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/button';
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { AccountShell } from '@/routes/account/AccountShell';

/**
 * Minimum password length, mirroring the constraint the backend validates against.
 */
const MINIMUM_PASSWORD_LENGTH = 12;

/**
 * Creates an account and adopts the token pair the backend returns for it.
 */
export function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { register, status } = useAuth();

  const [form, setForm] = useState({ username: '', firstName: '', lastName: '', password: '' });
  const [failure, setFailure] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  function update(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFailure(null);
    setSubmitting(true);
    try {
      await register(form);
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
      title={t('account.register.title')}
      subtitle={t('account.register.subtitle')}
      footer={
        <>
          {t('account.register.hasAccount')}{' '}
          <Link className="underline underline-offset-4" to="/account/login">
            {t('account.register.toLogin')}
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
              value={form.username}
              onChange={(event) => update('username', event.target.value)}
            />
          </Field>
          <Field orientation="responsive">
            <Field>
              <FieldLabel htmlFor="firstName">{t('account.fields.firstName')}</FieldLabel>
              <Input
                id="firstName"
                name="firstName"
                autoComplete="given-name"
                required
                value={form.firstName}
                onChange={(event) => update('firstName', event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="lastName">{t('account.fields.lastName')}</FieldLabel>
              <Input
                id="lastName"
                name="lastName"
                autoComplete="family-name"
                required
                value={form.lastName}
                onChange={(event) => update('lastName', event.target.value)}
              />
            </Field>
          </Field>
          <Field>
            <FieldLabel htmlFor="password">{t('account.fields.password')}</FieldLabel>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={MINIMUM_PASSWORD_LENGTH}
              required
              value={form.password}
              onChange={(event) => update('password', event.target.value)}
            />
            <FieldDescription>
              {t('account.hints.password', { length: MINIMUM_PASSWORD_LENGTH })}
            </FieldDescription>
          </Field>
          {failure ? <FieldError>{failure}</FieldError> : null}
        </FieldGroup>
        <Button type="submit" size="lg" disabled={submitting}>
          {t('account.register.submit')}
        </Button>
      </form>
    </AccountShell>
  );
}
