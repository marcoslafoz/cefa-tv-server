'use client'

import { useState } from 'react'
import { IconAlertTriangle, IconLoader2 } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { Page, PlayerGroup, Site } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

const NO_SITE = 'none'

export function DeleteSiteDialog({
  site,
  sites,
  onClose,
  onDeleted,
}: {
  site: Site
  sites: Site[]
  onClose: () => void
  onDeleted: () => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const [target, setTarget] = useState(NO_SITE)
  const [deleting, setDeleting] = useState(false)
  const others = sites.filter((entry) => entry.id !== site.id)
  const groups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const groupCount = asList(groups.data).filter((group) => group.siteId === site.id).length
  const hasDependents = site.playerCount > 0 || groupCount > 0

  async function confirm() {
    setDeleting(true)
    try {
      await api.sites.remove(site.id, target === NO_SITE ? null : target)
      toast.success(dict.sites.deleted)
      onDeleted()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.sites.deleteFailed))
      setDeleting(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{dict.sites.deleteTitle}</DialogTitle>
          <DialogDescription>
            {dict.sites.deleteDescription.replace('{name}', site.name)}
          </DialogDescription>
        </DialogHeader>
        {hasDependents ? (
          <div className="border-warning/40 bg-warning/10 space-y-3 rounded-xl border p-3 text-sm">
            <div className="text-warning space-y-1 font-medium">
              {site.playerCount > 0 ? (
                <p className="flex items-center gap-2">
                  <IconAlertTriangle className="size-4 shrink-0" />
                  {fmt(dict.sites.deleteHasPlayers, { count: site.playerCount })}
                </p>
              ) : null}
              {groupCount > 0 ? (
                <p className="flex items-center gap-2">
                  <IconAlertTriangle className="size-4 shrink-0" />
                  {fmt(dict.sites.deleteHasGroups, { count: groupCount })}
                </p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label>{dict.sites.reassignTo}</Label>
              <Select value={target} onValueChange={setTarget}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_SITE}>{dict.sites.noSiteOption}</SelectItem>
                  {others.map((entry) => (
                    <SelectItem key={entry.id} value={entry.id}>
                      {entry.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {dict.common.cancel}
          </Button>
          <Button variant="destructive" disabled={deleting} onClick={() => void confirm()}>
            {deleting ? <IconLoader2 className="animate-spin" /> : null}
            {dict.common.delete}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
