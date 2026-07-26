'use client';

import { useEffect, useState } from 'react';
import { CalendarDays, ChevronRight, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import type { Event, FamilyTree } from '@/data/types';
import { Link } from '@/i18n/navigation';

type Reminder = Event & { nextOccurrence: string; daysUntil: number; treeName: string };

export function DashboardUpcomingEvents() {
  const t = useTranslations('dashboard');
  const [items, setItems] = useState<Reminder[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const treeResponse = await fetch('/api/trees');
        if (!treeResponse.ok) return;
        const trees = await treeResponse.json() as FamilyTree[];
        const groups = await Promise.all(trees.map(async (tree) => {
          const response = await fetch(`/api/trees/${tree.id}/events?upcoming=true&days=7`);
          if (!response.ok) return [];
          const events = await response.json() as Array<Event & { nextOccurrence: string; daysUntil: number }>;
          return events.map((event) => ({ ...event, treeName: tree.name }));
        }));
        if (active) setItems(groups.flat().sort((a, b) => a.daysUntil - b.daysUntil).slice(0, 5));
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => { active = false; };
  }, []);

  if (loading) return <div className="flex min-h-24 items-center justify-center rounded-xl bg-muted/35 text-muted-foreground"><Loader2 className="size-4 animate-spin" /><span className="sr-only">{t('upcomingLoading')}</span></div>;
  if (!items.length) return <div className="flex min-h-24 items-center gap-3 rounded-xl border border-dashed border-border bg-muted/35 px-4 text-sm text-muted-foreground"><CalendarDays className="size-4 shrink-0" /><span>{t('upcomingEmpty')}</span></div>;
  return <ul className="grid gap-2">{items.map((event) => <li key={`${event.treeId}-${event.id}`}><Link href={`/trees/${event.treeId}/events#${event.id}`} className="group flex items-center gap-3 rounded-xl border border-border/70 p-3 transition-colors hover:border-primary/35 hover:bg-accent/35"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary"><CalendarDays className="size-4" /></span><span className="min-w-0 flex-1"><span className="block truncate text-sm font-semibold">{event.title}</span><span className="block truncate text-xs text-muted-foreground">{event.treeName} · {event.daysUntil === 0 ? t('upcomingToday') : t('upcomingInDays', { count: event.daysUntil })}</span></span><ChevronRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" /></Link></li>)}</ul>;
}
