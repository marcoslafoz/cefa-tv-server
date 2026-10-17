'use client'

import { useState } from 'react'
import { IconDeviceTv, IconLoader2 } from '@tabler/icons-react'
import { CopyButton } from '@/components/shared/copy-button'
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
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { api, asList } from '@/lib/api'
import { API_V1, ApiError, apiErrorMessage } from '@/lib/api-client'
import { PLAYER_INSTALL_COMMAND } from '@/lib/constants'
import { canAccessGroup } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import type { Page, PendingPairing, Player, PlayerGroup } from '@/types/api'

const CODE_LENGTH = 6
const NO_GROUP = 'none'

export function normalizeCode(raw: string): string {
  return raw
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, CODE_LENGTH)
}

export function formatCode(code: string): string {
  return code.length > 3 ? `${code.slice(0, 3)}-${code.slice(3)}` : code
}

export function PairDialog({
  open,
  onOpenChange,
  onPaired,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onPaired: (player: Player) => void
}) {
  const dict = useDictionary()
  const { user } = useAuth()
  const { formatRelative } = useDateFormatter()
  const pending = useApiResource<PendingPairing[] | Page<PendingPairing>>(
    `${API_V1}/pairings/pending`,
    { intervalMs: 4000, enabled: open }
  )
  const groups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`, {
    enabled: open,
  })
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [groupId, setGroupId] = useState(NO_GROUP)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const waiting = asList(pending.data)
  const allowedGroups = asList(groups.data).filter((group) =>
    canAccessGroup(user, group.id, 'OPERATE')
  )
  const groupRequired = !user?.admin
  const valid =
    code.length === CODE_LENGTH && name.trim() !== '' && (!groupRequired || groupId !== NO_GROUP)

  function reset() {
    setCode('')
    setName('')
    setGroupId(NO_GROUP)
    setError(null)
  }

  function changeOpen(value: boolean) {
    if (!value) reset()
    onOpenChange(value)
  }

  function describe(err: unknown): string {
    if (err instanceof ApiError) {
      if (err.status === 429) return dict.pairing.rateLimited
      if (err.status === 410 || err.errorCode.includes('EXPIRED')) return dict.pairing.expired
      if (
        err.status === 404 ||
        err.errorCode.includes('NOT_FOUND') ||
        err.errorCode.includes('INVALID')
      ) {
        return dict.pairing.invalid
      }
    }
    return apiErrorMessage(err, dict.pairing.failed)
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const player = await api.pairings.claim({
        code: formatCode(code),
        name: name.trim(),
        groupId: groupId === NO_GROUP ? null : groupId,
      })
      toast.success(dict.pairing.success)
      reset()
      onPaired(player)
    } catch (err) {
      setError(describe(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 p-0 sm:max-w-lg">
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="p-6 pb-3">
            <DialogTitle>{dict.pairing.title}</DialogTitle>
            <DialogDescription>{dict.pairing.description}</DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-2">
            <div className="space-y-2">
              <Label htmlFor="pairing-code">{dict.pairing.code}</Label>
              <Input
                id="pairing-code"
                autoFocus
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                inputMode="text"
                placeholder={dict.pairing.codePlaceholder}
                value={formatCode(code)}
                onChange={(event) => {
                  setCode(normalizeCode(event.target.value))
                  setError(null)
                }}
                className="h-14 text-center font-mono text-3xl font-semibold tracking-[0.3em] uppercase"
              />
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium">{dict.pairing.pending}</p>
              {waiting.length === 0 ? (
                <p className="text-muted-foreground rounded-lg border border-dashed p-3 text-center text-sm">
                  {dict.pairing.pendingEmpty}
                </p>
              ) : (
                <>
                  <p className="text-muted-foreground text-xs">{dict.pairing.pendingHint}</p>
                  <div className="space-y-1.5">
                    {waiting.map((entry) => (
                      <div
                        key={entry.id}
                        className="flex items-center gap-3 rounded-lg border p-2.5"
                      >
                        <span className="bg-primary/10 text-primary flex size-9 shrink-0 items-center justify-center rounded-lg">
                          <IconDeviceTv className="size-4" />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-medium">
                            {entry.hostname ?? entry.ip ?? '-'}
                          </span>
                          <span className="text-muted-foreground block truncate text-xs">
                            {[entry.ip, entry.resolution].filter(Boolean).join(' · ')}
                            {' · '}
                            {dict.pairing.pendingSeen.replace(
                              '{time}',
                              formatRelative(entry.requestedAt)
                            )}
                          </span>
                        </span>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="pairing-name">{dict.pairing.name}</Label>
                <Input
                  id="pairing-name"
                  required
                  placeholder={dict.pairing.namePlaceholder}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label>{dict.pairing.group}</Label>
                <Select value={groupId} onValueChange={setGroupId}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {!groupRequired ? (
                      <SelectItem value={NO_GROUP}>{dict.pairing.noGroup}</SelectItem>
                    ) : (
                      <SelectItem value={NO_GROUP} disabled>
                        {dict.pairing.groupRequired}
                      </SelectItem>
                    )}
                    {allowedGroups.map((group) => (
                      <SelectItem key={group.id} value={group.id}>
                        {group.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {groupRequired ? (
                  <p className="text-muted-foreground text-xs">{dict.pairing.groupRequiredHint}</p>
                ) : null}
              </div>
            </div>

            {error ? (
              <p role="alert" className="text-destructive bg-destructive/10 rounded-md p-2 text-sm">
                {error}
              </p>
            ) : null}

            <div className="bg-muted/40 space-y-2 rounded-lg border p-3">
              <p className="text-sm font-medium">{dict.pairing.installTitle}</p>
              <p className="text-muted-foreground text-xs">{dict.pairing.installDescription}</p>
              <code className="bg-background block rounded-md border p-2 font-mono text-xs break-all">
                {PLAYER_INSTALL_COMMAND}
              </code>
              <div className="flex items-center justify-between gap-2">
                <p className="text-muted-foreground text-xs">{dict.pairing.installHint}</p>
                <CopyButton value={PLAYER_INSTALL_COMMAND} />
              </div>
            </div>
          </div>
          <DialogFooter className="border-t p-4">
            <Button type="button" variant="outline" onClick={() => changeOpen(false)}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={!valid || saving}>
              {saving ? <IconLoader2 className="animate-spin" /> : null}
              {dict.pairing.submit}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
