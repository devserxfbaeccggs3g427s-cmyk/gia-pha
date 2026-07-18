import type { Metadata } from 'next';
import { headers } from 'next/headers';
import { AuthProvider } from '@/components/auth/auth-provider';
import { getTextDirection, isSupportedLocale } from '@/i18n/config';
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
      <body><AuthProvider>{children}</AuthProvider></body>
    </html>
  );
}
