import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MemberDetailPage } from '@/components/genealogy/member-pages';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const t = await getTranslations({ locale: (await params).locale, namespace: 'membersPage' });
  return { title: t('detailMetaTitle') };
}

export default async function MemberPage({ params }: { params: Promise<{ treeId: string; memberId: string }> }) {
  const { treeId, memberId } = await params;
  return <MemberDetailPage treeId={treeId} memberId={memberId} />;
}
