'use client'

import { useState } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import { TimezonePicker } from '@/components/shared/timezone-picker'
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
import { Textarea } from '@/components/shadcn-ui/textarea'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { isCidr, parseIpRanges } from '@/lib/network'
import { toast } from '@/lib/toast'
import type { Site } from '@/types/api'

export function SiteDialog({
  site,
  onClose,
  onSaved,
}: {
  site: Site | null
  onClose: () => void
  onSaved: (site: Site) => void
}) {
  const dict = useDictionary()
  const [name, setName] = useState(site?.name ?? '')
  const [city, setCity] = useState(site?.city ?? '')
  const [country, setCountry] = useState(site?.country ?? '')
  const [timezone, setTimezone] = useState(site?.timezone ?? '')
  const [ipRanges, setIpRanges] = useState((site?.ipRanges ?? []).join('\n'))
  const [saving, setSaving] = useState(false)

  const invalidRanges = parseIpRanges(ipRanges).filter((range) => !isCidr(range))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    const input = {
      name: name.trim(),
      city: city.trim() || null,
      country: country.trim() || null,
      timezone: timezone.trim() || null,
      ipRanges: parseIpRanges(ipRanges),
    }
    try {
      const saved = site ? await api.sites.update(site.id, input) : await api.sites.create(input)
      toast.success(dict.sites.saved)
      onSaved(saved)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.sites.saveFailed))
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{site ? dict.sites.editTitle : dict.sites.newTitle}</DialogTitle>
            <DialogDescription>{dict.sites.dialogDescription}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="site-name">{dict.common.name}</Label>
            <Input
              id="site-name"
              required
              autoFocus
              value={name}
              placeholder={dict.sites.namePlaceholder}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="site-city">{dict.sites.city}</Label>
              <Input
                id="site-city"
                value={city}
                onChange={(event) => setCity(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="site-country">{dict.sites.country}</Label>
              <Input
                id="site-country"
                value={country}
                onChange={(event) => setCountry(event.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="site-timezone">{dict.sites.timezone}</Label>
            <TimezonePicker id="site-timezone" value={timezone} onChange={setTimezone} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="site-ip-ranges">{dict.aracast.ranges.label}</Label>
            <Textarea
              id="site-ip-ranges"
              rows={3}
              spellCheck={false}
              className="font-mono"
              aria-invalid={invalidRanges.length > 0}
              placeholder={dict.aracast.ranges.placeholder}
              value={ipRanges}
              onChange={(event) => setIpRanges(event.target.value)}
            />
            {invalidRanges.length > 0 ? (
              <p className="text-destructive text-xs">
                {dict.aracast.ranges.invalid.replace('{ranges}', invalidRanges.join(', '))}
              </p>
            ) : (
              <p className="text-muted-foreground text-xs">{dict.aracast.ranges.hint}</p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" disabled={!name.trim() || invalidRanges.length > 0 || saving}>
              {saving ? <IconLoader2 className="animate-spin" /> : null}
              {dict.common.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
