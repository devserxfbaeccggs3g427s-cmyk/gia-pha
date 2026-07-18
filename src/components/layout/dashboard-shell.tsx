'use client';

import {
  BarChart3,
  BookOpen,
  CalendarDays,
  ChevronLeft,
  CircleHelp,
  FileImage,
  LayoutDashboard,
  Menu,
  Network,
  Search,
  Settings,
  ShieldCheck,
  Users,
  X
} from 'lucide-react';
import { signOut, useSession } from 'next-auth/react';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname, Link } from '@/i18n/navigation';
import { useEffect, useMemo, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { LanguageSwitcher } from '@/components/i18n/language-switcher';
import { ThemeToggle } from '@/components/theme/theme-toggle';
import { Breadcrumbs } from '@/components/layout/breadcrumbs';
import { MemberSearch } from '@/components/genealogy/member-search';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { useToast } from '@/components/ui/toast';
import { useUiStore } from '@/store/ui-store';
import styles from './dashboard-shell.module.css';

interface DashboardShellProps { children: React.ReactNode; }
interface NavItem { key: 'overview' | 'trees' | 'members' | 'events' | 'media' | 'reports' | 'settings'; icon: LucideIcon; href: string; requiresTree?: boolean; }

const primaryItems: Array<Pick<NavItem, 'key' | 'icon'>> = [
  { key: 'overview', icon: LayoutDashboard },
  { key: 'trees', icon: Network }
];
const workspaceItems: Array<Pick<NavItem, 'key' | 'icon'>> = [
  { key: 'members', icon: Users },
  { key: 'events', icon: CalendarDays },
  { key: 'media', icon: FileImage },
  { key: 'reports', icon: BarChart3 }
];

export function DashboardShell({ children }: DashboardShellProps) {
  const pathname = usePathname();
  const locale = useLocale();
  const t = useTranslations('common');
  const e = useTranslations('errors');
  const nav = useTranslations('navigation');
  const d = useTranslations('dashboard');
  const { data: session } = useSession();
  const { toast } = useToast();
  const collapsed = useUiStore((state) => state.sidebarCollapsed);
  const setCollapsed = useUiStore((state) => state.setSidebarCollapsed);
  const mobileOpen = useUiStore((state) => state.mobileSidebarOpen);
  const setMobileOpen = useUiStore((state) => state.setMobileSidebarOpen);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const treeId = useMemo(() => {
    const match = pathname.match(/^\/trees\/([^/]+)/);
    return match?.[1];
  }, [pathname]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      const isTyping = target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName);
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'b' && !isTyping) {
        event.preventDefault();
        useUiStore.getState().toggleSidebar();
      }
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k' && !isTyping) {
        event.preventDefault();
        setPaletteOpen(true);
      }
      if (event.key === 'Escape') setMobileOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const setSidebarCollapsed = (value: boolean) => {
    setCollapsed(value);
  };

  const getHref = (key: NavItem['key']) => {
    if (key === 'overview') return '/';
    if (key === 'trees') return '/trees';
    return treeId ? `/trees/${treeId}/${key}` : '/trees';
  };
  const items = (entries: Array<Pick<NavItem, 'key' | 'icon'>>, requiresTree = false): NavItem[] => entries.map((entry) => ({ ...entry, href: getHref(entry.key), requiresTree }));
  const allItems = [...items(primaryItems), ...items(workspaceItems, true), { key: 'settings' as const, icon: Settings, href: '/settings' }];
  const displayName = session?.user?.name || session?.user?.email?.split('@')[0] || 'Family member';
  const initials = displayName.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();

  return (
    <div className={styles.shell}>
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-lg focus:bg-primary focus:px-4 focus:py-3 focus:text-sm focus:font-semibold focus:text-primary-foreground">Skip to content</a>
      <button className={styles.mobileOverlay} data-open={mobileOpen} aria-label={t('close')} onClick={() => setMobileOpen(false)} />
      <aside id="dashboard-sidebar" className={styles.sidebar} data-collapsed={collapsed} data-mobile-open={mobileOpen} aria-label={nav('workspace')}>
        <Link href="/" className={styles.brand} onClick={() => setMobileOpen(false)}>
          <span className={styles.brandMark} aria-hidden="true"><BookOpen className="size-5" /></span>
          <span><span className={styles.brandName}>Gia Phả</span><span className={styles.brandTagline}>FAMILY LEGACY</span></span>
        </Link>
        <nav className={styles.nav} aria-label={nav('workspace')}>
          <div className={styles.sectionLabel}>{nav('workspace')}</div>
          <ul className={styles.navList}>
            {items(primaryItems).map((item) => <SidebarLink key={item.key} item={item} label={nav(item.key)} pathname={pathname} onNavigate={() => setMobileOpen(false)} collapsed={collapsed} />)}
          </ul>
          <div className={styles.sectionLabel}>{treeId ? nav('breadcrumbTree') : nav('trees')}</div>
          <ul className={styles.navList}>
            {items(workspaceItems, true).map((item) => <SidebarLink key={item.key} item={item} label={nav(item.key)} pathname={pathname} onNavigate={() => setMobileOpen(false)} collapsed={collapsed} disabled={!treeId} disabledLabel={nav('chooseTree')} />)}
          </ul>
          <div className={styles.sectionLabel}>{nav('settings')}</div>
          <ul className={styles.navList}>
            <SidebarLink item={{ key: 'settings', icon: Settings, href: '/settings' }} label={nav('settings')} pathname={pathname} onNavigate={() => setMobileOpen(false)} collapsed={collapsed} />
          </ul>
        </nav>
        <div className={styles.sidebarFooter}>
          <div className={styles.userCard} title={displayName}>
            <span className={styles.avatar} aria-hidden="true">{initials}</span>
            <span className={styles.userInfo}><span className={styles.userName}>{displayName}</span><span className={styles.userRole}>Family keeper</span></span>
          </div>
          <button className={styles.footerButton} onClick={() => toast({ title: nav('help'), description: e('comingSoon') })} title={nav('help')}>
            <CircleHelp className="size-4" aria-hidden="true" /><span>{nav('help')}</span>
          </button>
          <button className={styles.footerButton} onClick={() => setSidebarCollapsed(!collapsed)} title={collapsed ? nav('expand') : nav('collapse')} aria-expanded={!collapsed} aria-controls="dashboard-sidebar">
            <ChevronLeft className={`size-4 transition-transform ${collapsed ? 'rotate-180' : ''}`} aria-hidden="true" /><span>{collapsed ? nav('expand') : nav('collapse')}</span>
          </button>
        </div>
      </aside>

      <div className={styles.main} data-collapsed={collapsed}>
        <header className={styles.topbar}>
          <div className={styles.topbarStart}>
            <Button className={styles.menuButton} variant="ghost" size="icon" onClick={() => setMobileOpen(true)} aria-label={t('openMenu')}><Menu aria-hidden="true" /></Button>
            <Breadcrumbs />
          </div>
          <div className={styles.topbarActions}>
            <Dialog open={paletteOpen} onOpenChange={setPaletteOpen}>
              <DialogTrigger asChild>
                <button className={styles.searchButton} aria-label={t('search')}><Search className="size-4 shrink-0" aria-hidden="true" /><span className={styles.searchText}>{t('searchHint')}</span><kbd className={styles.shortcut}>⌘ K</kbd></button>
              </DialogTrigger>
              <DialogContent className={treeId ? 'max-w-3xl p-0 [&>button]:hidden' : 'max-w-xl p-0'}>
                {treeId ? (
                  <MemberSearch treeId={treeId} onClose={() => setPaletteOpen(false)} />
                ) : (
                  <>
                    <DialogHeader className="border-b border-border px-5 py-4">
                      <DialogTitle className="flex items-center gap-2 text-base"><Search className="size-4 text-muted-foreground" />{t('commandPalette')}</DialogTitle>
                      <DialogDescription>{t('commandPaletteHint')}</DialogDescription>
                    </DialogHeader>
                    <div className="grid gap-1 p-3">
                      {allItems.map((item) => <Link key={item.key} href={item.href as never} onClick={() => setPaletteOpen(false)} className="flex items-center gap-3 rounded-xl px-3 py-3 text-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><item.icon className="size-4 text-muted-foreground" />{nav(item.key)}</Link>)}
                    </div>
                  </>
                )}
              </DialogContent>
            </Dialog>
            <ThemeToggle labels={{ system: t('themeSystem'), light: t('themeLight'), dark: t('themeDark'), change: t('changeTheme') }} />
            <span className={styles.language}><LanguageSwitcher /></span>
            <Button variant="ghost" size="icon" aria-label={t('notifications')} title={t('notifications')} onClick={() => toast({ title: t('notifications'), description: d('noActivity') })}><span className="relative"><ShieldCheck className="size-4" aria-hidden="true" /><span className="absolute -right-1 -top-1 size-1.5 rounded-full bg-[hsl(var(--gold))]" /></span></Button>
            <div className={styles.profileWrap}>
              <details>
                <summary className={styles.profileTrigger} aria-label={t('profile')}><span className={styles.avatar}>{initials}</span><span className={styles.userInfo}><span className={styles.userName}>{displayName}</span></span></summary>
                <div className={styles.profileMenu}><button onClick={() => void signOut({ callbackUrl: `/${locale}/login` })}><X className="size-4" aria-hidden="true" />{t('signOut')}</button></div>
              </details>
            </div>
          </div>
        </header>
        <main id="main-content" className={styles.content}>{children}</main>
      </div>
    </div>
  );
}

function SidebarLink({ item, label, pathname, onNavigate, collapsed, disabled = false, disabledLabel }: { item: NavItem; label: string; pathname: string; onNavigate: () => void; collapsed: boolean; disabled?: boolean; disabledLabel?: string }) {
  const active = item.key === 'overview' ? pathname === '/' : item.key === 'trees' ? pathname === '/trees' : pathname === item.href || pathname.startsWith(`${item.href}/`);
  const Icon = item.icon;
  return (
    <li>
      <Link href={item.href as never} className={styles.navLink} data-active={active} data-disabled={disabled} aria-current={active ? 'page' : undefined} aria-disabled={disabled || undefined} title={collapsed || disabled ? (disabled ? disabledLabel : label) : undefined} onClick={onNavigate}>
        <span className={styles.navIcon}><Icon className="size-[18px]" aria-hidden="true" /></span><span className={styles.navText}>{label}</span>{disabled && !collapsed && <span className={styles.navHint}>•</span>}
      </Link>
    </li>
  );
}
