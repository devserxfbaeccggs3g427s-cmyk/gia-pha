'use client';

import { useEffect } from 'react';
import { useSession } from 'next-auth/react';
import { useQueryClient } from '@tanstack/react-query';

/** Registers the worker only in a browser production build. Keeping this out
 * of development prevents stale precached chunks from interfering with HMR. */
export function PwaProvider({ children }: { children: React.ReactNode }) {
  const { status } = useSession();
  const queryClient = useQueryClient();

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
    const notify = () => {
      window.dispatchEvent(new CustomEvent('composite-connectivity', { detail: { online: navigator.onLine, reauthorize: navigator.onLine } }));
      if (navigator.onLine) void queryClient.invalidateQueries({ refetchType: 'active' });
    };
    window.addEventListener('online', notify);
    window.addEventListener('offline', notify);
    return () => { window.removeEventListener('online', notify); window.removeEventListener('offline', notify); };
  }, [queryClient]);

  useEffect(() => {
    if (status === 'loading') return;
    if (status === 'unauthenticated') queryClient.clear();
    if (process.env.NODE_ENV !== 'production' || !('serviceWorker' in navigator)) return;
    void navigator.serviceWorker.ready.then((registration) => {
      registration.active?.postMessage({ type: status === 'authenticated' ? 'AUTHENTICATED' : 'CLEAR_PRIVATE_CACHES' });
    }).catch(() => undefined);
  }, [queryClient, status]);

  return children;
}
