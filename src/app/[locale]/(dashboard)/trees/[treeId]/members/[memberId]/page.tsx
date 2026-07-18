import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MemberDetailPage } from '@/components/genealogy/member-pages';

export async function generateMetadata({ params }: { params: { locale: string } }): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'membersPage' });
  return { title: t('detailMetaTitle') };
}

export default function MemberPage({ params }: { params: { treeId: string; memberId: string } }) {
  return <MemberDetailPage treeId={params.treeId} memberId={params.memberId} />;
}
