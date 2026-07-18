import { getRequestConfig } from 'next-intl/server';
import { DEFAULT_TIME_ZONE, formats, isSupportedLocale } from './config';
import { routing } from './routing';

export default getRequestConfig(async ({ requestLocale }) => {
  const requestedLocale = await requestLocale;
  const locale = isSupportedLocale(requestedLocale) ? requestedLocale : routing.defaultLocale;

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
    formats,
    timeZone: DEFAULT_TIME_ZONE
  };
});
