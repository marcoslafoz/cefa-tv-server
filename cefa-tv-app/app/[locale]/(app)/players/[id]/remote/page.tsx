'use client'

import { Textarea } from '@/components/shadcn-ui/textarea'
import { useCallback, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import {
  IconArrowLeft,
  IconInfoCircle,
  IconKeyboard,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlayerStop,
} from '@tabler/icons-react'
import {
  ARROW_KEYS,
  MODIFIER_CTRL,
  SHORTCUTS,
  SPECIAL_KEYS,
  isModifierKey,
  isPrintableKey,
  remoteModifiers,
  safeCursor,
  type RemoteKey,
} from '@/components/remote/keys'
import { RequirePermission } from '@/components/shared/require-permission'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { useDictionary } from '@/contexts/dictionary-context'
import { useLocalePath } from '@/hooks/use-locale-path'
import { remoteSocketUrl } from '@/lib/api-client'
import { cn } from '@/lib/utils'
import type { RemoteInput } from '@/types/api'

type ConnectionState = 'idle' | 'connecting' | 'live' | 'offline' | 'closed'

const MOVE_INTERVAL_MS = 25

function buttonName(button: number): 'left' | 'middle' | 'right' {
  if (button === 1) return 'middle'
  if (button === 2) return 'right'
  return 'left'
}

function frameRect(canvas: HTMLCanvasElement) {
  const rect = canvas.getBoundingClientRect()
  const aspect = canvas.width / canvas.height
  let width = rect.width
  let height = rect.height
  if (width / height > aspect) width = height * aspect
  else height = width / aspect
  return {
    left: rect.left + (rect.width - width) / 2,
    top: rect.top + (rect.height - height) / 2,
    width,
    height,
  }
}

function RemoteControl() {
  const dict = useDictionary()
  const params = useParams()
  const localePath = useLocalePath()
  const id = params.id as string
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const socketRef = useRef<WebSocket | null>(null)
  const lastMoveRef = useRef(0)
  const framesRef = useRef(0)
  const sentAtRef = useRef<number | null>(null)
  const pressedRef = useRef(new Set<string>())
  const macLayoutRef = useRef(false)
  const [state, setState] = useState<ConnectionState>('connecting')
  const [fps, setFps] = useState(0)
  const [latency, setLatency] = useState<number | null>(null)
  const [text, setText] = useState('')
  const [cursor, setCursor] = useState('default')
  const [focused, setFocused] = useState(false)

  useEffect(() => {
    macLayoutRef.current = /Mac|iPhone|iPad/.test(navigator.platform)
  }, [])

  const send = useCallback((message: Record<string, unknown>) => {
    const socket = socketRef.current
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(message))
  }, [])

  const sendInput = useCallback(
    (input: Omit<RemoteInput, 'type'>) => send({ type: 'INPUT', ...input }),
    [send]
  )

  const tapKey = useCallback(
    (key: RemoteKey, modifiers = 0) => {
      sendInput({
        kind: 'KEY_DOWN',
        key: key.key,
        code: key.code,
        windowsVirtualKeyCode: key.keyCode,
        modifiers,
      })
      sendInput({
        kind: 'KEY_UP',
        key: key.key,
        code: key.code,
        windowsVirtualKeyCode: key.keyCode,
        modifiers,
      })
      inputRef.current?.focus()
    },
    [sendInput]
  )

  const stop = useCallback(() => {
    const socket = socketRef.current
    if (socket) {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'STOP' }))
      socket.close()
    }
    socketRef.current = null
    setState('closed')
    setFps(0)
  }, [])

  const connect = useCallback(() => {
    socketRef.current?.close()
    const socket = new WebSocket(remoteSocketUrl(id))
    socket.binaryType = 'blob'
    socketRef.current = socket

    socket.onopen = () => {
      socket.send(JSON.stringify({ type: 'START', fps: 12, quality: 70, maxWidth: 1600 }))
    }
    socket.onmessage = async (event) => {
      if (typeof event.data === 'string') {
        try {
          const message = JSON.parse(event.data) as {
            type: string
            online?: boolean
            css?: string
          }
          if (message.type === 'STATE')
            setState(message.online === false ? 'offline' : 'connecting')
          if (message.type === 'CURSOR') setCursor(safeCursor(message.css))
        } catch {
          return
        }
        return
      }
      const bitmap = await createImageBitmap(event.data as Blob)
      const canvas = canvasRef.current
      if (!canvas) return
      if (canvas.width !== bitmap.width || canvas.height !== bitmap.height) {
        canvas.width = bitmap.width
        canvas.height = bitmap.height
      }
      canvas.getContext('2d')?.drawImage(bitmap, 0, 0)
      bitmap.close()
      framesRef.current += 1
      if (sentAtRef.current !== null) {
        setLatency(Math.round(performance.now() - sentAtRef.current))
        sentAtRef.current = null
      }
      setState('live')
    }
    socket.onclose = () => {
      if (socketRef.current === socket) {
        socketRef.current = null
        setState('closed')
      }
    }
  }, [id])

  const start = useCallback(() => {
    setState('connecting')
    connect()
  }, [connect])

  useEffect(() => {
    connect()
    return () => {
      const socket = socketRef.current
      socketRef.current = null
      socket?.close()
    }
  }, [connect])

  useEffect(() => {
    const timer = window.setInterval(() => {
      setFps(framesRef.current)
      framesRef.current = 0
    }, 1000)
    return () => window.clearInterval(timer)
  }, [])

  const position = useCallback((clientX: number, clientY: number) => {
    const canvas = canvasRef.current
    if (!canvas) return { x: 0, y: 0 }
    const rect = frameRect(canvas)
    return {
      x: Math.min(Math.max((clientX - rect.left) / rect.width, 0), 1),
      y: Math.min(Math.max((clientY - rect.top) / rect.height, 0), 1),
    }
  }, [])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      sendInput({
        kind: 'MOUSE_WHEEL',
        ...position(event.clientX, event.clientY),
        deltaX: event.deltaX,
        deltaY: event.deltaY,
      })
    }
    canvas.addEventListener('wheel', onWheel, { passive: false })
    return () => canvas.removeEventListener('wheel', onWheel)
  }, [sendInput, position])

  function onKeyDown(event: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (event.nativeEvent.isComposing || isModifierKey(event.key)) return
    const pasteShortcut =
      (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'v' && !event.altKey
    if (pasteShortcut || isPrintableKey(event)) return
    event.preventDefault()
    sentAtRef.current = performance.now()
    pressedRef.current.add(event.code)
    sendInput({
      kind: 'KEY_DOWN',
      key: event.key,
      code: event.code,
      windowsVirtualKeyCode: event.keyCode,
      modifiers: remoteModifiers(event, macLayoutRef.current),
    })
  }

  function onKeyUp(event: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (!pressedRef.current.delete(event.code)) return
    event.preventDefault()
    sendInput({
      kind: 'KEY_UP',
      key: event.key,
      code: event.code,
      windowsVirtualKeyCode: event.keyCode,
      modifiers: remoteModifiers(event, macLayoutRef.current),
    })
  }

  function commitText(value: string | null | undefined) {
    const textarea = inputRef.current
    if (textarea) textarea.value = ''
    if (!value) return
    sentAtRef.current = performance.now()
    sendInput({ kind: 'TEXT', text: value })
  }

  const live = state === 'live'
  const active = state === 'live' || state === 'connecting'

  const stateLabel: Record<ConnectionState, string> = {
    idle: dict.remote.stateIdle,
    connecting: dict.remote.stateConnecting,
    live: dict.remote.stateLive,
    offline: dict.remote.stateOffline,
    closed: dict.remote.stateClosed,
  }
  const keyNames = dict.remote.keyNames as Record<string, string>

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="sm">
            <Link href={localePath(`/players/${id}`)}>
              <IconArrowLeft /> {dict.players.back}
            </Link>
          </Button>
          <h1 className="text-xl font-semibold tracking-tight">{dict.remote.title}</h1>
          <Badge variant={live ? 'default' : 'secondary'}>{stateLabel[state]}</Badge>
          {live ? (
            <span className="text-muted-foreground text-xs tabular-nums">
              {fps} fps{latency !== null ? ` · ${latency} ms` : ''}
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          {live ? (
            <Badge variant={focused ? 'secondary' : 'outline'} className="gap-1.5">
              <IconKeyboard className="size-3.5" />
              {focused ? dict.remote.focused : dict.remote.unfocused}
            </Badge>
          ) : null}
          {active ? (
            <Button variant="outline" onClick={stop}>
              <IconPlayerStop /> {dict.remote.stop}
            </Button>
          ) : (
            <Button onClick={start}>
              <IconPlayerPlay /> {dict.remote.start}
            </Button>
          )}
        </div>
      </div>

      <div className="border-warning/40 bg-warning/10 flex items-start gap-3 rounded-xl border px-4 py-3 text-sm">
        {live ? (
          <IconPlayerPause className="text-warning mt-0.5 size-4 shrink-0" />
        ) : (
          <IconInfoCircle className="text-warning mt-0.5 size-4 shrink-0" />
        )}
        <div>
          <p className="font-medium">{dict.remote.pauseNoticeTitle}</p>
          <p className="text-muted-foreground">{dict.remote.pauseNoticeBody}</p>
        </div>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row">
        <div className="bg-muted/40 relative flex flex-1 items-center justify-center rounded-xl border p-2">
          <canvas
            ref={canvasRef}
            width={1280}
            height={720}
            style={{ cursor: live ? cursor : 'not-allowed' }}
            className={cn(
              'bg-foreground/90 max-h-[75vh] w-full touch-none rounded-lg object-contain outline-none',
              !live && 'opacity-60'
            )}
            onContextMenu={(event) => event.preventDefault()}
            onPointerMove={(event) => {
              const now = performance.now()
              if (now - lastMoveRef.current < MOVE_INTERVAL_MS) return
              lastMoveRef.current = now
              sendInput({ kind: 'MOUSE_MOVE', ...position(event.clientX, event.clientY) })
            }}
            onPointerDown={(event) => {
              event.preventDefault()
              event.currentTarget.setPointerCapture(event.pointerId)
              inputRef.current?.focus()
              sentAtRef.current = performance.now()
              sendInput({
                kind: 'MOUSE_DOWN',
                ...position(event.clientX, event.clientY),
                button: buttonName(event.button),
              })
            }}
            onPointerUp={(event) => {
              sendInput({
                kind: 'MOUSE_UP',
                ...position(event.clientX, event.clientY),
                button: buttonName(event.button),
              })
            }}
          />
          <Textarea
            ref={inputRef}
            aria-label={dict.remote.keyboard}
            autoCapitalize="off"
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
            className="pointer-events-none absolute top-0 left-0 size-px min-h-0 resize-none border-0 p-0 opacity-0"
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            onKeyDown={onKeyDown}
            onKeyUp={onKeyUp}
            onInput={(event) => {
              const native = event.nativeEvent as InputEvent
              if (native.isComposing) return
              commitText(native.data ?? event.currentTarget.value)
            }}
            onCompositionEnd={(event) => commitText(event.data)}
            onPaste={(event) => {
              event.preventDefault()
              commitText(event.clipboardData.getData('text'))
            }}
          />
        </div>

        <div className="w-full space-y-3 lg:w-80">
          <div className="space-y-2 rounded-xl border p-4">
            <p className="flex items-center gap-2 text-sm font-medium">
              <IconKeyboard className="size-4" /> {dict.remote.sendText}
            </p>
            <form
              className="flex gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                if (!text) return
                sendInput({ kind: 'TEXT', text })
                setText('')
              }}
            >
              <Input
                value={text}
                onChange={(event) => setText(event.target.value)}
                placeholder={dict.remote.textPlaceholder}
                disabled={!live}
              />
              <Button type="submit" disabled={!live || !text}>
                {dict.remote.send}
              </Button>
            </form>
          </div>

          <div className="space-y-3 rounded-xl border p-4">
            <p className="text-sm font-medium">{dict.remote.keys}</p>
            <div className="flex flex-wrap gap-1.5">
              {SPECIAL_KEYS.map((key) => (
                <Button
                  key={key.key}
                  type="button"
                  size="xs"
                  variant="outline"
                  disabled={!live}
                  onClick={() => tapKey(key)}
                >
                  {keyNames[key.key] ?? key.key}
                </Button>
              ))}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {ARROW_KEYS.map((key) => (
                <Button
                  key={key.key}
                  type="button"
                  size="xs"
                  variant="outline"
                  disabled={!live}
                  aria-label={key.key}
                  onClick={() => tapKey(key)}
                >
                  {key.symbol}
                </Button>
              ))}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {SHORTCUTS.map((shortcut) => (
                <Button
                  key={shortcut.label}
                  type="button"
                  size="xs"
                  variant="outline"
                  disabled={!live}
                  onClick={() => tapKey(shortcut.key, MODIFIER_CTRL)}
                >
                  {shortcut.label}
                </Button>
              ))}
            </div>
          </div>
          <p className="text-muted-foreground text-xs">
            {live && !focused ? dict.remote.focusHint : dict.remote.help}
          </p>
        </div>
      </div>
    </div>
  )
}

export default function RemoteControlPage() {
  return (
    <RequirePermission permission="PLAYER_REMOTE">
      <RemoteControl />
    </RequirePermission>
  )
}
