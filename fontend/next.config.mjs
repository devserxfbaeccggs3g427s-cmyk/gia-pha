import createNextIntlPlugin from 'next-intl/plugin';

const SPRING_BASE_URL = process.env.NEXT_PUBLIC_GIAPHA_SPRING_BASE_URL
  ?? process.env.GIAPHA_SPRING_BASE_URL
  ?? 'http://127.0.0.1:8080';

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  compress: true,
  poweredByHeader: false,
  // Task 36.1: selected API domains are proxied to Spring while public
  // routes are preserved. Public paths (login, share, marketing pages) are
  // untouched — the proxy only covers /api/internal and /api/public/share
  // once the cutover is active.
  async rewrites() {
    return [
      {
        source: '/api/spring/:path*',
        destination: `${SPRING_BASE_URL}/api/:path*`
      },
      {
        source: '/api/internal/:path*',
        destination: `${SPRING_BASE_URL}/api/internal/:path*`
      },
      {
        source: '/api/public/share/:path*',
        destination: `${SPRING_BASE_URL}/api/public/share/:path*`
      }
    ];
  },
  images: {
    // The optimizer negotiates WebP and emits a responsive srcset for every
    // next/image instance. Imported avatars can originate outside Blob, so
    // HTTPS is accepted while Next.js still rejects private IP ranges.
    formats: ['image/webp'],
    deviceSizes: [360, 640, 768, 1024, 1280, 1536, 1920],
    imageSizes: [32, 48, 64, 96, 128, 256, 384],
    minimumCacheTTL: 86400,
    remotePatterns: [{ protocol: 'https', hostname: '**' }]
  },
  experimental: {
    optimizePackageImports: ['lucide-react', 'reactflow']
  },
  // Keep the dev compiler's HMR chunks isolated from production builds. This
  // prevents webpack-runtime from retaining a reference to a chunk that a
  // concurrent `next build` has replaced in the shared output directory.
  distDir: process.env.NODE_ENV === 'development' ? '.next-dev' : '.next',
  async headers() {
    return [
      {
        source: '/sw.js',
        headers: [{ key: 'Cache-Control', value: 'no-cache, no-store, must-revalidate' }]
      },
      {
        source: '/manifest.json',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=3600, must-revalidate' }]
      },
      {
        source: '/icons/:path*',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=31536000, immutable' }]
      },
      {
        source: '/offline.html',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=3600, stale-while-revalidate=86400' }]
      }
    ];
  }
};

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

export default withNextIntl(nextConfig);
