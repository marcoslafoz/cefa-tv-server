import { useCallback } from 'react'
import { useParams } from 'next/navigation'
import { formatMessage, type FormatValues } from '@/lib/format'
import { defaultLocale, hasLocale } from '@/i18n/config'

export function useFormat() {
  const params = useParams()
  const requested = (params?.locale as string) || defaultLocale
  const locale = hasLocale(requested) ? requested : defaultLocale

  return useCallback(
    (template: string, values: FormatValues) => formatMessage(template, values, locale),
    [locale]
  )
}
