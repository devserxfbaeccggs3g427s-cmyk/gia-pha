'use client';

import { Laptop, Moon, Sun } from 'lucide-react';
import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';

interface ThemeToggleProps {
  labels: { system: string; light: string; dark: string; change: string };
}

export function ThemeToggle({ labels }: ThemeToggleProps) {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const current = mounted && (theme === 'light' || theme === 'dark') ? theme : 'system';
  const next = current === 'system' ? 'light' : current === 'light' ? 'dark' : 'system';
  const Icon = current === 'light' ? Sun : current === 'dark' ? Moon : Laptop;

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      onClick={() => setTheme(next)}
      aria-label={`${labels.change}: ${labels[current]}`}
      title={`${labels.change}: ${labels[current]}`}
    >
      <Icon aria-hidden="true" />
    </Button>
  );
}
