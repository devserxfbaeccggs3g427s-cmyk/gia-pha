'use client';

import { useTranslations } from 'next-intl';
import { FormEvent, useState } from 'react';
import { Link } from '@/i18n/navigation';
import styles from './auth.module.css';

export function RegisterForm() {
  const t = useTranslations('auth.register');
  const common = useTranslations('common');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    const form = new FormData(event.currentTarget);

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.get('name'),
          email: form.get('email'),
          password: form.get('password')
        })
      });
      const result = (await response.json()) as {
        ok: boolean;
        data?: { emailVerificationRequired?: boolean };
        error?: { code?: string };
      };

      if (response.ok) {
        setSuccessMessage(result.data?.emailVerificationRequired === false
          ? t('successReady')
          : t('successVerification'));
        event.currentTarget.reset();
      } else if (result.error?.code === 'EMAIL_ALREADY_EXISTS') {
        setError(t('errors.duplicate'));
      } else if (result.error?.code === 'VALIDATION_ERROR') {
        setError(t('errors.invalid'));
      } else {
        setError(t('errors.generic'));
      }
    } catch {
      setError(t('errors.generic'));
    } finally {
      setPending(false);
    }
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
          {successMessage && <p className={styles.message} role="status">{successMessage}</p>}
          {error && <p className={styles.error} role="alert">{error}</p>}

          <form className={styles.form} onSubmit={handleSubmit}>
            <label className={styles.field}>
              <span className={styles.label}>{t('name')}</span>
              <input className={styles.input} name="name" autoComplete="name" minLength={2} maxLength={100} required />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>{t('email')}</span>
              <input className={styles.input} name="email" type="email" autoComplete="email" required />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>{t('password')}</span>
              <input className={styles.input} name="password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required />
              <span className={styles.passwordHint}>{t('passwordHint')}</span>
            </label>
            <button className={styles.button} disabled={pending || Boolean(successMessage)} type="submit">
              {pending ? t('submitting') : t('submit')}
            </button>
          </form>

          <p className={styles.footnote}>{t('hasAccount')} <Link className={styles.link} href="/login">{t('login')}</Link></p>
        </div>
      </section>
    </div>
  );
}
