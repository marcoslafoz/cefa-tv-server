'use client'

import { useState } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import { aracastErrorMessage } from '@/components/players/aracast-errors'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
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
import { isHostname, isIpv4 } from '@/lib/network'
import { toast } from '@/lib/toast'
import type { AracastNetwork, AracastNetworkConfig } from '@/types/api'

type FieldKey = 'hostname' | 'ip' | 'netmask' | 'gateway' | 'dnsPrimary' | 'dnsSecondary'

const FIELDS: FieldKey[] = ['hostname', 'ip', 'netmask', 'gateway', 'dnsPrimary', 'dnsSecondary']
const ADDRESS_FIELDS: FieldKey[] = ['ip', 'netmask', 'gateway']

function fieldError(key: FieldKey, value: string): boolean {
  const trimmed = value.trim()
  if (key === 'hostname') return !isHostname(trimmed)
  if (key === 'dnsSecondary') return trimmed !== '' && !isIpv4(trimmed)
  return !isIpv4(trimmed)
}

export function AracastNetworkCard({ playerId, online }: { playerId: string; online: boolean }) {
  const dict = useDictionary()
  const text = dict.aracast.network
  const network = useApiResource<AracastNetwork>(`${API_V1}/players/${playerId}/aracast/network`)
  const [edits, setEdits] = useState<Partial<Record<FieldKey, string>>>({})
  const [reboot, setReboot] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const configured = network.data?.configured
  const effective = network.data?.effective

  function valueOf(key: FieldKey): string {
    return edits[key] ?? configured?.[key] ?? ''
  }

  const values = Object.fromEntries(FIELDS.map((key) => [key, valueOf(key)])) as Record<
    FieldKey,
    string
  >
  const invalid = FIELDS.filter((key) => fieldError(key, values[key]))
  const dirty = FIELDS.some((key) => values[key].trim() !== (configured?.[key] ?? ''))
  const addressChanged = ADDRESS_FIELDS.some(
    (key) => values[key].trim() !== (configured?.[key] ?? '')
  )
  const rebootRequired = addressChanged
  const canSave = dirty && invalid.length === 0 && online && !saving

  async function save() {
    setConfirming(false)
    setSaving(true)
    try {
      const result = await api.aracast.setNetwork(playerId, {
        hostname: values.hostname.trim(),
        ip: values.ip.trim(),
        netmask: values.netmask.trim(),
        gateway: values.gateway.trim(),
        dnsPrimary: values.dnsPrimary.trim(),
        ...(values.dnsSecondary.trim() ? { dnsSecondary: values.dnsSecondary.trim() } : {}),
        reboot: rebootRequired || reboot,
      })
      toast.success(text.saved)
      setNotice(
        [
          result.message,
          result.ipChanged ? text.ipChanged.replace('{host}', result.host) : null,
          result.rebooting ? text.rebooting : null,
        ]
          .filter(Boolean)
          .join(' ')
      )
      setEdits({})
      if (!result.rebooting) void network.reload()
    } catch (err) {
      toast.error(aracastErrorMessage(err, dict.aracast.errors, text.saveFailed))
    } finally {
      setSaving(false)
    }
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (rebootRequired) setConfirming(true)
    else void save()
  }

  function describe(config: AracastNetworkConfig | undefined, key: FieldKey | 'type'): string {
    if (!config) return '-'
    if (key === 'type') return config.type ? text.types[config.type] : '-'
    return config[key] || '-'
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{text.title}</CardTitle>
        <CardDescription>{text.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {network.error ? (
          <p className="text-destructive bg-destructive/10 rounded-md p-2 text-sm">
            {text.loadFailed}
          </p>
        ) : null}
        {notice ? (
          <p role="status" className="bg-muted/60 rounded-md p-2 text-sm">
            {notice}
          </p>
        ) : null}
        {network.data ? (
          <>
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground text-left">
                  <th className="py-1 font-medium" />
                  <th className="py-1 font-medium">{text.configured}</th>
                  <th className="py-1 font-medium">{text.effective}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {(['type', ...FIELDS] as Array<FieldKey | 'type'>).map((key) => (
                  <tr key={key}>
                    <td className="text-muted-foreground py-1.5 pr-3">{text.fields[key]}</td>
                    <td className="py-1.5 pr-3 font-medium break-all">
                      {describe(configured, key)}
                    </td>
                    <td className="py-1.5 font-medium break-all">{describe(effective, key)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <form onSubmit={submit} className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                {FIELDS.map((key) => {
                  const hasError = fieldError(key, values[key]) && values[key] !== ''
                  return (
                    <div key={key} className="space-y-2">
                      <Label htmlFor={`aracast-net-${key}`}>
                        {text.fields[key]}
                        {key === 'dnsSecondary' ? (
                          <span className="text-muted-foreground font-normal">
                            {' '}
                            ({dict.common.optional})
                          </span>
                        ) : null}
                      </Label>
                      <Input
                        id={`aracast-net-${key}`}
                        autoComplete="off"
                        spellCheck={false}
                        aria-invalid={hasError}
                        value={values[key]}
                        onChange={(event) =>
                          setEdits((previous) => ({ ...previous, [key]: event.target.value }))
                        }
                      />
                      {hasError ? (
                        <p className="text-destructive text-xs">
                          {key === 'hostname' ? text.invalidHostname : text.invalidIp}
                        </p>
                      ) : null}
                    </div>
                  )
                })}
              </div>
              <label className="flex items-start gap-2 text-sm">
                <Checkbox
                  className="mt-0.5"
                  checked={rebootRequired || reboot}
                  disabled={rebootRequired}
                  onCheckedChange={(checked) => setReboot(checked === true)}
                />
                <span>
                  {text.reboot}
                  <span className="text-muted-foreground block text-xs">
                    {rebootRequired ? text.rebootRequired : text.rebootHint}
                  </span>
                </span>
              </label>
              {!online ? <p className="text-muted-foreground text-xs">{text.offline}</p> : null}
              <div className="flex justify-end">
                <Button type="submit" disabled={!canSave}>
                  {saving ? <IconLoader2 className="animate-spin" /> : null}
                  {text.save}
                </Button>
              </div>
            </form>
          </>
        ) : null}
      </CardContent>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={text.confirmTitle}
        description={text.confirmDescription.replace('{ip}', values.ip.trim())}
        confirmLabel={text.confirmAction}
        onConfirm={() => void save()}
      />
    </Card>
  )
}
