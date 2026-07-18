import createNextIntlPlugin from 'next-intl/plugin';

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Keep the dev compiler's HMR chunks isolated from production builds. This
  // prevents webpack-runtime from retaining a reference to a chunk that a
  // concurrent `next build` has replaced in the shared output directory.
  distDir: process.env.NODE_ENV === 'development' ? '.next-dev' : '.next'
};

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

export default withNextIntl(nextConfig);
