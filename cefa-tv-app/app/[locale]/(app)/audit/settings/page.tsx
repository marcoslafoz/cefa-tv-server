'use client'

import { NumberField } from '@/components/shared/number-field'
import { useState } from 'react'
import Link from 'next/link'
import { IconArrowLeft, IconLoader2 } from '@tabler/icons-react'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useFormat } from '@/hooks/use-format'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { AuditSettings } from '@/types/api'

const PRESETS = [30, 90, 180, 365]

function AuditSettingsContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const localePath = useLocalePath()
  const settings = useApiResource<AuditSettings>(`${API_V1}/settings/audit`)
  const [edited, setEdited] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  if (settings.loading && !settings.data) return <PageSkeleton rows={1} />
  if (settings.error || !settings.data) return <ErrorState onRetry={() => void settings.reload()} />

  const value = edited ?? String(settings.data.retentionDays)
  const days = Number(value)
  const { minRetentionDays, maxRetentionDays } = settings.data
  const valid = Number.isInteger(days) && days >= minRetentionDays && days <= maxRetentionDays

  async function save() {
    setSaving(true)
    try {
      await api.audit.updateSettings({ retentionDays: days })
      toast.success(dict.audit.settings.saved)
      setEdited(null)
      void settings.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.audit.settings.saveFailed))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={localePath('/audit')}>
          <IconArrowLeft /> {dict.audit.settings.back}
        </Link>
      </Button>
      <PageHeader title={dict.audit.settings.title} description={dict.audit.settings.description} />
      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle>{dict.audit.settings.retention}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="retention-days">{dict.audit.settings.retentionLabel}</Label>
            <NumberField
              id="retention-days"
              min={minRetentionDays}
              max={maxRetentionDays}
              value={Number(value) || minRetentionDays}
              onValueChange={(days) => setEdited(String(days))}
            />
            <p className="text-muted-foreground text-xs">
              {dict.audit.settings.retentionHint
                .replace('{min}', String(minRetentionDays))
                .replace('{max}', String(maxRetentionDays))}
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {PRESETS.map((preset) => (
              <Button
                key={preset}
                size="xs"
                variant={days === preset ? 'default' : 'outline'}
                onClick={() => setEdited(String(preset))}
              >
                {fmt(dict.audit.settings.presetDays, { days: preset })}
              </Button>
            ))}
          </div>
          <div className="flex justify-end">
            <Button disabled={!valid || saving || edited === null} onClick={() => void save()}>
              {saving ? <IconLoader2 className="animate-spin" /> : null}
              {dict.common.save}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export default function AuditSettingsPage() {
  return (
    <RequirePermission permission="AUDIT_VIEW">
      <AuditSettingsContent />
    </RequirePermission>
  )
}
