'use client';

import { useEffect } from 'react';
import { getTextDirection } from '@/i18n/config';
import type { AppLocale } from '@/i18n/routing';

export function LocaleDocumentAttributes({ locale }: { locale: AppLocale }) {
  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = getTextDirection(locale);
  }, [locale]);

  return null;
}
