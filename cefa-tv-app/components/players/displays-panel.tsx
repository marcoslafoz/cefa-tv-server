'use client'

import { useState } from 'react'
import { IconDeviceDesktop, IconRefresh } from '@tabler/icons-react'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { PlayerDisplay } from '@/types/api'

function currentMode(display: PlayerDisplay): string | null {
  return display.width && display.height ? `${display.width}x${display.height}` : null
}

export function DisplaysPanel({
  playerId,
  displays,
  online,
  canManage,
}: {
  playerId: string
  displays: PlayerDisplay[] | null | undefined
  online: boolean
  canManage: boolean
}) {
  const dict = useDictionary()
  const text = dict.players.displays
  const [sending, setSending] = useState(false)
  const list = displays ?? []

  async function redetect() {
    setSending(true)
    try {
      await api.players.redetectDisplay(playerId)
      toast.success(dict.players.commandSent)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.commandFailed))
    } finally {
      setSending(false)
    }
  }

  async function changeResolution(output: string, mode: string) {
    const [width, height] = mode.split('x').map(Number)
    try {
      await api.players.setResolution(playerId, { output, width, height })
      toast.success(dict.players.commandSent)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.commandFailed))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-2">
          {text.title}
          {canManage ? (
            <Button variant="outline" size="sm" disabled={!online || sending} onClick={redetect}>
              <IconRefresh /> {text.redetect}
            </Button>
          ) : null}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {list.length === 0 ? (
          <p className="text-muted-foreground py-4 text-center text-sm">{text.empty}</p>
        ) : (
          <ul className="space-y-3">
            {list.map((display) => {
              const mode = currentMode(display)
              const modes = display.modes ?? []
              return (
                <li key={display.output} className="space-y-2 rounded-lg border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <IconDeviceDesktop className="text-muted-foreground size-4" />
                      <span className="font-medium">{display.output}</span>
                      {display.selected ? <Badge>{text.selected}</Badge> : null}
                      {display.primary ? <Badge variant="outline">{text.primary}</Badge> : null}
                      {!display.connected ? (
                        <Badge variant="secondary">{text.disconnected}</Badge>
                      ) : null}
                    </div>
                    <span className="text-muted-foreground text-sm tabular-nums">
                      {mode ? mode.replace('x', ' × ') : '-'}
                      {display.refreshHz ? ` · ${Math.round(display.refreshHz)} Hz` : ''}
                    </span>
                  </div>
                  {canManage && display.connected && modes.length > 0 ? (
                    <div className="flex items-center gap-2">
                      <span className="text-muted-foreground text-sm">{text.resolution}</span>
                      <Select
                        value={mode ?? undefined}
                        disabled={!online}
                        onValueChange={(value) => void changeResolution(display.output, value)}
                      >
                        <SelectTrigger className="w-44">
                          <SelectValue placeholder={text.choose} />
                        </SelectTrigger>
                        <SelectContent>
                          {modes.map((item) => (
                            <SelectItem key={item} value={item}>
                              {item.replace('x', ' × ')}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  ) : null}
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
