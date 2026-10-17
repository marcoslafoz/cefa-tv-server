'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { IconArrowLeft, IconLock, IconPlugConnected, IconShieldLock } from '@tabler/icons-react'
import type { Terminal as XTerm } from '@xterm/xterm'
import '@xterm/xterm/css/xterm.css'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { EmptyState } from '@/components/shared/state-views'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useLocalePath } from '@/hooks/use-locale-path'
import { shellSocketUrl } from '@/lib/api-client'
import { RequirePermission } from '@/components/shared/require-permission'

type ShellState = 'connecting' | 'connected' | 'closed'

interface ShellMessage {
  type: 'OPENED' | 'OUTPUT' | 'EXIT' | 'ERROR'
  data?: string
  code?: number
  message?: string
}

function PlayerTerminalPageContent() {
  const dict = useDictionary()
  const text = dict.players.terminal
  const params = useParams()
  const localePath = useLocalePath()
  const { user } = useAuth()
  const id = params.id as string
  const [container, setContainer] = useState<HTMLDivElement | null>(null)
  const [state, setState] = useState<ShellState>('connecting')
  const [attempt, setAttempt] = useState(0)
  const reasonRef = useRef('')

  useEffect(() => {
    if (!container) return
    let cancelled = false
    let socket: WebSocket | null = null
    let terminal: XTerm | null = null
    let observer: ResizeObserver | null = null

    async function start(target: HTMLDivElement) {
      const [{ Terminal }, { FitAddon }] = await Promise.all([
        import('@xterm/xterm'),
        import('@xterm/addon-fit'),
      ])
      if (cancelled) return
      const styles = getComputedStyle(document.documentElement)
      const term = new Terminal({
        theme: {
          background: styles.getPropertyValue('--background').trim(),
          foreground: styles.getPropertyValue('--foreground').trim(),
          cursor: styles.getPropertyValue('--foreground').trim(),
        },
        cursorBlink: true,
        fontSize: 13,
        scrollback: 5000,
        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
      })
      terminal = term
      const fit = new FitAddon()
      term.loadAddon(fit)
      term.open(target)
      fit.fit()
      term.writeln(`\x1b[2m${text.connecting}\x1b[0m`)

      const ws = new WebSocket(shellSocketUrl(id))
      socket = ws
      const send = (message: Record<string, unknown>) => {
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(message))
      }

      ws.onopen = () => send({ type: 'OPEN', cols: term.cols, rows: term.rows })
      ws.onmessage = (event) => {
        if (typeof event.data !== 'string') return
        const message = JSON.parse(event.data) as ShellMessage
        if (message.type === 'OPENED') {
          setState('connected')
          term.focus()
        } else if (message.type === 'OUTPUT' && message.data) {
          term.write(message.data)
        } else if (message.type === 'EXIT') {
          reasonRef.current = text.exited.replace('{code}', String(message.code ?? 0))
        } else if (message.type === 'ERROR') {
          reasonRef.current = message.message ?? text.failed
        }
      }
      ws.onclose = () => {
        if (cancelled) return
        setState('closed')
        term.write(`\r\n\x1b[33m[${reasonRef.current || text.closed}]\x1b[0m\r\n`)
      }
      term.onData((data) => send({ type: 'INPUT', data }))
      observer = new ResizeObserver(() => {
        fit.fit()
        send({ type: 'RESIZE', cols: term.cols, rows: term.rows })
      })
      observer.observe(target)
    }

    reasonRef.current = ''
    void start(container)
    return () => {
      cancelled = true
      observer?.disconnect()
      if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'CLOSE' }))
      socket?.close()
      terminal?.dispose()
    }
  }, [container, id, attempt, text])

  const reconnect = useCallback(() => {
    setState('connecting')
    setAttempt((value) => value + 1)
  }, [])

  if (user && !user.admin) {
    return <EmptyState icon={IconLock} title={text.forbidden} />
  }

  const stateLabel: Record<ShellState, string> = {
    connecting: text.stateConnecting,
    connected: text.stateConnected,
    closed: text.stateClosed,
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="sm">
            <Link href={localePath(`/players/${id}`)}>
              <IconArrowLeft /> {dict.players.back}
            </Link>
          </Button>
          <h1 className="text-xl font-semibold tracking-tight">{text.title}</h1>
          <Badge variant={state === 'connected' ? 'default' : 'secondary'}>
            {stateLabel[state]}
          </Badge>
        </div>
        <Button variant="outline" disabled={state === 'connecting'} onClick={reconnect}>
          <IconPlugConnected /> {text.reconnect}
        </Button>
      </div>

      <p className="bg-muted/60 flex items-start gap-2 rounded-lg border px-3 py-2 text-sm">
        <IconShieldLock className="mt-0.5 size-4 shrink-0" />
        {text.audited}
      </p>

      <div
        ref={setContainer}
        className="bg-background h-[65vh] min-w-0 overflow-hidden rounded-xl border p-2"
      />
    </div>
  )
}

export default function PlayerTerminalPage() {
  return (
    <RequirePermission permission="PLAYER_TERMINAL">
      <PlayerTerminalPageContent />
    </RequirePermission>
  )
}
