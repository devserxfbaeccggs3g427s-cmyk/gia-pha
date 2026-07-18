import type { Metadata } from 'next';
import { headers } from 'next/headers';
import { AppProviders } from '@/components/providers/app-providers';
import { getTextDirection, isSupportedLocale } from '@/i18n/config';
import 'reactflow/dist/style.css';
import './globals.css';

export const metadata: Metadata = {
  title: 'Family Genealogy Management',
  description: 'Manage family trees, members, relationships, events, and media.'
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  const requestLocale = headers().get('x-next-intl-locale');
  const locale = isSupportedLocale(requestLocale) ? requestLocale : 'vi';

  return (
    <html lang={locale} dir={getTextDirection(locale)} suppressHydrationWarning>
      <body><AppProviders>{children}</AppProviders></body>
    </html>
  );
}
