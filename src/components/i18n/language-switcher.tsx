'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useSearchParams } from 'next/navigation';
import { ChangeEvent, useEffect, useTransition } from 'react';
import { usePathname, useRouter } from '@/i18n/navigation';
import { isSupportedLocale, localeDetails } from '@/i18n/config';
import { routing, type AppLocale } from '@/i18n/routing';
import { useUiStore } from '@/store/ui-store';
import styles from './language-switcher.module.css';

export function LanguageSwitcher() {
  const locale = useLocale();
  const t = useTranslations('common');
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const setStoredLocale = useUiStore((state) => state.setLocale);

  useEffect(() => {
    if (isSupportedLocale(locale)) setStoredLocale(locale);
  }, [locale, setStoredLocale]);

  useEffect(() => {
    for (const availableLocale of routing.locales) {
      if (availableLocale !== locale) {
        router.prefetch(buildLocaleHref(pathname, searchParams, availableLocale), {
          locale: availableLocale
        });
      }
    }
  }, [locale, pathname, router, searchParams]);

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    const nextLocale = event.target.value;
    if (!isSupportedLocale(nextLocale) || nextLocale === locale) return;

    const href = buildLocaleHref(pathname, searchParams, nextLocale);
    setStoredLocale(nextLocale);
    startTransition(() => {
      router.replace(href, { locale: nextLocale, scroll: false });
    });
  }

  return (
    <label className={styles.switcher} aria-busy={isPending}>
      <span className={styles.srOnly}>{t('switchLanguage')}</span>
      <span className={styles.icon} aria-hidden="true">文</span>
      <select
        className={styles.select}
        value={locale}
        onChange={handleChange}
        disabled={isPending}
        aria-label={t('switchLanguage')}
      >
        {routing.locales.map((availableLocale) => (
          <option key={availableLocale} value={availableLocale}>
            {localeDetails[availableLocale].nativeName}
          </option>
        ))}
      </select>
    </label>
  );
}

function buildLocaleHref(
  pathname: string,
  searchParams: Pick<URLSearchParams, 'toString'>,
  locale: AppLocale
): string {
  const nextSearchParams = new URLSearchParams(searchParams.toString());
  const callbackUrl = nextSearchParams.get('callbackUrl');
  if (callbackUrl) {
    nextSearchParams.set(
      'callbackUrl',
      callbackUrl.replace(/^\/(vi|en)(?=\/|$)/, `/${locale}`)
    );
  }

  const query = nextSearchParams.toString();
  return query ? `${pathname}?${query}` : pathname;
}
