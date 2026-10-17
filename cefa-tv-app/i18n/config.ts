export const locales = ['es', 'en', 'pl'] as const
export type Locale = (typeof locales)[number]
export const defaultLocale: Locale = 'es'

export const intlLocales: Record<Locale, string> = {
  es: 'es-ES',
  en: 'en-GB',
  pl: 'pl-PL',
}

export function hasLocale(locale: string): locale is Locale {
  return locales.includes(locale as Locale)
}
