'use client';

import { useTranslations } from 'next-intl';
import { signIn } from 'next-auth/react';
import { FormEvent, useState } from 'react';
import { Link } from '@/i18n/navigation';
import styles from './auth.module.css';

interface LoginFormProps {
  callbackUrl?: string;
  emailVerified: boolean;
  verificationError: boolean;
  oauth: { google: boolean; facebook: boolean };
}

export function LoginForm(props: LoginFormProps) {
  const t = useTranslations('auth.login');
  const common = useTranslations('common');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const callbackUrl = props.callbackUrl?.startsWith('/') ? props.callbackUrl : '/';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    const form = new FormData(event.currentTarget);

    try {
      const result = await signIn('credentials', {
        email: String(form.get('email') ?? ''),
        password: String(form.get('password') ?? ''),
        callbackUrl,
        redirect: false
      });

      if (result?.ok) {
        window.location.assign(result.url ?? callbackUrl);
        return;
      }

      const errors: Record<string, string> = {
        ACCOUNT_LOCKED: t('errors.locked'),
        EMAIL_NOT_VERIFIED: t('errors.unverified'),
        CredentialsSignin: t('errors.invalid')
      };
      setError(errors[result?.error ?? ''] ?? t('errors.generic'));
    } catch {
      setError(t('errors.generic'));
    } finally {
      setPending(false);
    }
  }

  function handleOAuth(provider: 'google' | 'facebook') {
    setPending(true);
    void signIn(provider, { callbackUrl });
  }

  return (
    <div className={styles.page}>
      <section className={styles.visual} aria-hidden="true">
        <div className={styles.visualContent}>
          <p className={styles.eyebrow}>{t('eyebrow')}</p>
          <h2 className={styles.visualTitle}>{t('visualTitle')}</h2>
          <p className={styles.visualText}>{t('visualText')}</p>
        </div>
      </section>
      <section className={styles.panel}>
        <div className={styles.card}>
          <div className={styles.brand}><span className={styles.mark}>G</span> {common('brand')}</div>
          <h1 className={styles.title}>{t('title')}</h1>
          <p className={styles.subtitle}>{t('subtitle')}</p>
          {props.emailVerified && <p className={styles.message} role="status">{t('verified')}</p>}
          {props.verificationError && <p className={styles.error} role="alert">{t('invalidToken')}</p>}
          {error && <p className={styles.error} role="alert">{error}</p>}

          <form className={styles.form} onSubmit={handleSubmit}>
            <label className={styles.field}>
              <span className={styles.label}>{t('email')}</span>
              <input className={styles.input} name="email" type="email" autoComplete="email" required />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>{t('password')}</span>
              <input className={styles.input} name="password" type="password" autoComplete="current-password" required />
            </label>
            <button className={styles.button} disabled={pending} type="submit">
              {pending ? t('submitting') : t('submit')}
            </button>
          </form>

          {(props.oauth.google || props.oauth.facebook) && (
            <>
              <div className={styles.divider}>{t('or')}</div>
              <div className={styles.oauthGrid}>
                {props.oauth.google && <button className={styles.oauthButton} disabled={pending} onClick={() => handleOAuth('google')} type="button">Google</button>}
                {props.oauth.facebook && <button className={styles.oauthButton} disabled={pending} onClick={() => handleOAuth('facebook')} type="button">Facebook</button>}
              </div>
            </>
          )}

          <p className={styles.footnote}>{t('noAccount')} <Link className={styles.link} href="/register">{t('register')}</Link></p>
        </div>
      </section>
    </div>
  );
}
