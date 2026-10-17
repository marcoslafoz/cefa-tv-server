import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

type RefCallback = (element: Element | null) => void

export function useVisibleIds() {
  const [visible, setVisible] = useState<Set<string>>(new Set())
  const observerRef = useRef<IntersectionObserver | null>(null)
  const elementsRef = useRef<Map<string, Element>>(new Map())
  const callbacksRef = useRef<Map<string, RefCallback>>(new Map())

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        setVisible((current) => {
          const next = new Set(current)
          for (const entry of entries) {
            const id = (entry.target as HTMLElement).dataset.playerId
            if (!id) continue
            if (entry.isIntersecting) next.add(id)
            else next.delete(id)
          }
          return next.size === current.size && [...next].every((id) => current.has(id))
            ? current
            : next
        })
      },
      { rootMargin: '120px' }
    )
    observerRef.current = observer
    elementsRef.current.forEach((element) => observer.observe(element))
    return () => {
      observer.disconnect()
      observerRef.current = null
    }
  }, [])

  const register = useCallback((id: string): RefCallback => {
    const cached = callbacksRef.current.get(id)
    if (cached) return cached
    const callback: RefCallback = (element) => {
      const previous = elementsRef.current.get(id)
      if (element === null) {
        if (previous) observerRef.current?.unobserve(previous)
        elementsRef.current.delete(id)
        setVisible((current) => {
          if (!current.has(id)) return current
          const next = new Set(current)
          next.delete(id)
          return next
        })
        return
      }
      if (previous === element) return
      if (previous) observerRef.current?.unobserve(previous)
      elementsRef.current.set(id, element)
      observerRef.current?.observe(element)
    }
    callbacksRef.current.set(id, callback)
    return callback
  }, [])

  const visibleIds = useMemo(() => Array.from(visible), [visible])

  return { visibleIds, register }
}
