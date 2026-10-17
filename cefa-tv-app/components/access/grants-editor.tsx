'use client'

import { IconPlus, IconTrash } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import type { AccessGrant, AccessLevel, Page, PlayerGroup, Site } from '@/types/api'

const LEVELS: AccessLevel[] = ['VIEW', 'OPERATE']

function scopeKey(grant: AccessGrant): string {
  return grant.siteId ? `s:${grant.siteId}` : `g:${grant.playerGroupId ?? ''}`
}

function grantFromKey(key: string, level: AccessLevel): AccessGrant {
  return key.startsWith('s:')
    ? { siteId: key.slice(2), level }
    : { playerGroupId: key.slice(2), level }
}

export function GrantsEditor({
  value,
  onChange,
  playerGroups,
}: {
  value: AccessGrant[]
  onChange: (grants: AccessGrant[]) => void
  playerGroups: PlayerGroup[]
}) {
  const dict = useDictionary()
  const sitesResource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const sites = asList(sitesResource.data)
  const used = new Set(value.map(scopeKey))
  const options = [
    ...playerGroups.map((group) => ({ key: `g:${group.id}`, name: group.name, site: false })),
    ...sites.map((site) => ({ key: `s:${site.id}`, name: site.name, site: true })),
  ]
  const available = options.filter((option) => !used.has(option.key))

  function patch(index: number, update: Partial<AccessGrant>) {
    onChange(value.map((grant, position) => (position === index ? { ...grant, ...update } : grant)))
  }

  return (
    <div className="space-y-2">
      {value.length === 0 ? (
        <p className="text-muted-foreground rounded-lg border border-dashed p-3 text-center text-sm">
          {options.length === 0 ? dict.userGroups.noPlayerGroups : dict.userGroups.noGrants}
        </p>
      ) : (
        value.map((grant, index) => {
          const current = scopeKey(grant)
          const selectable = options.filter(
            (option) => option.key === current || !used.has(option.key)
          )
          return (
            <div key={current} className="flex items-center gap-2">
              <Select
                value={current}
                onValueChange={(key) =>
                  onChange(
                    value.map((entry, position) =>
                      position === index ? grantFromKey(key, grant.level) : entry
                    )
                  )
                }
              >
                <SelectTrigger className="min-w-0 flex-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectLabel>{dict.userGroups.grantGroups}</SelectLabel>
                    {selectable
                      .filter((option) => !option.site)
                      .map((option) => (
                        <SelectItem key={option.key} value={option.key}>
                          {option.name}
                        </SelectItem>
                      ))}
                  </SelectGroup>
                  {selectable.some((option) => option.site) ? (
                    <SelectGroup>
                      <SelectLabel>{dict.userGroups.grantSites}</SelectLabel>
                      {selectable
                        .filter((option) => option.site)
                        .map((option) => (
                          <SelectItem key={option.key} value={option.key}>
                            {option.name}
                          </SelectItem>
                        ))}
                    </SelectGroup>
                  ) : null}
                </SelectContent>
              </Select>
              <Select
                value={grant.level}
                onValueChange={(level) => patch(index, { level: level as AccessLevel })}
              >
                <SelectTrigger className="w-32">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {LEVELS.map((level) => (
                    <SelectItem key={level} value={level}>
                      {dict.access.levels[level]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                type="button"
                size="icon-sm"
                variant="ghost"
                aria-label={dict.common.delete}
                onClick={() => onChange(value.filter((_, position) => position !== index))}
              >
                <IconTrash />
              </Button>
            </div>
          )
        })
      )}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={available.length === 0}
        onClick={() => onChange([...value, grantFromKey(available[0].key, 'OPERATE')])}
      >
        <IconPlus /> {dict.userGroups.addGrant}
      </Button>
    </div>
  )
}
