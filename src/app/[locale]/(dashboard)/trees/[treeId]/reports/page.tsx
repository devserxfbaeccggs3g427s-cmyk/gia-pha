import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { ReportsPage } from '@/components/genealogy/reports-page';

interface ReportsRouteProps {
  params: { locale: string; treeId: string };
}

export async function generateMetadata({ params }: ReportsRouteProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'reportsPage' });
  return { title: t('metaTitle'), description: t('description') };
}

export default function ReportsRoute({ params }: ReportsRouteProps) {
  return <ReportsPage treeId={params.treeId} />;
}
