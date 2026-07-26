import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { TreeViewer } from '@/components/tree/TreeViewer';

interface TreePageProps {
  params: Promise<{ locale: string; treeId: string }>;
  searchParams?: { member?: string };
}

export async function generateMetadata({ params }: TreePageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: (await params).locale, namespace: 'treeViewer' });
  return { title: t('metaTitle') };
}

export default async function TreePage({ params, searchParams }: TreePageProps) {
  return <TreeViewer treeId={(await params).treeId} mode="vertical" selectedMemberId={searchParams?.member} />;
}
