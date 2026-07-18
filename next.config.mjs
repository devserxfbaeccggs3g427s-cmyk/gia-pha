import createNextIntlPlugin from 'next-intl/plugin';

/** @type {import('next').NextConfig} */
const allowedDevOrigins = new Set(['localhost', '127.0.0.1']);

if (process.env.NEXTAUTH_URL) {
  try {
    allowedDevOrigins.add(new URL(process.env.NEXTAUTH_URL).hostname);
  } catch {
    // NEXTAUTH_URL is validated by NextAuth at runtime; keep the dev server usable
    // even if a developer is still configuring the local environment.
  }
}

const nextConfig = {
  reactStrictMode: true,
  // Permit the LAN hostname used by NEXTAUTH_URL during local device testing.
  allowedDevOrigins: [...allowedDevOrigins]
};

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

export default withNextIntl(nextConfig);
