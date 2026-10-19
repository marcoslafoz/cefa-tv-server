'use client'

import { useState } from 'react'
import { IconClock, IconLoader2, IconServer } from '@tabler/icons-react'
import { aracastErrorMessage } from '@/components/players/aracast-errors'
import { Button } from '@/components/shadcn-ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/shadcn-ui/card'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { api } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { AracastClock, AracastClockMode } from '@/types/api'

export function AracastClockCard({ playerId, online }: { playerId: string; online: boolean }) {
  const dict = useDictionary()
  const text = dict.aracast.clock
  const { formatDateTime } = useDateFormatter()
  const clock = useApiResource<AracastClock>(`${API_V1}/players/${playerId}/aracast/clock`)
  const [ntpServer, setNtpServer] = useState('')
  const [syncing, setSyncing] = useState<AracastClockMode | null>(null)

  const current = clock.data

  async function sync(mode: AracastClockMode) {
    setSyncing(mode)
    try {
      const result = await api.aracast.setClock(playerId, {
        mode,
        ...(mode === 'NTP' && ntpServer.trim() ? { server: ntpServer.trim() } : {}),
      })
      clock.setData(result)
      toast.success(result.detail ?? text.synced)
    } catch (err) {
      toast.error(aracastErrorMessage(err, dict.aracast.errors, text.syncFailed))
    } finally {
      setSyncing(null)
    }
  }

  const playerTime = current?.playerTime
    ? Number.isNaN(Date.parse(current.playerTime))
      ? current.playerTime
      : formatDateTime(current.playerTime)
    : '-'
  const offset =
    current?.offsetSeconds === null || current?.offsetSeconds === undefined
      ? '-'
      : text.offsetFormat.replace('{seconds}', String(current.offsetSeconds))

  const rows: Array<[string, string]> = [
    [text.playerTime, playerTime],
    [text.timezone, current?.timezone ?? '-'],
    [text.offset, offset],
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{text.title}</CardTitle>
        <CardDescription>{text.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {clock.error ? (
          <p className="text-destructive bg-destructive/10 rounded-md p-2 text-sm">
            {text.loadFailed}
          </p>
        ) : null}
        <dl className="divide-y text-sm">
          {rows.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-3 py-2">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="text-right font-medium break-all">{value}</dd>
            </div>
          ))}
        </dl>
        <div className="space-y-2">
          <Label htmlFor="aracast-ntp-server">{text.ntpServer}</Label>
          <Input
            id="aracast-ntp-server"
            autoComplete="off"
            spellCheck={false}
            placeholder={text.ntpServerPlaceholder}
            value={ntpServer}
            onChange={(event) => setNtpServer(event.target.value)}
          />
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            variant="outline"
            disabled={!online || syncing !== null}
            onClick={() => void sync('SERVER')}
          >
            {syncing === 'SERVER' ? <IconLoader2 className="animate-spin" /> : <IconServer />}
            {text.syncServer}
          </Button>
          <Button
            variant="outline"
            disabled={!online || syncing !== null}
            onClick={() => void sync('NTP')}
          >
            {syncing === 'NTP' ? <IconLoader2 className="animate-spin" /> : <IconClock />}
            {text.syncNtp}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
