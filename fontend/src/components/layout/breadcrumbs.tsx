'use client';

import { ChevronRight, Home } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { cn } from '@/lib/utils';
import { apiRequest } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';

const labels: Record<string, string> = {
  trees: 'breadcrumbTree',
  members: 'breadcrumbMembers',
  events: 'breadcrumbEvents',
  media: 'breadcrumbMedia',
  reports: 'breadcrumbReports',
  settings: 'breadcrumbSettings'
};

export interface BreadcrumbItem {
  segment: string;
  index: number;
  href: string;
}

export function buildBreadcrumbItems(pathname: string): BreadcrumbItem[] {
  const segments = pathname.split('/').filter(Boolean);
  return segments.map((segment, index) => ({
    segment,
    index,
    href: `/${segments.slice(0, index + 1).join('/')}`
  }));
}

export function Breadcrumbs() {
  const pathname = usePathname();
  const t = useTranslations('navigation');
  const crumbs = buildBreadcrumbItems(pathname);
  const segments = crumbs.map((crumb) => crumb.segment);
  const treesIndex = segments.indexOf('trees');
  const treeId = treesIndex >= 0 ? segments[treesIndex + 1] : undefined;
  const membersIndex = segments.indexOf('members');
  const memberId = membersIndex >= 0 ? segments[membersIndex + 1] : undefined;
  const treeQuery = useQuery({
    queryKey: treeId ? queryKeys.tree(treeId) : ['breadcrumb-tree'],
    queryFn: () => apiRequest<{ name: string }>(`/api/trees/${encodeURIComponent(treeId!)}`),
    enabled: Boolean(treeId),
    staleTime: 60_000
  });
  const memberQuery = useQuery({
    queryKey: treeId && memberId ? queryKeys.member(treeId, memberId) : ['breadcrumb-member'],
    queryFn: () => apiRequest<{ fullName?: string; member?: { fullName?: string } }>(
      `/api/members/${encodeURIComponent(memberId!)}?treeId=${encodeURIComponent(treeId!)}`
    ),
    enabled: Boolean(treeId && memberId),
    staleTime: 60_000
  });
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
        const resourceLabel = crumb.index === treesIndex + 1 && treeId
          ? treeQuery.data?.name
          : crumb.index === membersIndex + 1 && memberId
            ? memberQuery.data?.fullName ?? memberQuery.data?.member?.fullName
            : undefined;
        const label = resourceLabel ?? (translationKey ? t(translationKey) : decodeURIComponent(crumb.segment));
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
