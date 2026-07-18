import { DEFAULT_TIME_ZONE, getIntlLocale, localeDetails } from './config';
import type { AppLocale } from './routing';

export type DateInput = Date | number | string;

export function formatDate(
  value: DateInput,
  locale: AppLocale,
  options: Intl.DateTimeFormatOptions = {}
): string {
  return new Intl.DateTimeFormat(getIntlLocale(locale), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: DEFAULT_TIME_ZONE,
    ...options
  }).format(toDate(value));
}

export function formatDateTime(
  value: DateInput,
  locale: AppLocale,
  options: Intl.DateTimeFormatOptions = {}
): string {
  return formatDate(value, locale, {
    hour: '2-digit',
    minute: '2-digit',
    ...options
  });
}

export function formatNumber(
  value: number | bigint,
  locale: AppLocale,
  options: Intl.NumberFormatOptions = {}
): string {
  return new Intl.NumberFormat(getIntlLocale(locale), options).format(value);
}

export function formatCurrency(
  value: number | bigint,
  locale: AppLocale,
  currency = localeDetails[locale].currency,
  options: Omit<Intl.NumberFormatOptions, 'style' | 'currency'> = {}
): string {
  return formatNumber(value, locale, {
    style: 'currency',
    currency,
    ...options
  });
}

function toDate(value: DateInput): Date {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new RangeError('Invalid date value');
  return date;
}
