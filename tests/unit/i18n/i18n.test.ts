import en from '@/messages/en.json';
import vi from '@/messages/vi.json';
import { renderToStaticMarkup } from 'react-dom/server';
import { OriginalLanguageText } from '@/components/i18n/original-language-text';
import {
  getIntlLocale,
  getTextDirection,
  isSupportedLocale,
  localeDetails
} from '@/i18n/config';
import { formatCurrency, formatDate, formatDateTime, formatNumber } from '@/i18n/format';
import { routing } from '@/i18n/routing';

describe('internationalization configuration', () => {
  it('uses Vietnamese by default and supports Vietnamese and English', () => {
    expect(routing.defaultLocale).toBe('vi');
    expect(routing.locales).toEqual(['vi', 'en']);
    expect(isSupportedLocale('vi')).toBe(true);
    expect(isSupportedLocale('en')).toBe(true);
    expect(isSupportedLocale('fr')).toBe(false);
    expect(getIntlLocale('vi')).toBe('vi-VN');
    expect(getIntlLocale('en')).toBe('en-US');
  });

  it('keeps translation catalogs structurally complete', () => {
    expect(flattenKeys(en)).toEqual(flattenKeys(vi));
    expect(vi.auth.login.title).not.toBe(en.auth.login.title);
  });

  it('resolves LTR and future RTL locales safely', () => {
    expect(getTextDirection('vi')).toBe('ltr');
    expect(getTextDirection('en-US')).toBe('ltr');
    expect(getTextDirection('ar')).toBe('rtl');
    expect(getTextDirection('fa-IR')).toBe('rtl');
    expect(getTextDirection('not_a_locale')).toBe('ltr');
  });

  it('preserves user-authored names in their original language and direction', () => {
    const originalName = 'Nguyễn مُحَمَّد Smith';
    const markup = renderToStaticMarkup(OriginalLanguageText({ children: originalName }));

    expect(markup).toContain('dir="auto"');
    expect(markup).toContain('translate="no"');
    expect(markup).toContain(originalName);
  });
});

describe('locale-aware formatting', () => {
  const timestamp = '2026-07-18T04:30:00.000Z';

  it('formats dates and date-times for the selected locale', () => {
    expect(formatDate(timestamp, 'vi')).toBe('18/07/2026');
    expect(formatDate(timestamp, 'en')).toBe('07/18/2026');
    expect(formatDateTime(timestamp, 'vi')).toContain('11:30');
    expect(formatDateTime(timestamp, 'en')).toContain('11:30 AM');
  });

  it('formats numbers and currencies for the selected locale', () => {
    expect(formatNumber(1_234_567.89, 'vi')).toBe('1.234.567,89');
    expect(formatNumber(1_234_567.89, 'en')).toBe('1,234,567.89');
    expect(formatCurrency(1_234_567.89, 'vi')).toContain('₫');
    expect(formatCurrency(1_234_567.89, 'en')).toBe('$1,234,567.89');
    expect(localeDetails.vi.currency).toBe('VND');
    expect(localeDetails.en.currency).toBe('USD');
  });

  it('rejects invalid dates instead of rendering misleading output', () => {
    expect(() => formatDate('not-a-date', 'vi')).toThrow(RangeError);
  });
});

function flattenKeys(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return [prefix];

  return Object.entries(value)
    .flatMap(([key, child]) => flattenKeys(child, prefix ? `${prefix}.${key}` : key))
    .sort();
}
