'use client'

import { useState } from 'react'
import { IconLoader2, IconPlugConnected } from '@tabler/icons-react'
import { aracastErrorMessage } from '@/components/players/aracast-errors'
import { Button } from '@/components/shadcn-ui/button'
import { DialogFooter } from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { api, asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { canAccessGroup, canAccessSite } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import { formatBytes } from '@/lib/utils'
import type { AracastStatus, Page, Player, PlayerGroup, Site } from '@/types/api'

const NONE = 'none'

export function AracastAddForm({
  onCancel,
  onAdded,
}: {
  onCancel: () => void
  onAdded: (player: Player) => void
}) {
  const dict = useDictionary()
  const text = dict.aracast
  const { user } = useAuth()
  const groups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const sites = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const [host, setHost] = useState('')
  const [status, setStatus] = useState<AracastStatus | null>(null)
  const [probing, setProbing] = useState(false)
  const [name, setName] = useState('')
  const [groupId, setGroupId] = useState(NONE)
  const [siteId, setSiteId] = useState(NONE)
  const [sshPassword, setSshPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const allowedGroups = asList(groups.data).filter((group) =>
    canAccessGroup(user, group.id, 'OPERATE')
  )
  const allowedSites = asList(sites.data).filter((site) => canAccessSite(user, site.id, 'OPERATE'))
  const scopeRequired = !user?.admin
  const hasScope = groupId !== NONE || siteId !== NONE
  const valid = host.trim() !== '' && (!scopeRequired || hasScope)

  function changeHost(value: string) {
    setHost(value)
    setStatus(null)
    setError(null)
  }

  async function probe() {
    setProbing(true)
    setStatus(null)
    setError(null)
    try {
      setStatus(await api.aracast.probe(host.trim()))
    } catch (err) {
      setError(aracastErrorMessage(err, text.errors, text.add.probeFailed))
    } finally {
      setProbing(false)
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const player = await api.aracast.create({
        host: host.trim(),
        ...(name.trim() ? { name: name.trim() } : {}),
        ...(groupId === NONE ? {} : { groupId }),
        ...(siteId === NONE ? {} : { siteId }),
        ...(sshPassword ? { sshPassword } : {}),
      })
      toast.success(text.add.success)
      onAdded(player)
    } catch (err) {
      setError(aracastErrorMessage(err, text.errors, text.add.failed))
    } finally {
      setSaving(false)
    }
  }

  const summary: Array<[string, string]> = status
    ? [
        [text.fields.model, status.model ?? '-'],
        [text.fields.version, status.version ?? '-'],
        [text.fields.mac, status.mac ?? '-'],
        [text.fields.currentTemplate, status.currentTemplate ?? '-'],
        ...(status.totalBytes !== null && status.freeBytes !== null
          ? ([
              [
                text.fields.disk,
                text.fields.diskFree
                  .replace('{free}', formatBytes(status.freeBytes))
                  .replace('{total}', formatBytes(status.totalBytes)),
              ],
            ] as Array<[string, string]>)
          : []),
      ]
    : []

  return (
    <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-2">
        <div className="space-y-2">
          <Label htmlFor="aracast-host">{text.add.host}</Label>
          <div className="flex gap-2">
            <Input
              id="aracast-host"
              autoFocus
              autoComplete="off"
              spellCheck={false}
              placeholder={text.add.hostPlaceholder}
              value={host}
              onChange={(event) => changeHost(event.target.value)}
            />
            <Button
              type="button"
              variant="outline"
              disabled={host.trim() === '' || probing}
              onClick={() => void probe()}
            >
              {probing ? <IconLoader2 className="animate-spin" /> : <IconPlugConnected />}
              {text.add.probe}
            </Button>
          </div>
        </div>

        {status ? (
          <div className="bg-muted/40 space-y-1 rounded-lg border p-3">
            <p className="text-success text-sm font-medium">{text.add.probeOk}</p>
            <dl className="divide-y text-sm">
              {summary.map(([label, value]) => (
                <div key={label} className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="text-right font-medium break-all">{value}</dd>
                </div>
              ))}
            </dl>
          </div>
        ) : null}

        <div className="space-y-2">
          <Label htmlFor="aracast-name">{text.add.name}</Label>
          <Input
            id="aracast-name"
            placeholder={text.add.namePlaceholder}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label>{dict.pairing.group}</Label>
            <Select value={groupId} onValueChange={setGroupId}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{dict.pairing.noGroup}</SelectItem>
                {allowedGroups.map((group) => (
                  <SelectItem key={group.id} value={group.id}>
                    {group.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>{dict.players.site}</Label>
            <Select value={siteId} onValueChange={setSiteId}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{dict.players.noSite}</SelectItem>
                {allowedSites.map((site) => (
                  <SelectItem key={site.id} value={site.id}>
                    {site.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {scopeRequired ? (
            <p className="text-muted-foreground text-xs sm:col-span-2">
              {text.add.scopeRequiredHint}
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="aracast-ssh-password">{text.add.sshPassword}</Label>
          <Input
            id="aracast-ssh-password"
            type="password"
            autoComplete="new-password"
            value={sshPassword}
            onChange={(event) => setSshPassword(event.target.value)}
          />
          <p className="text-muted-foreground text-xs">{text.add.sshPasswordHint}</p>
        </div>

        {error ? (
          <p role="alert" className="text-destructive bg-destructive/10 rounded-md p-2 text-sm">
            {error}
          </p>
        ) : null}
      </div>
      <DialogFooter className="border-t p-4">
        <Button type="button" variant="outline" onClick={onCancel}>
          {dict.common.cancel}
        </Button>
        <Button type="submit" disabled={!valid || saving}>
          {saving ? <IconLoader2 className="animate-spin" /> : null}
          {text.add.submit}
        </Button>
      </DialogFooter>
    </form>
  )
}
