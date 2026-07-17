'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import styles from './auth.module.css';

interface RegisterFormProps {
  locale: 'vi' | 'en';
}

const copy = {
  vi: {
    eyebrow: 'Bắt đầu từ hôm nay',
    visualTitle: 'Gìn giữ những điều làm nên gia đình.',
    visualText: 'Tạo không gian riêng tư để lưu giữ con người, câu chuyện và những cột mốc đáng nhớ.',
    title: 'Tạo tài khoản',
    subtitle: 'Bắt đầu xây dựng gia phả an toàn của gia đình bạn.',
    name: 'Họ và tên',
    email: 'Địa chỉ email',
    password: 'Mật khẩu',
    hint: 'Tối thiểu 12 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.',
    submit: 'Tạo tài khoản',
    submitting: 'Đang tạo tài khoản…',
    hasAccount: 'Đã có tài khoản?',
    login: 'Đăng nhập',
    success: 'Tài khoản đã được tạo. Hãy kiểm tra email để xác nhận trước khi đăng nhập.',
    duplicate: 'Email này đã được sử dụng.',
    invalid: 'Vui lòng kiểm tra lại thông tin và độ mạnh mật khẩu.',
    generic: 'Không thể tạo tài khoản lúc này. Vui lòng thử lại.'
  },
  en: {
    eyebrow: 'Start today',
    visualTitle: 'Preserve what makes your family unique.',
    visualText: 'Create a private place for the people, stories, and milestones that matter.',
    title: 'Create your account',
    subtitle: "Start building your family's secure genealogy.",
    name: 'Full name',
    email: 'Email address',
    password: 'Password',
    hint: 'At least 12 characters with uppercase, lowercase, number, and special character.',
    submit: 'Create account',
    submitting: 'Creating account…',
    hasAccount: 'Already have an account?',
    login: 'Sign in',
    success: 'Your account is ready. Check your email and verify it before signing in.',
    duplicate: 'This email is already registered.',
    invalid: 'Please check your details and password strength.',
    generic: 'Unable to create your account right now. Please try again.'
  }
} as const;

export function RegisterForm({ locale }: RegisterFormProps) {
  const text = copy[locale];
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
        data?: { message?: string };
        error?: { code?: string };
      };

      if (response.ok) {
        setSuccessMessage(result.data?.message ?? text.success);
        event.currentTarget.reset();
      } else if (result.error?.code === 'EMAIL_ALREADY_EXISTS') {
        setError(text.duplicate);
      } else if (result.error?.code === 'VALIDATION_ERROR') {
        setError(text.invalid);
      } else {
        setError(text.generic);
      }
    } catch {
      setError(text.generic);
    } finally {
      setPending(false);
    }
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
          {successMessage && <p className={styles.message} role="status">{successMessage}</p>}
          {error && <p className={styles.error} role="alert">{error}</p>}

          <form className={styles.form} onSubmit={handleSubmit}>
            <label className={styles.field}>
              <span className={styles.label}>{text.name}</span>
              <input className={styles.input} name="name" autoComplete="name" minLength={2} maxLength={100} required />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>{text.email}</span>
              <input className={styles.input} name="email" type="email" autoComplete="email" required />
            </label>
            <label className={styles.field}>
              <span className={styles.label}>{text.password}</span>
              <input className={styles.input} name="password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required />
              <span className={styles.passwordHint}>{text.hint}</span>
            </label>
            <button className={styles.button} disabled={pending || Boolean(successMessage)} type="submit">
              {pending ? text.submitting : text.submit}
            </button>
          </form>

          <p className={styles.footnote}>{text.hasAccount} <Link className={styles.link} href={`/${locale}/login`}>{text.login}</Link></p>
        </div>
      </section>
    </div>
  );
}
