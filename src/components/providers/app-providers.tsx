'use client';

import { SessionProvider } from 'next-auth/react';
import { ThemeProvider } from '@/components/theme/theme-provider';
import { ToastProvider } from '@/components/ui/toast';
import { useOfflineMutationSync } from '@/hooks/useOfflineMutationSync';
import { QueryProvider } from '@/components/providers/query-provider';

function OfflineMutationSync() {
  useOfflineMutationSync();
  return null;
}

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider refetchInterval={60} refetchOnWindowFocus>
      <QueryProvider>
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <ToastProvider>
            <OfflineMutationSync />
            {children}
          </ToastProvider>
        </ThemeProvider>
      </QueryProvider>
    </SessionProvider>
  );
}
