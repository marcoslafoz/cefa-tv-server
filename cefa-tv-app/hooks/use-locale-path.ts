import { useCallback } from 'react'
import { useParams } from 'next/navigation'

export function useLocalePath() {
  const params = useParams()!
  const locale = (params.locale as string) || 'es'

  return useCallback(
    (path: string) => {
      if (path.startsWith(`/${locale}`)) return path
      if (path === '/') return `/${locale}`
      return `/${locale}${path}`
    },
    [locale]
  )
}
