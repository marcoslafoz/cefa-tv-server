'use client'

import { useMemo, useState } from 'react'
import { IconDeviceTv, IconLoader2, IconSearch } from '@tabler/icons-react'
import { OnlineDot } from '@/components/shared/player-status'
import { Button } from '@/components/shadcn-ui/button'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import { normalizeText } from '@/lib/utils'
import type { Page, Player, Site } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export function AddPlayersDialog({
  site,
  onClose,
  onAdded,
}: {
  site: Site
  onClose: () => void
  onAdded: () => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const players = useApiResource<Player[] | Page<Player>>(`${API_V1}/players`)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [saving, setSaving] = useState(false)

  const candidates = useMemo(() => {
    const needle = normalizeText(query)
    return asList(players.data).filter(
      (player) => !player.siteId && (needle === '' || normalizeText(player.name).includes(needle))
    )
  }, [players.data, query])

  function toggle(id: string) {
    setSelected((current) =>
      current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id]
    )
  }

  async function confirm() {
    setSaving(true)
    try {
      for (const id of selected) await api.players.update(id, { siteId: site.id })
      toast.success(fmt(dict.sites.playersAdded, { count: selected.length }))
      onAdded()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.sites.addPlayersFailed))
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{dict.sites.addPlayersTitle.replace('{name}', site.name)}</DialogTitle>
          <DialogDescription>{dict.sites.addPlayersDescription}</DialogDescription>
        </DialogHeader>
        <div className="relative">
          <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
          <Input
            className="pl-8"
            placeholder={dict.sites.searchPlayers}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <div className="max-h-72 space-y-0.5 overflow-y-auto rounded-xl border p-1.5">
          {candidates.length === 0 ? (
            <p className="text-muted-foreground py-6 text-center text-sm">
              {dict.sites.noFreePlayers}
            </p>
          ) : (
            candidates.map((player) => (
              <label
                key={player.id}
                className="hover:bg-muted flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2"
              >
                <Checkbox
                  checked={selected.includes(player.id)}
                  onCheckedChange={() => toggle(player.id)}
                />
                <IconDeviceTv className="text-muted-foreground size-4" />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{player.name}</span>
                <OnlineDot online={player.online} className="size-2" />
              </label>
            ))
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {dict.common.cancel}
          </Button>
          <Button disabled={selected.length === 0 || saving} onClick={() => void confirm()}>
            {saving ? <IconLoader2 className="animate-spin" /> : null}
            {fmt(dict.sites.addSelected, { count: selected.length })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
