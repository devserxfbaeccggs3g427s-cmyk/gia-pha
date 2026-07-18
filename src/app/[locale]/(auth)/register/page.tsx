import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { RegisterForm } from '@/components/auth/register-form';
import { isSupportedLocale } from '@/i18n/config';

export async function generateMetadata({ params }: { params: { locale: string } }): Promise<Metadata> {
  if (!isSupportedLocale(params.locale)) return {};
  const t = await getTranslations({ locale: params.locale, namespace: 'auth.register' });
  return { title: t('metaTitle') };
}

export default function RegisterPage() {
  return <RegisterForm />;
}
