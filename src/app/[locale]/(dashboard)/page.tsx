import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { ArrowUpRight, CalendarDays, Camera, GitBranch, Leaf, LockKeyhole, Network, Plus, Users, type LucideIcon } from 'lucide-react';
import { Link } from '@/i18n/navigation';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DashboardUpcomingEvents } from '@/components/genealogy/dashboard-upcoming-events';

interface DashboardPageProps { params: { locale: string }; }

export async function generateMetadata({ params }: DashboardPageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'dashboard' });
  return { title: t('metaTitle') };
}

export default async function DashboardPage() {
  const t = await getTranslations('dashboard');
  const n = await getTranslations('navigation');
  return (
    <div className="space-y-8">
      <section className="relative overflow-hidden rounded-3xl bg-primary px-6 py-8 text-primary-foreground shadow-lg shadow-primary/10 sm:px-9 sm:py-10">
        <div className="pointer-events-none absolute -right-20 -top-32 size-80 rounded-full border border-primary-foreground/10" />
        <div className="pointer-events-none absolute -bottom-40 right-24 size-72 rounded-full border border-primary-foreground/10" />
        <div className="relative max-w-2xl">
          <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-primary-foreground/70"><Leaf className="size-4" aria-hidden="true" />{t('greeting')}</div>
          <h1 className="font-display text-balance text-3xl font-medium leading-tight tracking-[-.035em] sm:text-5xl">{t('welcome')}</h1>
          <div className="mt-7 flex flex-wrap gap-3">
            <Button asChild size="lg" className="bg-primary-foreground text-primary shadow-none hover:bg-primary-foreground/90"><Link href="/trees"><Plus aria-hidden="true" />{t('createTree')}</Link></Button>
            <Button asChild variant="ghost" size="lg" className="text-primary-foreground hover:bg-primary-foreground/10 hover:text-primary-foreground"><Link href="/trees">{t('viewTrees')}<ArrowUpRight aria-hidden="true" /></Link></Button>
          </div>
        </div>
      </section>

      <section aria-labelledby="overview-title" className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3"><div><h2 id="overview-title" className="font-display text-2xl font-medium tracking-[-.025em]">{t('overview')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('overviewHint')}</p></div><span className="text-xs text-muted-foreground">{t('lastUpdated')}: —</span></div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard icon={Users} label={t('members')} value="0" detail={t('activeTrees')} tone="green" />
          <StatCard icon={GitBranch} label={t('generations')} value="0" detail={t('thisMonth')} tone="gold" />
          <StatCard icon={CalendarDays} label={t('events')} value="0" detail={t('upcoming')} tone="blue" />
          <StatCard icon={Camera} label={t('media')} value="0" detail={t('thisMonth')} tone="rose" />
        </div>
      </section>

      <section className="grid gap-5 lg:grid-cols-[1.25fr_.75fr]">
        <Card className="overflow-hidden">
          <CardHeader className="flex-row items-start justify-between gap-4 border-b border-border/70 pb-5"><div><CardTitle className="font-display text-xl font-medium">{t('getStarted')}</CardTitle><CardDescription className="mt-2 max-w-md">{t('getStartedHint')}</CardDescription></div><span className="grid size-11 shrink-0 place-items-center rounded-xl bg-accent text-primary"><Network className="size-5" aria-hidden="true" /></span></CardHeader>
          <CardContent className="grid gap-3 pt-5 sm:grid-cols-3">
            <OnboardingStep number="01" text={t('stepOne')} active />
            <OnboardingStep number="02" text={t('stepTwo')} />
            <OnboardingStep number="03" text={t('stepThree')} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle className="font-display text-xl font-medium">{t('upcoming')}</CardTitle><CardDescription>{t('upcomingHint')}</CardDescription></CardHeader>
          <CardContent><DashboardUpcomingEvents /></CardContent>
        </Card>
      </section>

      <section className="grid gap-5 lg:grid-cols-[1.25fr_.75fr]">
        <Card><CardHeader><CardTitle className="font-display text-xl font-medium">{t('activity')}</CardTitle></CardHeader><CardContent><div className="flex min-h-24 items-center gap-3 rounded-xl bg-muted/35 px-4 text-sm text-muted-foreground"><span className="size-2 rounded-full bg-primary/35" />{t('noActivity')}</div></CardContent></Card>
        <Card className="border-primary/15 bg-accent/45"><CardHeader><span className="mb-3 grid size-10 place-items-center rounded-xl bg-primary text-primary-foreground"><LockKeyhole className="size-4" aria-hidden="true" /></span><CardTitle className="font-display text-xl font-medium">{t('privacy')}</CardTitle><CardDescription>{t('privacyHint')}</CardDescription></CardHeader><CardContent><Link href="/settings" className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline">{n('settings')}<ArrowUpRight className="size-3.5" aria-hidden="true" /></Link></CardContent></Card>
      </section>
    </div>
  );
}

function StatCard({ icon: Icon, label, value, detail, tone }: { icon: LucideIcon; label: string; value: string; detail: string; tone: 'green' | 'gold' | 'blue' | 'rose' }) {
  const tones = { green: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300', gold: 'bg-amber-500/10 text-amber-700 dark:text-amber-300', blue: 'bg-sky-500/10 text-sky-700 dark:text-sky-300', rose: 'bg-rose-500/10 text-rose-700 dark:text-rose-300' };
  return <Card className="group transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-md"><CardContent className="p-5"><div className="flex items-start justify-between"><span className={`grid size-10 place-items-center rounded-xl ${tones[tone]}`}><Icon className="size-[18px]" aria-hidden="true" /></span><ArrowUpRight className="size-4 text-muted-foreground/40 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" aria-hidden="true" /></div><p className="mt-5 text-sm text-muted-foreground">{label}</p><p className="mt-1 text-3xl font-semibold tracking-tight">{value}</p><p className="mt-1 truncate text-xs text-muted-foreground">{detail}</p></CardContent></Card>;
}

function OnboardingStep({ number, text, active = false }: { number: string; text: string; active?: boolean }) {
  return <div className={`rounded-xl border p-4 ${active ? 'border-primary/25 bg-accent/60' : 'border-border/70 bg-muted/20'}`}><span className={`text-xs font-bold tracking-[.14em] ${active ? 'text-primary' : 'text-muted-foreground'}`}>{number}</span><p className="mt-6 text-sm font-semibold leading-snug">{text}</p><span className={`mt-4 block h-1 rounded-full ${active ? 'bg-primary' : 'bg-border'}`} /></div>;
}
