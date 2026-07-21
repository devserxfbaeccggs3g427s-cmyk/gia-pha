import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { TreeViewer } from '@/components/tree/TreeViewer';
import { CompositeWizard } from '@/components/genealogy/composite-wizard';
import { getTrees } from '@/lib/blob/readers';

interface TreePageProps {
  params: { locale: string; treeId: string };
  searchParams?: { member?: string };
}

export async function generateMetadata({ params }: TreePageProps): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'treeViewer' });
  return { title: t('metaTitle') };
}

export default async function TreePage({ params, searchParams }: TreePageProps) {
  const trees = await getTrees();
  const tree = trees.find((item) => item.id === params.treeId);
  return <div className="space-y-6">{tree?.kind === 'COMPOSITE' && <CompositeWizard tree={tree} availableTrees={trees} />}<TreeViewer treeId={params.treeId} mode="vertical" selectedMemberId={searchParams?.member} /></div>;
}
