import type { Metadata } from 'next';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages, getTranslations, setRequestLocale } from 'next-intl/server';
import { notFound } from 'next/navigation';
import { LocaleDocumentAttributes } from '@/components/i18n/locale-document-attributes';
import { DEFAULT_TIME_ZONE, formats, isSupportedLocale } from '@/i18n/config';
import { routing, type AppLocale } from '@/i18n/routing';

interface LocaleLayoutProps {
  children: React.ReactNode;
  params: { locale: string };
}

export function generateStaticParams(): Array<{ locale: AppLocale }> {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: LocaleLayoutProps): Promise<Metadata> {
  if (!isSupportedLocale(params.locale)) return {};
  const t = await getTranslations({ locale: params.locale, namespace: 'app' });

  return {
    title: t('name'),
    description: t('description')
  };
}

export default async function LocaleLayout({ children, params }: LocaleLayoutProps) {
  if (!isSupportedLocale(params.locale)) notFound();

  setRequestLocale(params.locale);
  const messages = await getMessages();

  return (
    <NextIntlClientProvider
      locale={params.locale}
      messages={messages}
      formats={formats}
      timeZone={DEFAULT_TIME_ZONE}
    >
      <LocaleDocumentAttributes locale={params.locale} />
      {children}
    </NextIntlClientProvider>
  );
}
