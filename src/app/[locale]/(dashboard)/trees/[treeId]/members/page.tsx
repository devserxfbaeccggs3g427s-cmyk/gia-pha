import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MemberListPage } from '@/components/genealogy/member-pages';
import { treeService } from '@/lib/services/tree-service';
import { getCompositePublishedConfig, getTrees } from '@/lib/blob/readers';

export async function generateMetadata({ params }: { params: { locale: string } }): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'membersPage' });
  return { title: t('metaTitle') };
}

export default async function MembersPage({ params }: { params: { treeId: string } }) {
  const tree = await treeService.getTree(params.treeId);
  if ((tree.kind ?? 'STANDALONE') !== 'COMPOSITE') return <MemberListPage treeId={params.treeId} treeKind="STANDALONE" />;
  const [config, trees] = await Promise.all([getCompositePublishedConfig(params.treeId), getTrees()]);
  const treeNames = new Map(trees.map((candidate) => [candidate.id, candidate.name]));
  const sources = (config?.sources ?? []).map((source) => ({ treeId: source.sourceTreeId, name: source.preferredLabel ?? treeNames.get(source.sourceTreeId) ?? source.sourceTreeId }));
  return <MemberListPage treeId={params.treeId} treeKind="COMPOSITE" sourceTrees={sources} />;
}
