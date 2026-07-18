import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { EventListPage } from '@/components/genealogy/event-pages';

export async function generateMetadata({ params }: { params: { locale: string } }): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'eventsPage' });
  return { title: t('metaTitle') };
}

export default function EventsPage({ params }: { params: { treeId: string } }) {
  return <EventListPage treeId={params.treeId} />;
}
