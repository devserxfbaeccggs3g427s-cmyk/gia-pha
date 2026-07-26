import type { Metadata } from 'next';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages, getTranslations, setRequestLocale } from 'next-intl/server';
import { notFound } from 'next/navigation';
import { LocaleDocumentAttributes } from '@/components/i18n/locale-document-attributes';
import { DEFAULT_TIME_ZONE, formats, isSupportedLocale } from '@/i18n/config';
import { routing, type AppLocale } from '@/i18n/routing';
import { OfflineIndicator } from '@/components/pwa/offline-indicator';

interface LocaleLayoutProps {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}

export function generateStaticParams(): Array<{ locale: AppLocale }> {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: LocaleLayoutProps): Promise<Metadata> {
  const { locale } = await params;
  if (!isSupportedLocale(locale)) return {};
  const t = await getTranslations({ locale, namespace: 'app' });

  return {
    title: t('name'),
    description: t('description')
  };
}

export default async function LocaleLayout({ children, params }: LocaleLayoutProps) {
  const { locale } = await params;
  if (!isSupportedLocale(locale)) notFound();

  setRequestLocale(locale);
  const messages = await getMessages();

  return (
    <NextIntlClientProvider
      locale={locale}
      messages={messages}
      formats={formats}
      timeZone={DEFAULT_TIME_ZONE}
    >
      <LocaleDocumentAttributes locale={locale} />
      <OfflineIndicator />
      {children}
    </NextIntlClientProvider>
  );
}
