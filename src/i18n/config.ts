import type { Formats } from 'next-intl';
import type { AppLocale } from './routing';

export type TextDirection = 'ltr' | 'rtl';

export const DEFAULT_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export const localeDetails: Record<
  AppLocale,
  {
    label: string;
    nativeName: string;
    intlLocale: string;
    currency: string;
  }
> = {
  vi: {
    label: 'Vietnamese',
    nativeName: 'Tiếng Việt',
    intlLocale: 'vi-VN',
    currency: 'VND'
  },
  en: {
    label: 'English',
    nativeName: 'English',
    intlLocale: 'en-US',
    currency: 'USD'
  }
};

export const formats = {
  dateTime: {
    short: {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    },
    medium: {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    },
    long: {
      day: '2-digit',
      month: 'long',
      year: 'numeric'
    },
    dateTime: {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }
  },
  number: {
    integer: {
      maximumFractionDigits: 0
    },
    decimal: {
      maximumFractionDigits: 2
    },
    percent: {
      style: 'percent',
      maximumFractionDigits: 1
    }
  }
} satisfies Formats;

export function isSupportedLocale(locale: string | null | undefined): locale is AppLocale {
  return locale === 'vi' || locale === 'en';
}

export function getIntlLocale(locale: AppLocale): string {
  return localeDetails[locale].intlLocale;
}

/**
 * Resolves writing direction from a BCP 47 locale. The fallback list keeps RTL
 * working in runtimes that do not yet expose Intl.Locale#getTextInfo.
 */
export function getTextDirection(locale: string): TextDirection {
  try {
    const localeWithDirection = new Intl.Locale(locale) as Intl.Locale & {
      textInfo?: { direction: TextDirection };
      getTextInfo?: () => { direction: TextDirection };
    };
    const textInfo = localeWithDirection.getTextInfo?.() ?? localeWithDirection.textInfo;
    if (textInfo?.direction === 'rtl') return 'rtl';
    if (textInfo?.direction === 'ltr') return 'ltr';
  } catch {
    // Fall through to the language-based fallback for malformed/older locales.
  }

  const language = locale.toLowerCase().split(/[-_]/, 1)[0];
  return ['ar', 'ckb', 'dv', 'fa', 'he', 'ku', 'ps', 'sd', 'ug', 'ur', 'yi'].includes(language)
    ? 'rtl'
    : 'ltr';
}
