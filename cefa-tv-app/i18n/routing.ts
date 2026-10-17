import { locales, defaultLocale } from '@/i18n/config'

export const routing = {
  locales,
  defaultLocale,
  localePrefix: 'as-needed',
} as const

export type Pathnames = '/' | string
