import type { Metadata } from 'next';
import { headers } from 'next/headers';
import { AppProviders } from '@/components/providers/app-providers';
import { getTextDirection, isSupportedLocale } from '@/i18n/config';
import 'reactflow/dist/style.css';
import './globals.css';

export const metadata: Metadata = {
  title: 'Family Genealogy Management',
  description: 'Manage family trees, members, relationships, events, and media.',
  manifest: '/manifest.json',
  icons: {
    icon: [
      { url: '/icons/icon-192.svg', type: 'image/svg+xml', sizes: '192x192' },
      { url: '/icons/icon-512.svg', type: 'image/svg+xml', sizes: '512x512' }
    ],
    apple: { url: '/icons/icon-192.svg', type: 'image/svg+xml', sizes: '192x192' }
  },
  appleWebApp: {
    capable: true,
    title: 'Gia Phả',
    statusBarStyle: 'default'
  }
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
