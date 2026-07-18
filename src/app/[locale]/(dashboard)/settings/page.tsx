import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { Settings, ShieldCheck } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

interface SettingsPageProps { params: { locale: string }; }

export async function generateMetadata({ params }: SettingsPageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'navigation' });
  return { title: t('settings') };
}

export default async function SettingsPage() {
  const t = await getTranslations('navigation');
  const s = await getTranslations('settingsPage');
  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div><h1 className="font-display text-4xl font-medium tracking-[-.035em]">{t('settings')}</h1><p className="mt-2 text-muted-foreground">{s('description')}</p></div>
      <div className="grid gap-5 sm:grid-cols-2">
        <Card><CardHeader><span className="mb-2 grid size-10 place-items-center rounded-xl bg-accent text-primary"><Settings className="size-5" aria-hidden="true" /></span><CardTitle>{s('workspaceTitle')}</CardTitle><CardDescription>{s('workspaceDescription')}</CardDescription></CardHeader><CardContent className="text-sm text-muted-foreground">{s('workspaceHint')}</CardContent></Card>
        <Card><CardHeader><span className="mb-2 grid size-10 place-items-center rounded-xl bg-accent text-primary"><ShieldCheck className="size-5" aria-hidden="true" /></span><CardTitle>{s('privacyTitle')}</CardTitle><CardDescription>{s('privacyDescription')}</CardDescription></CardHeader><CardContent className="text-sm text-muted-foreground">{s('privacyHint')}</CardContent></Card>
      </div>
    </div>
  );
}
