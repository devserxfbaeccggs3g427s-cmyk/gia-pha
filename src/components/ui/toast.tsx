'use client';

import * as React from 'react';
import * as ToastPrimitive from '@radix-ui/react-toast';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

type ToastTone = 'default' | 'success' | 'destructive';
type ToastInput = { title: string; description?: string; tone?: ToastTone; duration?: number };
type ToastItem = ToastInput & { id: number; open: boolean };

const ToastContext = React.createContext<((toast: ToastInput) => void) | null>(null);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = React.useState<ToastItem[]>([]);
  const nextId = React.useRef(0);

  const toast = React.useCallback((input: ToastInput) => {
    const id = ++nextId.current;
    setItems((current) => [...current, { ...input, id, open: true }]);
  }, []);

  return (
    <ToastContext.Provider value={toast}>
      <ToastPrimitive.Provider swipeDirection="right">
        {children}
        {items.map((item) => (
          <ToastPrimitive.Root
            key={item.id}
            open={item.open}
            duration={item.duration ?? 4500}
            onOpenChange={(open) => {
              if (open) return;
              setItems((current) => current.map((candidate) => candidate.id === item.id ? { ...candidate, open: false } : candidate));
              window.setTimeout(() => setItems((current) => current.filter((candidate) => candidate.id !== item.id)), 180);
            }}
            className={cn(
              'group pointer-events-auto relative grid w-full grid-cols-[1fr_auto] gap-x-4 gap-y-1 overflow-hidden rounded-xl border border-border bg-popover p-4 pr-8 text-popover-foreground shadow-xl data-[state=open]:animate-in data-[state=closed]:animate-out data-[swipe=end]:animate-out data-[state=closed]:fade-out-80 data-[state=open]:slide-in-from-right-full data-[state=closed]:slide-out-to-right-full',
              item.tone === 'destructive' && 'border-destructive/30 bg-destructive text-destructive-foreground',
              item.tone === 'success' && 'border-primary/25'
            )}
          >
            <ToastPrimitive.Title className="text-sm font-semibold">{item.title}</ToastPrimitive.Title>
            {item.description && <ToastPrimitive.Description className="col-start-1 text-sm opacity-80">{item.description}</ToastPrimitive.Description>}
            <ToastPrimitive.Close className="absolute right-2 top-2 rounded-md p-1 opacity-60 transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring" aria-label="Close notification">
              <X className="size-4" />
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        ))}
        <ToastPrimitive.Viewport className="fixed bottom-0 right-0 z-[100] flex max-h-screen w-full flex-col gap-2 p-4 sm:max-w-[420px]" />
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const toast = React.useContext(ToastContext);
  if (!toast) throw new Error('useToast must be used within ToastProvider');
  return { toast };
}
