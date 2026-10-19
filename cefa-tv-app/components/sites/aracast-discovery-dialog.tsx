'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { IconLoader2, IconPlus, IconRadar2 } from '@tabler/icons-react'
import { aracastErrorMessage } from '@/components/players/aracast-errors'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { useFormat } from '@/hooks/use-format'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api } from '@/lib/api'
import { toast } from '@/lib/toast'
import type { AracastDiscovery, AracastFound, Site } from '@/types/api'

export function AracastDiscoveryDialog({
  site,
  onClose,
  onAdded,
}: {
  site: Site
  onClose: () => void
  onAdded: () => void
}) {
  const dict = useDictionary()
  const text = dict.aracast.discovery
  const fmt = useFormat()
  const localePath = useLocalePath()
  const [result, setResult] = useState<AracastDiscovery | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [scanning, setScanning] = useState(true)
  const [attempt, setAttempt] = useState(0)
  const [sshPassword, setSshPassword] = useState('')
  const [adding, setAdding] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api.sites
      .discoverAracast(site.id)
      .then((discovery) => {
        if (!active) return
        setResult(discovery)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!active) return
        setResult(null)
        setError(aracastErrorMessage(err, dict.aracast.errors, text.failed))
      })
      .finally(() => {
        if (active) setScanning(false)
      })
    return () => {
      active = false
    }
  }, [site.id, attempt, dict.aracast.errors, text.failed])

  function rescan() {
    setScanning(true)
    setAttempt((value) => value + 1)
  }

  async function add(found: AracastFound) {
    setAdding(found.host)
    try {
      const player = await api.aracast.create({
        host: found.host,
        siteId: site.id,
        ...(sshPassword ? { sshPassword } : {}),
      })
      setResult((previous) =>
        previous
          ? {
              ...previous,
              found: previous.found.map((entry) =>
                entry.host === found.host
                  ? { ...entry, registered: true, playerId: player.id }
                  : entry
              ),
            }
          : previous
      )
      toast.success(text.added)
      onAdded()
    } catch (err) {
      toast.error(aracastErrorMessage(err, dict.aracast.errors, dict.aracast.add.failed))
    } finally {
      setAdding(null)
    }
  }

  const hasNew = result?.found.some((entry) => !entry.registered) ?? false

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{text.title.replace('{name}', site.name)}</DialogTitle>
          <DialogDescription>{text.description}</DialogDescription>
        </DialogHeader>

        {scanning ? (
          <div className="text-muted-foreground flex items-center justify-center gap-2 py-10 text-sm">
            <IconLoader2 className="size-4 animate-spin" /> {text.scanning}
          </div>
        ) : null}

        {!scanning && error ? (
          <p role="alert" className="text-destructive bg-destructive/10 rounded-md p-3 text-sm">
            {error}
          </p>
        ) : null}

        {!scanning && result ? (
          <div className="space-y-4">
            <p className="text-muted-foreground text-xs">
              {fmt(text.summary, {
                scanned: result.scanned,
                seconds: Math.max(1, Math.round(result.durationMs / 1000)),
              })}
            </p>

            {result.found.length === 0 ? (
              <p className="text-muted-foreground rounded-lg border border-dashed p-6 text-center text-sm">
                {text.noneFound}
              </p>
            ) : (
              <>
                {hasNew ? (
                  <div className="space-y-2">
                    <Label htmlFor="discovery-ssh-password">{text.sshPassword}</Label>
                    <Input
                      id="discovery-ssh-password"
                      type="password"
                      autoComplete="new-password"
                      value={sshPassword}
                      onChange={(event) => setSshPassword(event.target.value)}
                    />
                    <p className="text-muted-foreground text-xs">
                      {dict.aracast.add.sshPasswordHint}
                    </p>
                  </div>
                ) : null}
                <ul className="divide-y rounded-lg border">
                  {result.found.map((entry) => (
                    <li
                      key={entry.host}
                      className="flex flex-wrap items-center justify-between gap-3 p-3"
                    >
                      <div className="min-w-0 space-y-0.5">
                        <p className="truncate text-sm font-medium">
                          {entry.hostname ?? entry.host}
                          <span className="text-muted-foreground font-mono text-xs">
                            {entry.hostname ? ` · ${entry.host}` : ''}
                          </span>
                        </p>
                        <p className="text-muted-foreground truncate text-xs">
                          {[entry.model, entry.version, entry.mac].filter(Boolean).join(' · ') ||
                            '-'}
                        </p>
                      </div>
                      {entry.registered ? (
                        <div className="flex items-center gap-2">
                          <Badge variant="secondary">{text.registered}</Badge>
                          {entry.playerId ? (
                            <Button asChild size="sm" variant="ghost">
                              <Link href={localePath(`/players/${entry.playerId}`)}>
                                {text.open}
                              </Link>
                            </Button>
                          ) : null}
                        </div>
                      ) : (
                        <Button
                          size="sm"
                          disabled={adding !== null}
                          onClick={() => void add(entry)}
                        >
                          {adding === entry.host ? (
                            <IconLoader2 className="animate-spin" />
                          ) : (
                            <IconPlus />
                          )}
                          {dict.common.add}
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              </>
            )}

            {result.offline.length > 0 ? (
              <div className="space-y-2">
                <p className="text-sm font-medium">{text.offlineTitle}</p>
                <p className="text-muted-foreground text-xs">{text.offlineHint}</p>
                <div className="flex flex-wrap gap-1.5">
                  {result.offline.map((host) => (
                    <Badge key={host} variant="destructive" className="font-mono">
                      {host}
                    </Badge>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        ) : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {dict.common.close}
          </Button>
          <Button disabled={scanning} onClick={rescan}>
            <IconRadar2 /> {text.rescan}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
