import { useCallback, useEffect, useRef, useState } from 'react'
import { apiClient } from '@/lib/api-client'

interface Options {
  intervalMs?: number
  enabled?: boolean
}

export function useApiResource<T>(path: string | null, options: Options = {}) {
  const { intervalMs, enabled = true } = options
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const [loadedPath, setLoadedPath] = useState<string | null>(null)
  const requestRef = useRef(0)

  const run = useCallback((targetPath: string) => {
    const requestId = ++requestRef.current
    return apiClient
      .get<T>(targetPath)
      .then((result) => {
        if (requestId !== requestRef.current) return
        setData(result)
        setError(null)
        setLoadedPath(targetPath)
      })
      .catch((err: Error) => {
        if (requestId !== requestRef.current) return
        setError(err)
        setLoadedPath(targetPath)
      })
  }, [])

  useEffect(() => {
    if (!path || !enabled) return
    void run(path)
    const timer = intervalMs ? window.setInterval(() => void run(path), intervalMs) : undefined
    return () => {
      requestRef.current += 1
      if (timer) window.clearInterval(timer)
    }
  }, [path, enabled, intervalMs, run])

  const reload = useCallback(async () => {
    if (path && enabled) await run(path)
  }, [path, enabled, run])

  const loading = Boolean(path && enabled) && loadedPath !== path

  return { data, error, loading, reload, setData }
}
