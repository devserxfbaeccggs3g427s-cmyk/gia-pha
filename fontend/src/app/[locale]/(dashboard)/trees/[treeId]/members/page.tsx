import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MemberListPage } from '@/components/genealogy/member-pages';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const t = await getTranslations({ locale: (await params).locale, namespace: 'membersPage' });
  return { title: t('metaTitle') };
}

export default async function MembersPage({ params }: { params: Promise<{ treeId: string }> }) {
  return <MemberListPage treeId={(await params).treeId} />;
}
