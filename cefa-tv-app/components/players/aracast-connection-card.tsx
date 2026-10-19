'use client'

import { useState } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import { aracastErrorMessage } from '@/components/players/aracast-errors'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/shadcn-ui/card'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { api } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { AracastConnection } from '@/types/api'

function PasswordField({
  id,
  label,
  configured,
  value,
  clear,
  onValueChange,
  onClearChange,
}: {
  id: string
  label: string
  configured: boolean
  value: string
  clear: boolean
  onValueChange: (value: string) => void
  onClearChange: (value: boolean) => void
}) {
  const dict = useDictionary()
  const text = dict.aracast.connection
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Label htmlFor={id}>{label}</Label>
        <Badge variant={configured ? 'secondary' : 'outline'}>
          {configured ? text.own : text.global}
        </Badge>
      </div>
      <Input
        id={id}
        type="password"
        autoComplete="new-password"
        disabled={clear}
        placeholder={configured ? text.keepPlaceholder : text.globalPlaceholder}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
      />
      {configured ? (
        <label className="flex items-center gap-2 text-sm">
          <Checkbox
            checked={clear}
            onCheckedChange={(checked) => {
              onClearChange(checked === true)
              if (checked === true) onValueChange('')
            }}
          />
          {text.clear}
        </label>
      ) : null}
    </div>
  )
}

export function AracastConnectionCard({ playerId }: { playerId: string }) {
  const dict = useDictionary()
  const text = dict.aracast.connection
  const connection = useApiResource<AracastConnection>(`${API_V1}/players/aracast/${playerId}`)
  const [sshPassword, setSshPassword] = useState('')
  const [ftpPassword, setFtpPassword] = useState('')
  const [clearSsh, setClearSsh] = useState(false)
  const [clearFtp, setClearFtp] = useState(false)
  const [saving, setSaving] = useState(false)

  const current = connection.data
  const dirty = sshPassword !== '' || ftpPassword !== '' || clearSsh || clearFtp

  async function save() {
    setSaving(true)
    try {
      const updated = await api.aracast.updateCredentials(playerId, {
        sshPassword: clearSsh ? '' : sshPassword || null,
        ftpPassword: clearFtp ? '' : ftpPassword || null,
      })
      connection.setData(updated)
      setSshPassword('')
      setFtpPassword('')
      setClearSsh(false)
      setClearFtp(false)
      toast.success(text.saved)
    } catch (err) {
      toast.error(aracastErrorMessage(err, dict.aracast.errors, text.saveFailed))
    } finally {
      setSaving(false)
    }
  }

  const rows: Array<[string, string]> = [
    [text.host, current?.host ?? '-'],
    [dict.aracast.fields.mac, current?.mac ?? '-'],
    [dict.aracast.fields.model, current?.model ?? '-'],
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{text.title}</CardTitle>
        <CardDescription>{text.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {connection.error ? (
          <p className="text-destructive bg-destructive/10 rounded-md p-2 text-sm">
            {text.loadFailed}
          </p>
        ) : null}
        {current ? (
          <>
            <dl className="divide-y text-sm">
              {rows.map(([label, value]) => (
                <div key={label} className="flex justify-between gap-3 py-2">
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="text-right font-medium break-all">{value}</dd>
                </div>
              ))}
            </dl>
            <PasswordField
              id="aracast-ssh"
              label={text.sshPassword}
              configured={current.sshPasswordConfigured}
              value={sshPassword}
              clear={clearSsh}
              onValueChange={setSshPassword}
              onClearChange={setClearSsh}
            />
            <PasswordField
              id="aracast-ftp"
              label={text.ftpPassword}
              configured={current.ftpPasswordConfigured}
              value={ftpPassword}
              clear={clearFtp}
              onValueChange={setFtpPassword}
              onClearChange={setClearFtp}
            />
            <div className="flex justify-end">
              <Button disabled={!dirty || saving} onClick={() => void save()}>
                {saving ? <IconLoader2 className="animate-spin" /> : null}
                {text.save}
              </Button>
            </div>
          </>
        ) : null}
      </CardContent>
    </Card>
  )
}
