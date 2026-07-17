'use client';

import Link from 'next/link';
import { signIn } from 'next-auth/react';
import { FormEvent, useState } from 'react';
import styles from './auth.module.css';

interface LoginFormProps {
  locale: 'vi' | 'en';
  callbackUrl?: string;
  emailVerified: boolean;
  verificationError: boolean;
  oauth: { google: boolean; facebook: boolean };
}

const copy = {
  vi: {
    eyebrow: 'Di sản gia đình',
    visualTitle: 'Mỗi thế hệ, một câu chuyện.',
    visualText: 'Gìn giữ ký ức, kết nối người thân và trao lại lịch sử gia đình cho những thế hệ mai sau.',
    title: 'Chào mừng trở lại',
    subtitle: 'Đăng nhập để tiếp tục hành trình gia đình của bạn.',
    email: 'Địa chỉ email',
    password: 'Mật khẩu',
    submit: 'Đăng nhập',
    submitting: 'Đang xác thực…',
    or: 'hoặc tiếp tục với',
    noAccount: 'Chưa có tài khoản?',
    register: 'Tạo tài khoản',
    verified: 'Email đã được xác nhận. Bạn có thể đăng nhập ngay bây giờ.',
    invalidToken: 'Liên kết xác nhận không hợp lệ hoặc đã hết hạn.',
    invalid: 'Email hoặc mật khẩu không chính xác.',
    locked: 'Tài khoản đã bị khóa trong 15 phút do đăng nhập sai quá 5 lần.',
    unverified: 'Vui lòng xác nhận email trước khi đăng nhập.',
    generic: 'Không thể đăng nhập lúc này. Vui lòng thử lại.'
  },
  en: {
    eyebrow: 'Family legacy',
    visualTitle: 'Every generation has a story.',
    visualText: 'Preserve memories, connect relatives, and pass your family history on to generations to come.',
    title: 'Welcome back',
    subtitle: 'Sign in to continue your family journey.',
    email: 'Email address',
    password: 'Password',
    submit: 'Sign in',
    submitting: 'Authenticating…',
    or: 'or continue with',
    noAccount: 'New here?',
    register: 'Create an account',
    verified: 'Your email is verified. You can now sign in.',
    invalidToken: 'The verification link is invalid or has expired.',
    invalid: 'The email or password is incorrect.',
    locked: 'Your account is locked for 15 minutes after 5 failed sign-in attempts.',
    unverified: 'Please verify your email before signing in.',
    generic: 'Unable to sign in right now. Please try again.'
  }
} as const;

export function LoginForm(props: LoginFormProps) {
  const text = copy[props.locale];
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
        ACCOUNT_LOCKED: text.locked,
        EMAIL_NOT_VERIFIED: text.unverified,
        CredentialsSignin: text.invalid
      };
      setError(errors[result?.error ?? ''] ?? text.generic);
    } catch {
      setError(text.generic);
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
          <p className={styles.eyebrow}>{text.eyebrow}</p>
          <h2 className={styles.visualTitle}>{text.visualTitle}</h2>
          <p className={styles.visualText}>{text.visualText}</p>
        </div>
      </section>
      <section className={styles.panel}>
        <div className={styles.card}>
          <div className={styles.brand}><span className={styles.mark}>G</span> Gia Phả</div>
          <h1 className={styles.title}>{text.title}</h1>
          <p className={styles.subtitle}>{text.subtitle}</p>
          {props.emailVerified && <p className={styles.message} role="status">{text.verified}</p>}
          {props.verificationError && <p className={styles.error} role="alert">{text.invalidToken}</p>}
          {error && <p className={styles.error} role="alert">{error}</p>}

          <form className={styles.form} onSubmit={handleSubmit}>
            <label className={styles.field}>
              <span className={styles.label}>{text.email}</span>
              <input className={styles.input} name="email" type="email" autoComplete="email" required />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>{text.password}</span>
              <input className={styles.input} name="password" type="password" autoComplete="current-password" required />
            </label>
            <button className={styles.button} disabled={pending} type="submit">
              {pending ? text.submitting : text.submit}
            </button>
          </form>

          {(props.oauth.google || props.oauth.facebook) && (
            <>
              <div className={styles.divider}>{text.or}</div>
              <div className={styles.oauthGrid}>
                {props.oauth.google && <button className={styles.oauthButton} disabled={pending} onClick={() => handleOAuth('google')} type="button">Google</button>}
                {props.oauth.facebook && <button className={styles.oauthButton} disabled={pending} onClick={() => handleOAuth('facebook')} type="button">Facebook</button>}
              </div>
            </>
          )}

          <p className={styles.footnote}>{text.noAccount} <Link className={styles.link} href={`/${props.locale}/register`}>{text.register}</Link></p>
        </div>
      </section>
    </div>
  );
}

