import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { MediaGalleryPage } from '@/components/genealogy/media-pages';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const t = await getTranslations({ locale: (await params).locale, namespace: 'mediaPage' });
  return { title: t('metaTitle') };
}

export default async function MediaPage({ params }: { params: Promise<{ treeId: string }> }) {
  return <MediaGalleryPage treeId={(await params).treeId} />;
}
