'use client';

import { useEffect } from 'react';
import { useSession } from 'next-auth/react';

/** Registers the worker only in a browser production build. Keeping this out
 * of development prevents stale precached chunks from interfering with HMR. */
export function PwaProvider({ children }: { children: React.ReactNode }) {
  const { status } = useSession();

  useEffect(() => {
    if (process.env.NODE_ENV !== 'production' || !('serviceWorker' in navigator)) return;

    let registration: ServiceWorkerRegistration | undefined;
    const update = () => void registration?.update().catch(() => undefined);
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') update();
    };
    window.addEventListener('focus', update);
    document.addEventListener('visibilitychange', handleVisibility);

    void navigator.serviceWorker.register('/sw.js', { scope: '/' }).then((value) => {
      registration = value;
    }).catch(() => {
      // PWA support is progressive; an unavailable worker must not break the
      // genealogy application itself.
    });

    return () => {
      window.removeEventListener('focus', update);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, []);

  useEffect(() => {
    const notify = () => window.dispatchEvent(new CustomEvent('composite-connectivity', { detail: { online: navigator.onLine, reauthorize: navigator.onLine } }));
    window.addEventListener('online', notify);
    window.addEventListener('offline', notify);
    return () => { window.removeEventListener('online', notify); window.removeEventListener('offline', notify); };
  }, []);

  useEffect(() => {
    if (status === 'loading' || process.env.NODE_ENV !== 'production' || !('serviceWorker' in navigator)) return;
    // Private route/API caches must never survive a sign-out and be shown to
    // another account on the same device. The shell cache is intentionally
    // retained because it contains no family data.
    void navigator.serviceWorker.ready.then((registration) => {
      registration.active?.postMessage({ type: status === 'authenticated' ? 'AUTHENTICATED' : 'CLEAR_PRIVATE_CACHES' });
    }).catch(() => undefined);
  }, [status]);

  return children;
}
