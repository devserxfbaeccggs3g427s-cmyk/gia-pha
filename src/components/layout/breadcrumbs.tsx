'use client';

import { ChevronRight, Home } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { cn } from '@/lib/utils';

const labels: Record<string, string> = {
  trees: 'breadcrumbTree',
  members: 'breadcrumbMembers',
  events: 'breadcrumbEvents',
  media: 'breadcrumbMedia',
  reports: 'breadcrumbReports',
  settings: 'breadcrumbSettings'
};

export function Breadcrumbs() {
  const pathname = usePathname();
  const t = useTranslations('navigation');
  const segments = pathname.split('/').filter(Boolean);
  const crumbs = segments.map((segment, index) => ({
    segment,
    href: `/${segments.slice(0, index + 1).join('/')}`
  }));

  return (
    <nav aria-label={t('breadcrumbLabel')} className="flex min-w-0 items-center gap-1.5 text-sm">
      <Link
        href="/"
        className="inline-flex shrink-0 items-center gap-1.5 rounded-md px-1.5 py-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Home className="size-3.5" aria-hidden="true" />
        <span className="sr-only">{t('breadcrumbHome')}</span>
      </Link>
      {crumbs.map((crumb, index) => {
        const translationKey = labels[crumb.segment] as Parameters<typeof t>[0] | undefined;
        const label = translationKey ? t(translationKey) : decodeURIComponent(crumb.segment);
        const isLast = index === crumbs.length - 1;
        return (
          <span key={crumb.href} className="flex min-w-0 items-center gap-1.5">
            <ChevronRight className="size-3.5 shrink-0 text-muted-foreground/60" aria-hidden="true" />
            {isLast ? (
              <span className="truncate font-medium text-foreground" aria-current="page">{label}</span>
            ) : (
              <Link href={crumb.href as never} className={cn('truncate rounded-md px-1.5 py-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring')}>
                {label}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
