import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MediaGalleryPage } from '@/components/genealogy/media-pages';

export async function generateMetadata({ params }: { params: { locale: string } }): Promise<Metadata> {
  const t = await getTranslations({ locale: params.locale, namespace: 'mediaPage' });
  return { title: t('metaTitle') };
}

export default function MediaPage({ params }: { params: { treeId: string } }) {
  return <MediaGalleryPage treeId={params.treeId} />;
}
