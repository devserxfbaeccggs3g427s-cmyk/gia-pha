import type { ImageLoaderProps } from 'next/image';

/**
 * Builds authenticated, same-origin image variants. Unlike the default Next
 * optimizer, the browser calls this URL directly and therefore keeps the
 * session cookie required by private family media.
 */
export function privateMediaLoader({ src, width, quality }: ImageLoaderProps): string {
  const separator = src.includes('?') ? '&' : '?';
  return `${src}${separator}width=${width}&format=webp&quality=${quality ?? 78}`;
}

export function isPrivateMediaUrl(src: string): boolean {
  return src.startsWith('/api/media/') && src.includes('/content');
}
