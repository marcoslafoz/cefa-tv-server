import { type Locale } from '@/i18n/config'

const COOKIE_NAME = 'NEXT_LOCALE'

export function setUserLocale(locale: Locale) {
  document.cookie = `${COOKIE_NAME}=${locale};path=/;max-age=31536000`
}
