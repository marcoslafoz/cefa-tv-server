'use client'

import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { cn } from '@/lib/utils'
import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  IconInfoCircle,
  IconLayoutGrid,
  IconMapPin,
  IconPencil,
  IconPlus,
  IconTrash,
} from '@tabler/icons-react'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { GroupBadge } from '@/components/shared/group-icon'
import { ColorPicker, IconPicker } from '@/components/shared/icon-picker'
import {
  DEFAULT_IDLE_LOGO,
  IDLE_LOGO_INHERIT,
  IdleLogoSelect,
  type IdleLogoValue,
} from '@/components/players/idle-logo-select'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
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
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { hasPermission } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import type { Page, PlayerGroup, Site } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

const NO_SITE = 'none'

function GroupsContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const canManage = hasPermission(user, 'PLAYER_MANAGE')
  const resource = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const sitesResource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const [editing, setEditing] = useState<PlayerGroup | 'new' | null>(null)
  const [name, setName] = useState('')
  const [icon, setIcon] = useState<string | null>(null)
  const [color, setColor] = useState<string | null>(null)
  const [siteId, setSiteId] = useState(NO_SITE)
  const [idleLogo, setIdleLogo] = useState<IdleLogoValue>(IDLE_LOGO_INHERIT)
  const router = useRouter()
  const [toDelete, setToDelete] = useState<PlayerGroup | null>(null)

  function openEditor(group: PlayerGroup | 'new') {
    setName(group === 'new' ? '' : group.name)
    setIcon(group === 'new' ? null : (group.icon ?? null))
    setColor(group === 'new' ? null : (group.color ?? null))
    setSiteId(group === 'new' ? NO_SITE : (group.siteId ?? NO_SITE))
    setIdleLogo(group === 'new' ? IDLE_LOGO_INHERIT : (group.idleLogo ?? IDLE_LOGO_INHERIT))
    setEditing(group)
  }

  async function save(event: React.FormEvent) {
    event.preventDefault()
    try {
      if (editing === 'new') {
        await api.groups.create({
          name: name.trim(),
          icon,
          color,
          ...(siteId === NO_SITE ? {} : { siteId }),
          ...(!user?.admin || idleLogo === IDLE_LOGO_INHERIT ? {} : { idleLogo }),
        })
      } else if (editing) {
        await api.groups.update(editing.id, {
          name: name.trim(),
          fallbackPlaylistId: editing.fallbackPlaylistId ?? null,
          icon,
          color,
          ...(siteId === NO_SITE ? { clearSite: Boolean(editing.siteId) } : { siteId }),
          ...(!user?.admin
            ? {}
            : idleLogo === IDLE_LOGO_INHERIT
              ? { clearIdleLogo: Boolean(editing.idleLogo) }
              : { idleLogo }),
        })
      }
      setEditing(null)
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.groups.saveFailed))
    }
  }

  async function remove(group: PlayerGroup) {
    try {
      await api.groups.remove(group.id)
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.groups.deleteFailed))
    }
  }

  const siteList = asList(sitesResource.data)
  const chosenSite = siteList.find((site) => site.id === siteId)
  const currentSiteId =
    editing !== null && editing !== 'new' ? (editing.siteId ?? NO_SITE) : NO_SITE
  const memberCount = editing !== null && editing !== 'new' ? (editing.playerCount ?? 0) : 0
  const chosenName = (chosenSite?.name ?? '').replace(/[{}]/g, '')
  const siteNotice =
    editing === null || siteId === currentSiteId
      ? null
      : chosenSite
        ? memberCount > 0
          ? fmt(dict.groups.siteWarning.replaceAll('{site}', chosenName), { count: memberCount })
          : fmt(dict.groups.siteFuture, { site: chosenName })
        : dict.groups.siteClearNote

  if (resource.loading && !resource.data) return <PageSkeleton rows={2} />
  if (resource.error) return <ErrorState onRetry={() => void resource.reload()} />
  const groups = asList(resource.data)

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.groups.title}
        description={dict.groups.description}
        actions={
          canManage ? (
            <Button onClick={() => openEditor('new')}>
              <IconPlus /> {dict.groups.new}
            </Button>
          ) : null
        }
      />
      {groups.length === 0 ? (
        <EmptyState
          icon={IconLayoutGrid}
          title={dict.groups.emptyTitle}
          description={dict.groups.emptyDescription}
        />
      ) : (
        <Card className="divide-y p-0">
          {groups.map((group) => (
            <div
              key={group.id}
              {...rowActivation(() => router.push(localePath(`/groups/${group.id}`)))}
              className={cn(
                'hover:bg-accent/50 flex items-center justify-between gap-3 px-4 py-3',
                ROW_CLASS
              )}
            >
              <div className="flex min-w-0 items-center gap-3">
                <GroupBadge icon={group.icon} color={group.color} />
                <div className="min-w-0">
                  <Link
                    href={localePath(`/groups/${group.id}`)}
                    className="truncate text-sm font-medium hover:underline"
                  >
                    {group.name}
                  </Link>
                  {group.playerCount !== undefined ? (
                    <p className="text-muted-foreground text-xs">
                      {fmt(dict.groups.players, { count: group.playerCount })}
                    </p>
                  ) : null}
                  {group.siteName ? (
                    <p className="text-muted-foreground flex items-center gap-1 truncate text-xs">
                      <IconMapPin className="size-3 shrink-0" />
                      <span className="truncate">{group.siteName}</span>
                    </p>
                  ) : null}
                </div>
              </div>
              <div className="flex items-center gap-1">
                <Button asChild size="sm" variant="outline">
                  <Link href={localePath(`/groups/${group.id}`)}>{dict.groups.open}</Link>
                </Button>
                {canManage ? (
                  <>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={dict.common.edit}
                      onClick={() => openEditor(group)}
                    >
                      <IconPencil />
                    </Button>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={dict.common.delete}
                      onClick={() => setToDelete(group)}
                    >
                      <IconTrash />
                    </Button>
                  </>
                ) : null}
              </div>
            </div>
          ))}
        </Card>
      )}

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <form onSubmit={save} className="space-y-4">
            <DialogHeader>
              <DialogTitle>{editing === 'new' ? dict.groups.new : dict.groups.rename}</DialogTitle>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="group-name">{dict.common.name}</Label>
              <Input
                id="group-name"
                required
                autoFocus
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>{dict.groups.siteLabel}</Label>
              <Select value={siteId} onValueChange={setSiteId}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_SITE}>{dict.players.noSite}</SelectItem>
                  {siteList.map((site) => (
                    <SelectItem key={site.id} value={site.id}>
                      {site.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-muted-foreground text-xs">{dict.groups.siteHint}</p>
              {siteNotice ? (
                <p className="border-warning/40 bg-warning/10 text-warning flex items-start gap-2 rounded-lg border p-2.5 text-xs">
                  <IconInfoCircle className="mt-0.5 size-4 shrink-0" />
                  <span>{siteNotice}</span>
                </p>
              ) : null}
            </div>
            {user?.admin ? (
              <IdleLogoSelect
                value={idleLogo}
                onChange={setIdleLogo}
                hint={dict.idleLogo.groupHint}
                inheritLogo={DEFAULT_IDLE_LOGO}
                inheritLabel={dict.idleLogo.default}
              />
            ) : null}
            <div className="space-y-2">
              <Label>{dict.iconPicker.icon}</Label>
              <IconPicker value={icon} onChange={setIcon} color={color} />
            </div>
            <div className="space-y-2">
              <Label>{dict.iconPicker.color}</Label>
              <ColorPicker value={color} onChange={setColor} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>
                {dict.common.cancel}
              </Button>
              <Button type="submit">{dict.common.save}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title={dict.groups.deleteTitle}
        description={dict.groups.deleteDescription.replace('{name}', toDelete?.name ?? '')}
        confirmLabel={dict.common.delete}
        onConfirm={() => {
          if (toDelete) void remove(toDelete)
          setToDelete(null)
        }}
      />
    </div>
  )
}

export default function GroupsPage() {
  return (
    <RequirePermission permission="PLAYER_VIEW">
      <GroupsContent />
    </RequirePermission>
  )
}
