'use client'

import { useState } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { toast } from '@/lib/toast'

const MIN_PASSWORD_LENGTH = 8

export function ChangePasswordForm() {
  const dict = useDictionary()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (next.length < MIN_PASSWORD_LENGTH) return setError(dict.access.changePassword.tooShort)
    if (next !== confirm) return setError(dict.access.changePassword.mismatch)
    setSaving(true)
    setError(null)
    try {
      await api.auth.changePassword({ currentPassword: current, newPassword: next })
      toast.success(dict.access.changePassword.saved)
      setCurrent('')
      setNext('')
      setConfirm('')
    } catch {
      setError(dict.access.changePassword.failed)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="max-w-md space-y-4">
      <div className="space-y-2">
        <Label htmlFor="current-password">{dict.access.changePassword.current}</Label>
        <Input
          id="current-password"
          type="password"
          autoComplete="current-password"
          required
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="new-password">{dict.access.changePassword.next}</Label>
        <Input
          id="new-password"
          type="password"
          autoComplete="new-password"
          required
          value={next}
          onChange={(event) => setNext(event.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="confirm-password">{dict.access.changePassword.confirm}</Label>
        <Input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(event) => setConfirm(event.target.value)}
        />
      </div>
      {error ? (
        <p role="alert" className="text-destructive text-sm">
          {error}
        </p>
      ) : null}
      <Button type="submit" disabled={saving}>
        {saving ? <IconLoader2 className="animate-spin" /> : null}
        {dict.common.save}
      </Button>
    </form>
  )
}
