import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { TreesPage } from '@/components/genealogy/trees-page';

interface TreesPageProps { params: { locale: string }; }

export async function generateMetadata({ params }: TreesPageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'trees' });
  return { title: t('metaTitle') };
}

export default function TreesRoutePage() { return <TreesPage />; }
