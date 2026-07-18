import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { Network, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface TreesPageProps { params: { locale: string }; }

export async function generateMetadata({ params }: TreesPageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'trees' });
  return { title: t('metaTitle') };
}

export default async function TreesPage() {
  const t = await getTranslations('trees');
  return (
    <div className="mx-auto flex min-h-[calc(100vh-190px)] max-w-3xl flex-col justify-center py-10 text-center">
      <span className="mx-auto grid size-16 place-items-center rounded-2xl bg-accent text-primary"><Network className="size-7" aria-hidden="true" /></span>
      <h1 className="mt-6 font-display text-4xl font-medium tracking-[-.035em]">{t('emptyTitle')}</h1>
      <p className="mx-auto mt-3 max-w-md text-muted-foreground">{t('emptyDescription')}</p>
      <div className="mt-7"><Button size="lg" disabled><Plus aria-hidden="true" />{t('create')}</Button><p className="mt-3 text-xs text-muted-foreground">{t('subtitle')}</p></div>
    </div>
  );
}
