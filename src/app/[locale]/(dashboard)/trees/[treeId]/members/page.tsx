import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MemberListPage } from '@/components/genealogy/member-pages';
import { treeService } from '@/lib/services/tree-service';

export async function generateMetadata({ params }: { params: { locale: string } }): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'membersPage' });
  return { title: t('metaTitle') };
}

export default async function MembersPage({ params }: { params: { treeId: string } }) {
  const tree = await treeService.getTree(params.treeId);
  return <MemberListPage treeId={params.treeId} treeKind={tree.kind ?? 'STANDALONE'} />;
}
