import { useParams } from 'next/navigation'
import { useCallback } from 'react'
import { defaultLocale, hasLocale, intlLocales } from '@/i18n/config'

export function useDateFormatter() {
  const params = useParams()
  const requested = (params?.locale as string) || defaultLocale
  const locale = intlLocales[hasLocale(requested) ? requested : defaultLocale]

  const formatDateTime = useCallback(
    (value: string | null | undefined) =>
      value
        ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(
            new Date(value)
          )
        : '',
    [locale]
  )

  const formatDate = useCallback(
    (value: string | null | undefined) =>
      value ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(value)) : '',
    [locale]
  )

  const formatRelative = useCallback(
    (value: string | null | undefined) => {
      if (!value) return ''
      const diffSeconds = Math.round((new Date(value).getTime() - Date.now()) / 1000)
      const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
      const abs = Math.abs(diffSeconds)
      if (abs < 60) return rtf.format(diffSeconds, 'second')
      if (abs < 3600) return rtf.format(Math.round(diffSeconds / 60), 'minute')
      if (abs < 86400) return rtf.format(Math.round(diffSeconds / 3600), 'hour')
      return rtf.format(Math.round(diffSeconds / 86400), 'day')
    },
    [locale]
  )

  return { formatDateTime, formatDate, formatRelative, locale }
}
