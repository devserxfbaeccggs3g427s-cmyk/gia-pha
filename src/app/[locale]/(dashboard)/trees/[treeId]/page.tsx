import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { TreeViewer } from '@/components/tree/TreeViewer';

interface TreePageProps {
  params: { locale: string; treeId: string };
  searchParams?: { member?: string };
}

export async function generateMetadata({ params }: TreePageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'treeViewer' });
  return { title: t('metaTitle') };
}

export default function TreePage({ params, searchParams }: TreePageProps) {
  return <TreeViewer treeId={params.treeId} mode="vertical" selectedMemberId={searchParams?.member} />;
}
