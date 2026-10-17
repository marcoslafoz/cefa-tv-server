'use client'

import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { cn } from '@/lib/utils'
import { useMemo, useState } from 'react'
import { IconLock, IconPencil, IconPlus, IconTrash, IconUsersGroup } from '@tabler/icons-react'
import { GrantsEditor } from '@/components/access/grants-editor'
import { PermissionChecklist } from '@/components/access/permission-checklist'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { TitleFields } from '@/components/shared/title-fields'
import { Input } from '@/components/shadcn-ui/input'
import { Switch } from '@/components/shadcn-ui/switch'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useFormat } from '@/hooks/use-format'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { PERMISSION_GROUPS } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import { normalizeText } from '@/lib/utils'
import type { AccessGrant, AppUser, Page, Permission, PlayerGroup, UserGroup } from '@/types/api'

const ALL_PERMISSIONS: Permission[] = PERMISSION_GROUPS.flatMap((group) => group.items)

const PRESETS: Array<{ id: 'readOnly' | 'scheduler' | 'all'; permissions: Permission[] }> = [
  { id: 'readOnly', permissions: ['PLAYER_VIEW'] },
  {
    id: 'scheduler',
    permissions: [
      'MEDIA_MANAGE',
      'PLAYLIST_MANAGE',
      'SCHEDULE_MANAGE',
      'PLAYER_VIEW',
      'PLAYER_REMOTE',
    ],
  },
  { id: 'all', permissions: ALL_PERMISSIONS },
]

function Section({
  title,
  hint,
  children,
}: {
  title: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <section className="space-y-3 rounded-xl border p-4">
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        {hint ? <p className="text-muted-foreground text-xs">{hint}</p> : null}
      </div>
      {children}
    </section>
  )
}

function UserGroupDialog({
  group,
  users,
  playerGroups,
  onClose,
  onSaved,
}: {
  group: UserGroup | 'new'
  users: AppUser[]
  playerGroups: PlayerGroup[]
  onClose: () => void
  onSaved: () => void
}) {
  const dict = useDictionary()
  const { user: current } = useAuth()
  const existing = group === 'new' ? null : group
  const locked = Boolean(existing?.builtin)
  const [name, setName] = useState(existing?.name ?? '')
  const [description, setDescription] = useState(existing?.description ?? '')
  const [memberIds, setMemberIds] = useState<string[]>(existing?.memberIds ?? [])
  const [permissions, setPermissions] = useState<Permission[]>(existing?.permissions ?? [])
  const [allAccess, setAllAccess] = useState(existing?.allAccess ?? false)
  const [grants, setGrants] = useState<AccessGrant[]>(existing?.grants ?? [])
  const [query, setQuery] = useState('')
  const [saving, setSaving] = useState(false)

  const filtered = useMemo(() => {
    const needle = normalizeText(query)
    return users.filter(
      (user) =>
        needle === '' ||
        normalizeText(`${user.displayName ?? ''} ${user.username}`).includes(needle)
    )
  }, [users, query])

  function toggleMember(id: string) {
    setMemberIds((current) =>
      current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id]
    )
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      const input = {
        name: name.trim(),
        description: description.trim() || null,
        memberIds,
        permissions,
        allAccess,
        grants: allAccess ? [] : grants,
      }
      if (existing) await api.userGroups.update(existing.id, input)
      else await api.userGroups.create(input)
      toast.success(dict.userGroups.saved)
      onSaved()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.userGroups.saveFailed))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 p-0 sm:max-w-3xl">
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="p-6 pb-3">
            <DialogTitle>
              {existing ? dict.userGroups.editTitle : dict.userGroups.newTitle}
            </DialogTitle>
          </DialogHeader>
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-6 py-2">
            {locked ? (
              <div className="bg-muted/50 flex items-start gap-3 rounded-xl border p-3 text-sm">
                <IconLock className="text-muted-foreground mt-0.5 size-4 shrink-0" />
                <p>{dict.userGroups.builtinLocked}</p>
              </div>
            ) : null}
            <TitleFields
              variant="boxed"
              autoFocus={!existing}
              disabled={locked}
              name={name}
              description={description}
              onNameChange={setName}
              onDescriptionChange={setDescription}
              nameLabel={dict.userGroups.name}
              descriptionLabel={dict.userGroups.descriptionLabel}
            />

            <Section title={dict.userGroups.sections.members} hint={dict.userGroups.membersHint}>
              <Input
                placeholder={dict.userGroups.membersSearch}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              <div className="max-h-44 space-y-0.5 overflow-y-auto rounded-lg border p-1.5">
                {filtered.map((user) => (
                  <label
                    key={user.id}
                    className="hover:bg-muted flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm"
                  >
                    <Checkbox
                      checked={memberIds.includes(user.id)}
                      onCheckedChange={() => toggleMember(user.id)}
                    />
                    <span className="font-medium">{user.displayName || user.username}</span>
                    <span className="text-muted-foreground text-xs">{user.username}</span>
                  </label>
                ))}
              </div>
            </Section>

            <Section
              title={dict.userGroups.sections.permissions}
              hint={dict.userGroups.permissionsHint}
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="text-muted-foreground text-xs">
                  {dict.userGroups.shortcuts.label}
                </span>
                {PRESETS.map((preset) => (
                  <Button
                    key={preset.id}
                    type="button"
                    size="xs"
                    variant="outline"
                    disabled={locked}
                    onClick={() => setPermissions(preset.permissions)}
                  >
                    {dict.userGroups.shortcuts[preset.id]}
                  </Button>
                ))}
              </div>
              <PermissionChecklist
                value={permissions}
                onChange={setPermissions}
                disabled={locked}
              />
            </Section>

            <Section title={dict.userGroups.sections.players} hint={dict.userGroups.grantsHint}>
              {current?.admin ? (
                <label className="flex cursor-pointer items-start justify-between gap-3 rounded-lg border p-3">
                  <span>
                    <span className="block text-sm font-medium">{dict.userGroups.allAccess}</span>
                    <span className="text-muted-foreground block text-xs">
                      {dict.userGroups.allAccessHint}
                    </span>
                  </span>
                  <Switch checked={allAccess} disabled={locked} onCheckedChange={setAllAccess} />
                </label>
              ) : (
                <p className="text-muted-foreground text-xs">
                  {dict.userGroups.allAccessAdminOnly}
                </p>
              )}
              {allAccess ? null : (
                <GrantsEditor value={grants} onChange={setGrants} playerGroups={playerGroups} />
              )}
            </Section>
          </div>
          <DialogFooter className="border-t p-4">
            <Button type="button" variant="outline" onClick={onClose}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={saving || locked || name.trim() === ''}>
              {dict.common.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function UserGroupsContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const groups = useApiResource<UserGroup[] | Page<UserGroup>>(`${API_V1}/user-groups`)
  const users = useApiResource<AppUser[] | Page<AppUser>>(`${API_V1}/users`)
  const playerGroups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const [editing, setEditing] = useState<UserGroup | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<UserGroup | null>(null)

  async function remove(group: UserGroup) {
    try {
      await api.userGroups.remove(group.id)
      toast.success(dict.userGroups.deleted)
      void groups.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.userGroups.deleteFailed))
    }
  }

  if ((groups.loading && !groups.data) || (users.loading && !users.data)) {
    return <PageSkeleton rows={2} />
  }
  if (groups.error && !groups.data) return <ErrorState onRetry={() => void groups.reload()} />
  const list = asList(groups.data)
  const playerGroupList = asList(playerGroups.data)

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.userGroups.title}
        description={dict.userGroups.description}
        actions={
          <Button onClick={() => setEditing('new')}>
            <IconPlus /> {dict.userGroups.new}
          </Button>
        }
      />
      {list.length === 0 ? (
        <EmptyState icon={IconUsersGroup} title={dict.userGroups.empty} />
      ) : (
        <Card className="divide-y p-0">
          {list.map((group) => (
            <div
              key={group.id}
              {...rowActivation(() => setEditing(group), 'button')}
              className={cn(
                'hover:bg-accent/50 flex items-center justify-between gap-3 px-4 py-3',
                ROW_CLASS
              )}
            >
              <div className="min-w-0 space-y-1">
                <p className="flex items-center gap-2 truncate text-sm font-medium">
                  {group.name}
                  {group.builtin ? (
                    <Badge variant="secondary" className="gap-1">
                      <IconLock className="size-3" /> {dict.userGroups.builtin}
                    </Badge>
                  ) : null}
                  {group.allAccess ? (
                    <Badge variant="outline">{dict.userGroups.allAccessBadge}</Badge>
                  ) : null}
                </p>
                <p className="text-muted-foreground truncate text-xs">
                  {fmt(dict.userGroups.memberCount, { count: group.memberIds.length })}
                  {' · '}
                  {fmt(dict.userGroups.permissionCount, { count: group.permissions.length })}
                  {group.allAccess
                    ? ''
                    : ` · ${fmt(dict.userGroups.grantCount, { count: group.grants.length })}`}
                  {group.description ? ` · ${group.description}` : ''}
                </p>
              </div>
              <div className="flex gap-1">
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label={dict.common.edit}
                  onClick={() => setEditing(group)}
                >
                  <IconPencil />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label={dict.common.delete}
                  disabled={group.builtin}
                  onClick={() => setToDelete(group)}
                >
                  <IconTrash />
                </Button>
              </div>
            </div>
          ))}
        </Card>
      )}
      {editing ? (
        <UserGroupDialog
          key={editing === 'new' ? 'new' : editing.id}
          group={editing}
          users={asList(users.data)}
          playerGroups={playerGroupList}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void groups.reload()
            void users.reload()
          }}
        />
      ) : null}
      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title={dict.userGroups.deleteTitle}
        description={dict.userGroups.deleteDescription.replace('{name}', toDelete?.name ?? '')}
        confirmLabel={dict.common.delete}
        onConfirm={() => {
          if (toDelete) void remove(toDelete)
          setToDelete(null)
        }}
      />
    </div>
  )
}

export default function UserGroupsPage() {
  return (
    <RequirePermission permission="USER_MANAGE">
      <UserGroupsContent />
    </RequirePermission>
  )
}
