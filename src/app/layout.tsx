import type { Metadata } from 'next';
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
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
