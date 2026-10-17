'use client'

import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { useMemo, useState } from 'react'
import Link from 'next/link'
import {
  IconAlertTriangle,
  IconHistory,
  IconKey,
  IconPencil,
  IconPlus,
  IconTrash,
  IconUsers,
} from '@tabler/icons-react'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { Switch } from '@/components/shadcn-ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/shadcn-ui/table'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { hasPermission } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import { normalizeText } from '@/lib/utils'
import type { AppUser, AuthProvider, Page, UserGroup } from '@/types/api'

const MIN_PASSWORD_LENGTH = 8
const AUTH_PROVIDERS: AuthProvider[] = ['LOCAL', 'MICROSOFT', 'HYBRID']

function UserDialog({
  user,
  userGroups,
  onClose,
  onSaved,
}: {
  user: AppUser | 'new'
  userGroups: UserGroup[]
  onClose: () => void
  onSaved: () => void
}) {
  const dict = useDictionary()
  const existing = user === 'new' ? null : user
  const [username, setUsername] = useState(existing?.username ?? '')
  const [displayName, setDisplayName] = useState(existing?.displayName ?? '')
  const [email, setEmail] = useState(existing?.email ?? '')
  const [authProvider, setAuthProvider] = useState<AuthProvider>(existing?.authProvider ?? 'LOCAL')
  const [password, setPassword] = useState('')
  const [enabled, setEnabled] = useState(existing?.enabled ?? true)
  const [groupIds, setGroupIds] = useState<string[]>(existing?.groupIds ?? [])
  const [groupQuery, setGroupQuery] = useState('')
  const [saving, setSaving] = useState(false)

  function toggleGroup(id: string) {
    setGroupIds((current) =>
      current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id]
    )
  }

  const needsPassword = authProvider !== 'MICROSOFT'
  const needsEmail = authProvider !== 'LOCAL'
  const valid =
    username.trim() !== '' &&
    displayName.trim() !== '' &&
    (!needsEmail || email.trim() !== '') &&
    (existing !== null || !needsPassword || password.length >= MIN_PASSWORD_LENGTH)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      const input = {
        displayName: displayName.trim(),
        email: email.trim() || null,
        authProvider,
        enabled,
        groupIds,
      }
      if (existing) await api.users.update(existing.id, input)
      else {
        await api.users.create({
          ...input,
          username: username.trim(),
          ...(needsPassword ? { password } : {}),
        })
      }
      toast.success(dict.users.saved)
      onSaved()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.users.saveFailed))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 p-0 sm:max-w-lg">
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="p-6 pb-3">
            <DialogTitle>{existing ? dict.users.editTitle : dict.users.newTitle}</DialogTitle>
          </DialogHeader>
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-6 py-2">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="user-username">{dict.users.username}</Label>
                <Input
                  id="user-username"
                  required
                  autoFocus={!existing}
                  disabled={existing !== null}
                  autoComplete="off"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="user-display">{dict.users.displayName}</Label>
                <Input
                  id="user-display"
                  required
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{dict.users.authProvider}</Label>
              <Select
                value={authProvider}
                onValueChange={(value) => setAuthProvider(value as AuthProvider)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {AUTH_PROVIDERS.map((provider) => (
                    <SelectItem key={provider} value={provider}>
                      {dict.users.providers[provider]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-muted-foreground text-xs">
                {dict.users.providerHints[authProvider]}
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="user-email">{dict.users.email}</Label>
              <Input
                id="user-email"
                type="email"
                autoComplete="off"
                required={needsEmail}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              {needsEmail ? (
                <p className="text-muted-foreground text-xs">{dict.users.emailHint}</p>
              ) : null}
            </div>
            {!existing && needsPassword ? (
              <div className="space-y-2">
                <Label htmlFor="user-password">{dict.users.password}</Label>
                <Input
                  id="user-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <p className="text-muted-foreground text-xs">{dict.users.passwordHint}</p>
              </div>
            ) : null}
            <div className="flex items-center justify-between rounded-lg border px-3 py-2">
              <Label htmlFor="user-enabled">{dict.users.enabledLabel}</Label>
              <Switch id="user-enabled" checked={enabled} onCheckedChange={setEnabled} />
            </div>
            <div className="space-y-2">
              <Label>{dict.users.userGroups}</Label>
              <p className="text-muted-foreground text-xs">{dict.users.userGroupsHint}</p>
              {userGroups.length > 6 ? (
                <Input
                  placeholder={dict.users.groupsSearch}
                  value={groupQuery}
                  onChange={(event) => setGroupQuery(event.target.value)}
                />
              ) : null}
              <div className="max-h-44 space-y-0.5 overflow-y-auto rounded-lg border p-1.5">
                {userGroups
                  .filter((group) => normalizeText(group.name).includes(normalizeText(groupQuery)))
                  .map((group) => (
                    <label
                      key={group.id}
                      className="hover:bg-muted flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm"
                    >
                      <Checkbox
                        checked={groupIds.includes(group.id)}
                        onCheckedChange={() => toggleGroup(group.id)}
                      />
                      <span className="min-w-0 flex-1 truncate font-medium">{group.name}</span>
                      {group.allAccess ? (
                        <Badge variant="outline">{dict.userGroups.allAccessBadge}</Badge>
                      ) : null}
                    </label>
                  ))}
              </div>
              {groupIds.length === 0 ? (
                <p className="text-warning flex items-center gap-2 text-xs">
                  <IconAlertTriangle className="size-4 shrink-0" />
                  {dict.users.noGroupsWarning}
                </p>
              ) : null}
            </div>
          </div>
          <DialogFooter className="border-t p-4">
            <Button type="button" variant="outline" onClick={onClose}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={saving || !valid}>
              {dict.common.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function PasswordDialog({ user, onClose }: { user: AppUser; onClose: () => void }) {
  const dict = useDictionary()
  const [password, setPassword] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await api.users.setPassword(user.id, password)
      toast.success(dict.users.passwordChanged)
      onClose()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.users.passwordFailed))
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] sm:max-w-md">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>
              {dict.users.resetTitle.replace('{name}', user.displayName || user.username)}
            </DialogTitle>
            <DialogDescription>{dict.users.resetDescription}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reset-password">{dict.users.newPassword}</Label>
            <Input
              id="reset-password"
              type="password"
              autoComplete="new-password"
              autoFocus
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <p className="text-muted-foreground text-xs">{dict.users.passwordHint}</p>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={saving || password.length < MIN_PASSWORD_LENGTH}>
              {dict.common.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function UsersContent() {
  const dict = useDictionary()
  const { user: current } = useAuth()
  const localePath = useLocalePath()
  const canAudit = hasPermission(current, 'AUDIT_VIEW')
  const users = useApiResource<AppUser[] | Page<AppUser>>(`${API_V1}/users`)
  const userGroups = useApiResource<UserGroup[] | Page<UserGroup>>(`${API_V1}/user-groups`)
  const [editing, setEditing] = useState<AppUser | 'new' | null>(null)
  const [resetting, setResetting] = useState<AppUser | null>(null)
  const [toDelete, setToDelete] = useState<AppUser | null>(null)

  const groupNames = useMemo(
    () => new Map(asList(userGroups.data).map((group) => [group.id, group.name])),
    [userGroups.data]
  )

  async function toggleEnabled(target: AppUser, enabled: boolean) {
    try {
      await api.users.update(target.id, {
        displayName: target.displayName ?? target.username,
        email: target.email,
        authProvider: target.authProvider,
        enabled,
        groupIds: target.groupIds,
      })
      void users.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.users.saveFailed))
    }
  }

  async function remove(target: AppUser) {
    try {
      await api.users.remove(target.id)
      toast.success(dict.users.deleted)
      void users.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.users.deleteFailed))
    }
  }

  if (users.loading && !users.data) {
    return <PageSkeleton rows={2} />
  }
  if (users.error && !users.data) return <ErrorState onRetry={() => void users.reload()} />
  const list = asList(users.data)

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.users.title}
        description={dict.users.description}
        actions={
          <Button onClick={() => setEditing('new')}>
            <IconPlus /> {dict.users.new}
          </Button>
        }
      />
      {list.length === 0 ? (
        <EmptyState icon={IconUsers} title={dict.users.empty} />
      ) : (
        <Card className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{dict.users.title}</TableHead>
                <TableHead>{dict.users.authProvider}</TableHead>
                <TableHead>{dict.users.groups}</TableHead>
                <TableHead>{dict.users.status}</TableHead>
                <TableHead className="text-right">{dict.common.actions}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((entry) => {
                const isSelf = entry.id === current?.id
                return (
                  <TableRow
                    key={entry.id}
                    {...rowActivation(() => setEditing(entry), 'button')}
                    className={ROW_CLASS}
                  >
                    <TableCell>
                      <p className="font-medium">
                        {entry.displayName || entry.username}
                        {isSelf ? (
                          <Badge variant="secondary" className="ml-2">
                            {dict.users.you}
                          </Badge>
                        ) : null}
                      </p>
                      <p className="text-muted-foreground text-xs">{entry.username}</p>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {dict.users.providers[entry.authProvider ?? 'LOCAL']}
                      </Badge>
                      {entry.email ? (
                        <p className="text-muted-foreground mt-1 text-xs">{entry.email}</p>
                      ) : null}
                    </TableCell>
                    <TableCell className="max-w-64">
                      {entry.groupIds.length === 0 ? (
                        <Badge variant="secondary" className="bg-warning/15 text-warning">
                          {dict.users.noGroups}
                        </Badge>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {entry.groupIds.map((id) => (
                            <Badge key={id} variant="outline" className="max-w-full">
                              <span className="truncate">{groupNames.get(id) ?? id}</span>
                            </Badge>
                          ))}
                        </div>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Switch
                          checked={entry.enabled}
                          disabled={isSelf}
                          aria-label={dict.users.enabledLabel}
                          onCheckedChange={(value) => void toggleEnabled(entry, value)}
                        />
                        <span className="text-muted-foreground text-xs">
                          {entry.enabled ? dict.users.active : dict.users.inactive}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        {canAudit ? (
                          <Button
                            asChild
                            size="icon-sm"
                            variant="ghost"
                            aria-label={dict.audit.viewHistory}
                            title={dict.audit.viewHistory}
                          >
                            <Link href={localePath(`/audit?userId=${entry.id}`)}>
                              <IconHistory />
                            </Link>
                          </Button>
                        ) : null}
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          aria-label={dict.users.resetPassword}
                          onClick={() => setResetting(entry)}
                        >
                          <IconKey />
                        </Button>
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          aria-label={dict.common.edit}
                          onClick={() => setEditing(entry)}
                        >
                          <IconPencil />
                        </Button>
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          aria-label={dict.common.delete}
                          disabled={isSelf}
                          onClick={() => setToDelete(entry)}
                        >
                          <IconTrash />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </Card>
      )}
      {editing ? (
        <UserDialog
          key={editing === 'new' ? 'new' : editing.id}
          user={editing}
          userGroups={asList(userGroups.data)}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void users.reload()
            void userGroups.reload()
          }}
        />
      ) : null}
      {resetting ? <PasswordDialog user={resetting} onClose={() => setResetting(null)} /> : null}
      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title={dict.users.deleteTitle}
        description={dict.users.deleteDescription.replace(
          '{name}',
          toDelete ? toDelete.displayName || toDelete.username : ''
        )}
        confirmLabel={dict.common.delete}
        onConfirm={() => {
          if (toDelete) void remove(toDelete)
          setToDelete(null)
        }}
      />
    </div>
  )
}

export default function UsersPage() {
  return (
    <RequirePermission permission="USER_MANAGE">
      <UsersContent />
    </RequirePermission>
  )
}
