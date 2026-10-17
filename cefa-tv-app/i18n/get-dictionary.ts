import 'server-only'
import { Locale } from '@/i18n/config'

const dictionaries = {
  es: () => import('./dictionaries/es.json').then((m) => m.default),
  en: () => import('./dictionaries/en.json').then((m) => m.default),
  pl: () => import('./dictionaries/pl.json').then((m) => m.default),
}

export const getDictionary = async (locale: Locale) => dictionaries[locale]?.() ?? dictionaries.es()

export type Dictionary = Awaited<ReturnType<typeof getDictionary>>
