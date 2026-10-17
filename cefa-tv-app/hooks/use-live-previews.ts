import { useCallback, useEffect, useRef, useState } from 'react'
import { previewsSocketUrl } from '@/lib/api-client'

const MAX_SUBSCRIBED = 48
const RENEW_MS = 15000
const RESUBSCRIBE_DEBOUNCE_MS = 400
const ID_BYTES = 36

export interface PreviewFrame {
  url: string
  at: number
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function useLivePreviews(enabled: boolean, visibleIds: string[]) {
  const [frames, setFrames] = useState<Map<string, PreviewFrame>>(new Map())
  const socketRef = useRef<WebSocket | null>(null)
  const idsRef = useRef<string[]>([])
  const framesRef = useRef<Map<string, PreviewFrame>>(new Map())
  const [pageVisible, setPageVisible] = useState(true)

  const subscribe = useCallback(() => {
    const socket = socketRef.current
    if (!socket || socket.readyState !== WebSocket.OPEN) return
    const ids = idsRef.current.slice(0, MAX_SUBSCRIBED)
    if (ids.length === 0) {
      socket.send(JSON.stringify({ type: 'UNSUBSCRIBE' }))
      return
    }
    socket.send(
      JSON.stringify({
        type: 'SUBSCRIBE',
        playerIds: ids,
        intervalSec: prefersReducedMotion() ? 5 : 2,
      })
    )
  }, [])

  useEffect(() => {
    const onChange = () => setPageVisible(document.visibilityState === 'visible')
    document.addEventListener('visibilitychange', onChange)
    return () => document.removeEventListener('visibilitychange', onChange)
  }, [])

  useEffect(() => {
    idsRef.current = visibleIds
    const timer = window.setTimeout(subscribe, RESUBSCRIBE_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [visibleIds, subscribe])

  useEffect(() => {
    if (!enabled || !pageVisible) return
    let closed = false
    let renewTimer: number | undefined
    let retryTimer: number | undefined
    let attempt = 0

    function connect() {
      const socket = new WebSocket(previewsSocketUrl())
      socket.binaryType = 'arraybuffer'
      socketRef.current = socket
      socket.onopen = () => {
        attempt = 0
        subscribe()
        renewTimer = window.setInterval(subscribe, RENEW_MS)
      }
      socket.onmessage = (event) => {
        if (!(event.data instanceof ArrayBuffer) || event.data.byteLength <= ID_BYTES) return
        const bytes = new Uint8Array(event.data)
        const playerId = new TextDecoder('ascii').decode(bytes.subarray(0, ID_BYTES))
        const url = URL.createObjectURL(
          new Blob([bytes.subarray(ID_BYTES)], { type: 'image/jpeg' })
        )
        const previous = framesRef.current.get(playerId)
        if (previous) URL.revokeObjectURL(previous.url)
        framesRef.current.set(playerId, { url, at: Date.now() })
        setFrames(new Map(framesRef.current))
      }
      socket.onclose = () => {
        window.clearInterval(renewTimer)
        if (closed) return
        attempt += 1
        retryTimer = window.setTimeout(connect, Math.min(2000 * attempt, 15000))
      }
    }

    connect()

    return () => {
      closed = true
      window.clearInterval(renewTimer)
      window.clearTimeout(retryTimer)
      const socket = socketRef.current
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'UNSUBSCRIBE' }))
      }
      socket?.close()
      socketRef.current = null
      framesRef.current.forEach((frame) => URL.revokeObjectURL(frame.url))
      framesRef.current = new Map()
      setFrames(new Map())
    }
  }, [enabled, pageVisible, subscribe])

  return frames
}
