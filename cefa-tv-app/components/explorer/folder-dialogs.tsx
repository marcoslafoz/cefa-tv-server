'use client'

import { useState } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import { FolderTree } from '@/components/explorer/folder-tree'
import type { FolderModel } from '@/components/explorer/use-folders'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/shadcn-ui/alert-dialog'
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
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { useFormat } from '@/hooks/use-format'

export function NameDialog({
  open,
  title,
  initial,
  confirmLabel,
  onSubmit,
  onClose,
}: {
  open: boolean
  title: string
  initial: string
  confirmLabel: string
  onSubmit: (name: string) => Promise<void>
  onClose: () => void
}) {
  const dict = useDictionary()
  const [name, setName] = useState(initial)
  const [saving, setSaving] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await onSubmit(name.trim())
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-sm">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="explorer-name">{dict.explorer.nameLabel}</Label>
            <Input
              id="explorer-name"
              autoFocus
              required
              value={name}
              placeholder={dict.explorer.namePlaceholder}
              onFocus={(event) => event.target.select()}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={!name.trim() || saving}>
              {saving ? <IconLoader2 className="animate-spin" /> : null}
              {confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export function MoveDialog({
  open,
  model,
  initialFolderId,
  disabledIds,
  count,
  onConfirm,
  onClose,
}: {
  open: boolean
  model: FolderModel
  initialFolderId: string | null
  disabledIds: Set<string>
  count: number
  onConfirm: (folderId: string | null) => Promise<void>
  onClose: () => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const [target, setTarget] = useState<string | null>(initialFolderId)
  const [saving, setSaving] = useState(false)

  async function confirm() {
    setSaving(true)
    try {
      await onConfirm(target)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{dict.explorer.moveTitle}</DialogTitle>
          <DialogDescription>
            {fmt(dict.explorer.moveDescription, { count: count })}
          </DialogDescription>
        </DialogHeader>
        <div className="max-h-72 overflow-y-auto rounded-xl border p-1.5">
          <FolderTree
            model={model}
            selectedId={target}
            rootLabel={dict.explorer.rootMedia}
            droppable={false}
            disabledIds={disabledIds}
            onSelect={setTarget}
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {dict.common.cancel}
          </Button>
          <Button disabled={saving} onClick={() => void confirm()}>
            {saving ? <IconLoader2 className="animate-spin" /> : null}
            {dict.explorer.moveHere}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function DeleteFoldersDialog({
  open,
  names,
  onConfirm,
  onClose,
}: {
  open: boolean
  names: string[]
  onConfirm: (moveContentsToParent: boolean) => void
  onClose: () => void
}) {
  const dict = useDictionary()
  const [moveContents, setMoveContents] = useState(true)

  return (
    <AlertDialog open={open} onOpenChange={(next) => !next && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{dict.explorer.deleteFolderTitle}</AlertDialogTitle>
          <AlertDialogDescription>
            {dict.explorer.deleteFolderDescription.replace('{names}', names.join(', '))}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <label className="flex cursor-pointer items-start gap-3 rounded-lg border p-3">
          <Checkbox
            className="mt-0.5"
            checked={moveContents}
            onCheckedChange={(value) => setMoveContents(value === true)}
          />
          <span>
            <span className="block text-sm font-medium">{dict.explorer.deleteMoveContents}</span>
            <span className="text-muted-foreground block text-xs">
              {dict.explorer.deleteMoveContentsHint}
            </span>
          </span>
        </label>
        <AlertDialogFooter>
          <AlertDialogCancel>{dict.common.cancel}</AlertDialogCancel>
          <AlertDialogAction onClick={() => onConfirm(moveContents)}>
            {dict.common.delete}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
