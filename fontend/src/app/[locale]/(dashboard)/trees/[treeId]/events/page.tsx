import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { EventListPage } from '@/components/genealogy/event-pages';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const t = await getTranslations({ locale: (await params).locale, namespace: 'eventsPage' });
  return { title: t('metaTitle') };
}

export default async function EventsPage({ params }: { params: Promise<{ treeId: string }> }) {
  return <EventListPage treeId={(await params).treeId} />;
}
